package com.daaradji.budget.goal;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class GoalDtos {

    private GoalDtos() {
    }

    public record GoalRequest(
            @NotBlank(message = "Le nom est obligatoire") @Size(max = 80) String name,
            @NotNull(message = "Le montant cible est obligatoire")
            @DecimalMin(value = "1", message = "Le montant cible doit être positif") BigDecimal targetAmount,
            @DecimalMin(value = "0", message = "Le montant épargné ne peut pas être négatif") BigDecimal savedAmount,
            LocalDate deadline,
            @Size(max = 40) String icon,
            @Size(max = 20) String color) {
    }

    /** Versement (montant positif) ou retrait (montant négatif). */
    public record ContributionRequest(@NotNull(message = "Le montant est obligatoire") BigDecimal amount) {
    }

    public record GoalResponse(Long id, String name, BigDecimal targetAmount, BigDecimal savedAmount,
            BigDecimal remaining, int percent, LocalDate deadline, Long monthsLeft, BigDecimal monthlyNeeded,
            String icon, String color, boolean completed) {
    }
}
