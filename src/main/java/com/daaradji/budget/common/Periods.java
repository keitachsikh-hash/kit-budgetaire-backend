package com.daaradji.budget.common;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

/** Utilitaires pour les périodes mensuelles au format yyyy-MM. */
public final class Periods {

    private Periods() {
    }

    public static YearMonth parseOrCurrent(String period) {
        if (period == null || period.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(period);
        } catch (DateTimeParseException ex) {
            throw new BusinessException("Période invalide, format attendu : AAAA-MM");
        }
    }
}
