package com.daaradji.budget.goal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingGoalRepository extends JpaRepository<SavingGoal, Long> {

    List<SavingGoal> findByUserIdOrderByDeadlineAscIdAsc(Long userId);

    Optional<SavingGoal> findByIdAndUserId(Long id, Long userId);
}
