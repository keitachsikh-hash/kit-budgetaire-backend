package com.daaradji.budget.transaction;

import java.math.BigDecimal;

/** Total des opérations d'une catégorie sur une période. */
public interface CategoryTotal {

    Long getCategoryId();

    String getName();

    String getIcon();

    String getColor();

    BigDecimal getTotal();
}
