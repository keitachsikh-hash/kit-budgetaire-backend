package com.daaradji.budget.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Le nom est obligatoire") @Size(max = 120) String fullName,
            @NotBlank(message = "L'email est obligatoire") @Email(message = "Email invalide") String email,
            @NotBlank @Size(min = 6, message = "6 caractères minimum") String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "L'email est obligatoire") String email,
            @NotBlank(message = "Le mot de passe est obligatoire") String password) {
    }

    public record UserResponse(Long id, String fullName, String email, String currency) {
    }

    public record AuthResponse(String token, long expiresIn, UserResponse user) {
    }
}
