package com.daaradji.budget.account;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record AccountRequest(
            @NotBlank(message = "Le nom est obligatoire") @Size(max = 80) String name,
            @NotNull(message = "Le type est obligatoire") AccountType type,
            BigDecimal initialBalance,
            @Size(max = 20) String color,
            boolean archived) {
    }

    public record AccountResponse(Long id, String name, AccountType type, BigDecimal initialBalance,
            BigDecimal balance, String color, boolean archived) {
    }
}
