package com.daaradji.budget.document;

import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.Base64PdfSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.daaradji.budget.common.BusinessException;
import com.daaradji.budget.document.DocumentDtos.Extraction;

/**
 * Lit une copie manuscrite (photo ou PDF) avec Claude et renvoie les opérations en JSON structuré.
 * La clé API est lue dans la variable d'environnement ANTHROPIC_API_KEY.
 */
@Component
public class HandwritingExtractor {

    private static final Logger log = LoggerFactory.getLogger(HandwritingExtractor.class);
    /** Limite de l'API pour une image encodée. */
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private static final String SYSTEM_PROMPT = """
            Tu aides des particuliers et des petits commerçants d'Afrique de l'Ouest à numériser leurs anciens \
            cahiers de comptes écrits à la main. On te donne la photo ou le scan d'une page. Relève chaque \
            dépense et chaque revenu qui y figure, pour qu'ils soient enregistrés dans une application de budget.

            Ce qu'il faut savoir sur ces cahiers :
            - Les montants sont en francs CFA. On écrit souvent « 2500f », « 2.500 », « 2 500 F » ou « 15 mille » ; \
            convertis toujours en nombre (2500, 15000). Ne confonds pas un point de milliers avec une virgule décimale.
            - Une page peut être en colonnes (Entrées / Sorties, Recettes / Dépenses) ou en liste avec des signes + et −. \
            Sans indication, une ligne d'achat ou de paiement est une dépense.
            - Les dates peuvent être écrites en entier, abrégées (« 12/3 »), ou données une seule fois en tête de groupe : \
            applique-les aux lignes qui suivent. Si l'année manque, déduis-la du contexte de la page, sinon utilise \
            l'année indiquée dans la demande. Laisse la date vide si rien ne permet de la connaître.
            - Ignore les totaux, sous-totaux, reports de solde et ratures : ce ne sont pas des opérations.
            - Le texte peut mélanger le français, le wolof ou d'autres langues locales : reformule le libellé en \
            français simple dans « description » et recopie le texte original dans « rawText ».

            Pour « category » et « account », choisis le nom exact dans les listes fournies quand une correspondance \
            est raisonnable ; sinon propose le nom le plus proche. Marque « uncertain » à true pour toute ligne dont \
            tu n'es pas sûr (chiffre mal lisible, type ambigu). L'utilisateur vérifie chaque ligne avant de \
            l'enregistrer : signale le doute plutôt que d'inventer une valeur, et n'ajoute jamais une ligne qui \
            n'est pas sur la page.""";

    private final String model;
    private final boolean configured;
    private volatile AnthropicClient client;

    public HandwritingExtractor(@Value("${app.ai.model:claude-opus-5-5}") String model,
            @Value("${ANTHROPIC_API_KEY:}") String apiKey) {
        this.model = model;
        this.configured = apiKey != null && !apiKey.isBlank();
    }

    public boolean isConfigured() {
        return configured;
    }

    public Extraction extract(byte[] data, String contentType, List<String> expenseCategories,
            List<String> incomeCategories, List<String> accounts) {
        if (!configured) {
            throw new BusinessException("La lecture automatique n'est pas activée : définissez la variable "
                    + "d'environnement ANTHROPIC_API_KEY sur le serveur. La copie reste archivée.");
        }
        ContentBlockParam file = fileBlock(data, contentType);
        String request = """
                Année par défaut : %d.
                Catégories de dépenses : %s
                Catégories de revenus : %s
                Comptes : %s

                Relève toutes les opérations de cette copie.""".formatted(LocalDate.now().getYear(),
                String.join(", ", expenseCategories), String.join(", ", incomeCategories), String.join(", ", accounts));

        StructuredOutputConfig<Extraction> output = StructuredOutputConfig.<Extraction>builder()
                .format(Extraction.class)
                .effort(OutputConfig.Effort.MEDIUM)
                .build();
        var params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(SYSTEM_PROMPT)
                .outputConfig(output)
                .addUserMessageOfContentBlockParams(List.of(file, ContentBlockParam.ofText(request)))
                // Si un filtre de sécurité refuse la demande, l'API la relance sur un modèle de repli adapté.
                .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build();

        StructuredMessage<Extraction> response;
        try {
            response = client().messages().create(params);
        } catch (UnauthorizedException e) {
            throw new BusinessException("Clé API Anthropic invalide : vérifiez ANTHROPIC_API_KEY.");
        } catch (RateLimitException e) {
            throw new BusinessException("Service de lecture momentanément saturé, réessayez dans une minute.");
        } catch (AnthropicServiceException e) {
            log.warn("Échec de la lecture de la copie : {}", e.getMessage());
            throw new BusinessException("La lecture de la copie a échoué (" + e.statusCode() + "). Réessayez plus tard.");
        } catch (AnthropicIoException e) {
            throw new BusinessException("Impossible de joindre le service de lecture : vérifiez la connexion internet du serveur.");
        } catch (AnthropicException e) {
            log.warn("Lecture de la copie impossible : {}", e.getMessage());
            throw new BusinessException("La lecture automatique est indisponible pour le moment.");
        }

        StopReason stop = response.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop)) {
            throw new BusinessException("Cette copie n'a pas pu être lue automatiquement. Saisissez les lignes à la main.");
        }
        if (StopReason.MAX_TOKENS.equals(stop)) {
            throw new BusinessException("La copie contient trop de lignes pour une seule lecture : "
                    + "photographiez-la en plusieurs parties.");
        }
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst()
                .orElseThrow(() -> new BusinessException("Aucune opération n'a été reconnue sur cette copie."));
    }

    private static ContentBlockParam fileBlock(byte[] data, String contentType) {
        String b64 = Base64.getEncoder().encodeToString(data);
        if ("application/pdf".equals(contentType)) {
            return ContentBlockParam.ofDocument(Base64PdfSource.builder().data(b64).build());
        }
        if (data.length > MAX_IMAGE_BYTES) {
            throw new BusinessException("Image trop lourde pour la lecture automatique (5 Mo maximum).");
        }
        return ContentBlockParam.ofImage(Base64ImageSource.builder()
                .data(b64)
                .mediaType(Base64ImageSource.MediaType.of(contentType))
                .build());
    }

    private AnthropicClient client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.fromEnv();
                }
            }
        }
        return client;
    }
}
