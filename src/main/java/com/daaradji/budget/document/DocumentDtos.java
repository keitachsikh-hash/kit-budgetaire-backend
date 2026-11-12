package com.daaradji.budget.document;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record DocumentResponse(Long id, String fileName, String contentType, long sizeBytes, String title,
            String note, String thumbnail, DocumentStatus status, int extractedCount, int importedCount,
            LocalDateTime createdAt) {

        static DocumentResponse of(ScannedDocument d) {
            return new DocumentResponse(d.getId(), d.getFileName(), d.getContentType(), d.getSizeBytes(), d.getTitle(),
                    d.getNote(), d.getThumbnail(), d.getStatus(), d.getExtractedCount(), d.getImportedCount(),
                    d.getCreatedAt());
        }
    }

    public record UpdateRequest(@Size(max = 120) String title, @Size(max = 500) String note) {
    }

    public record ImportedRequest(@Min(1) int count) {
    }

    /** Réponse structurée demandée à Claude (le schéma JSON est dérivé de ces records). */
    public record Extraction(
            @JsonPropertyDescription("Opérations lues sur la copie, dans l'ordre où elles apparaissent")
            List<ExtractedLine> lines,
            @JsonPropertyDescription("Remarques courtes pour l'utilisateur : passages illisibles, hypothèses faites (vide si rien)")
            String notes) {
    }

    public record ExtractedLine(
            @JsonPropertyDescription("Date au format AAAA-MM-JJ, ou chaîne vide si absente")
            String date,
            @JsonPropertyDescription("EXPENSE pour une dépense/sortie, INCOME pour un revenu/entrée")
            String type,
            @JsonPropertyDescription("Montant positif en FCFA, sans séparateur de milliers")
            double amount,
            @JsonPropertyDescription("Nom exact d'une catégorie de la liste fournie, ou la meilleure proposition")
            String category,
            @JsonPropertyDescription("Nom exact d'un compte de la liste fournie, ou chaîne vide si non précisé")
            String account,
            @JsonPropertyDescription("Libellé de l'opération, reformulé proprement")
            String description,
            @JsonPropertyDescription("Texte tel qu'écrit à la main, pour vérification")
            String rawText,
            @JsonPropertyDescription("true si la lecture de cette ligne est incertaine")
            boolean uncertain) {
    }
}
