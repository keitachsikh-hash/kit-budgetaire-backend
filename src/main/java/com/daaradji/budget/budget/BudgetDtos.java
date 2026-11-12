package com.daaradji.budget.budget;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class BudgetDtos {

    private BudgetDtos() {
    }

    public enum BudgetStatus {
        OK,
        WARNING,
        EXCEEDED
    }

    public record BudgetRequest(
            @NotNull(message = "La catégorie est obligatoire") Long categoryId,
            @NotBlank @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "Période invalide (AAAA-MM)") String period,
            @NotNull(message = "Le plafond est obligatoire")
            @DecimalMin(value = "1", message = "Le plafond doit être positif") BigDecimal limitAmount) {
    }

    public record BudgetLine(Long id, Long categoryId, String categoryName, String icon, String color,
            BigDecimal limitAmount, BigDecimal spent, BigDecimal remaining, int percent, BudgetStatus status) {
    }

    public record BudgetOverview(String period, BigDecimal totalLimit, BigDecimal totalSpent, BigDecimal remaining,
            int percent, List<BudgetLine> lines) {
    }
}
