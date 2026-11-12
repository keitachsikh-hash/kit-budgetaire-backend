package com.daaradji.budget.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryRequest(
            @NotBlank(message = "Le nom est obligatoire") @Size(max = 60) String name,
            @NotNull(message = "Le type est obligatoire") CategoryType type,
            @Size(max = 40) String icon,
            @Size(max = 20) String color) {
    }

    public record CategoryResponse(Long id, String name, CategoryType type, String icon, String color) {

        public static CategoryResponse of(Category c) {
            return new CategoryResponse(c.getId(), c.getName(), c.getType(), c.getIcon(), c.getColor());
        }
    }
}
