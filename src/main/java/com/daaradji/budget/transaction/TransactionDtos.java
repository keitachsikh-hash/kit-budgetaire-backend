package com.daaradji.budget.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;

import com.daaradji.budget.category.CategoryType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    public record TransactionRequest(
            @NotNull(message = "Le type est obligatoire") CategoryType type,
            @NotNull(message = "Le montant est obligatoire")
            @DecimalMin(value = "0.01", message = "Le montant doit être positif")
            @Digits(integer = 13, fraction = 2) BigDecimal amount,
            @NotNull(message = "La date est obligatoire") LocalDate date,
            @NotNull(message = "Le compte est obligatoire") Long accountId,
            @NotNull(message = "La catégorie est obligatoire") Long categoryId,
            @Size(max = 255) String description,
            MobileOperator operator) {
    }

    /** Saisie multiple : plusieurs dépenses et/ou revenus enregistrés en une seule fois. */
    public record BatchRequest(
            @NotEmpty(message = "Ajoutez au moins une opération")
            @Size(max = 500, message = "500 opérations maximum par envoi")
            List<@Valid @NotNull TransactionRequest> items) {
    }

    /** Totaux de toutes les opérations correspondant aux filtres (toutes pages confondues). */
    public record SummaryResponse(BigDecimal income, BigDecimal expense, BigDecimal net, long count) {
    }

    public record AccountRef(Long id, String name, String type, String color) {
    }

    public record CategoryRef(Long id, String name, String icon, String color) {
    }

    public record TransactionResponse(Long id, CategoryType type, BigDecimal amount, LocalDate date,
            String description, MobileOperator operator, AccountRef account, CategoryRef category) {

        public static TransactionResponse of(BudgetTransaction t) {
            return new TransactionResponse(t.getId(), t.getType(), t.getAmount(), t.getDate(), t.getDescription(),
                    t.getOperator(),
                    new AccountRef(t.getAccount().getId(), t.getAccount().getName(), t.getAccount().getType().name(),
                            t.getAccount().getColor()),
                    new CategoryRef(t.getCategory().getId(), t.getCategory().getName(), t.getCategory().getIcon(),
                            t.getCategory().getColor()));
        }
    }

    public record TransactionFilter(LocalDate from, LocalDate to, CategoryType type, Long accountId,
            Long categoryId, String q) {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        public static <T> PageResponse<T> of(Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(),
                    p.getTotalPages());
        }
    }
}
