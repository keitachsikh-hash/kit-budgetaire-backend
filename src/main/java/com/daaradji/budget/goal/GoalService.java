package com.daaradji.budget.goal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.daaradji.budget.common.BusinessException;
import com.daaradji.budget.common.NotFoundException;
import com.daaradji.budget.goal.GoalDtos.GoalRequest;
import com.daaradji.budget.goal.GoalDtos.GoalResponse;

@Service
public class GoalService {

    private final SavingGoalRepository goals;

    public GoalService(SavingGoalRepository goals) {
        this.goals = goals;
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> list(Long userId) {
        return goals.findByUserIdOrderByDeadlineAscIdAsc(userId).stream().map(GoalService::toResponse).toList();
    }

    @Transactional
    public GoalResponse create(Long userId, GoalRequest req) {
        SavingGoal g = new SavingGoal();
        g.setUserId(userId);
        apply(g, req);
        return toResponse(goals.save(g));
    }

    @Transactional
    public GoalResponse update(Long userId, Long id, GoalRequest req) {
        SavingGoal g = find(userId, id);
        apply(g, req);
        return toResponse(g);
    }

    @Transactional
    public GoalResponse contribute(Long userId, Long id, BigDecimal amount) {
        SavingGoal g = find(userId, id);
        BigDecimal saved = g.getSavedAmount().add(amount);
        if (saved.signum() < 0) {
            throw new BusinessException("Le retrait dépasse le montant épargné");
        }
        g.setSavedAmount(saved);
        return toResponse(g);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        goals.delete(find(userId, id));
    }

    private SavingGoal find(Long userId, Long id) {
        return goals.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Objectif introuvable"));
    }

    private static void apply(SavingGoal g, GoalRequest req) {
        g.setName(req.name().trim());
        g.setTargetAmount(req.targetAmount());
        if (req.savedAmount() != null) {
            g.setSavedAmount(req.savedAmount());
        }
        g.setDeadline(req.deadline());
        g.setIcon(req.icon() == null || req.icon().isBlank() ? "piggy-bank" : req.icon());
        g.setColor(req.color() == null || req.color().isBlank() ? "#198754" : req.color());
    }

    static GoalResponse toResponse(SavingGoal g) {
        BigDecimal remaining = g.getTargetAmount().subtract(g.getSavedAmount()).max(BigDecimal.ZERO);
        int percent = g.getSavedAmount().multiply(BigDecimal.valueOf(100))
                .divide(g.getTargetAmount(), 0, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100)).intValue();
        Long monthsLeft = null;
        BigDecimal monthlyNeeded = null;
        if (g.getDeadline() != null) {
            long months = ChronoUnit.MONTHS.between(YearMonth.now(), YearMonth.from(g.getDeadline()));
            monthsLeft = Math.max(months, 0);
            if (remaining.signum() > 0 && !g.getDeadline().isBefore(LocalDate.now())) {
                monthlyNeeded = remaining.divide(BigDecimal.valueOf(Math.max(months, 1)), 0, RoundingMode.CEILING);
            }
        }
        return new GoalResponse(g.getId(), g.getName(), g.getTargetAmount(), g.getSavedAmount(), remaining, percent,
                g.getDeadline(), monthsLeft, monthlyNeeded, g.getIcon(), g.getColor(), remaining.signum() == 0);
    }
}
