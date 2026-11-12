package com.daaradji.budget.budget;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudget, Long> {

    @EntityGraph(attributePaths = "category")
    List<MonthlyBudget> findByUserIdAndPeriod(Long userId, String period);

    @EntityGraph(attributePaths = "category")
    Optional<MonthlyBudget> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndCategoryIdAndPeriod(Long userId, Long categoryId, String period);
}
