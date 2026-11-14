package com.daaradji.budget.transaction;

/** Opérateur téléphonique d'une recharge de crédit. */
public enum MobileOperator {
    ORANGE("Orange"),
    YAS("Yas"),
    EXPRESSO("Expresso");

    private final String label;

    MobileOperator(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
