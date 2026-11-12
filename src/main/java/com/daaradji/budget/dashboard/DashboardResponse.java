package com.daaradji.budget.dashboard;

import java.math.BigDecimal;
import java.util.List;

import com.daaradji.budget.budget.BudgetDtos.BudgetLine;
import com.daaradji.budget.goal.GoalDtos.GoalResponse;
import com.daaradji.budget.transaction.TransactionDtos.TransactionResponse;

public record DashboardResponse(
        String period,
        BigDecimal income,
        BigDecimal expense,
        BigDecimal net,
        int savingsRate,
        Integer expenseChangePercent,
        BigDecimal totalBalance,
        List<CategorySlice> expensesByCategory,
        List<MonthTrend> trend,
        BudgetSummary budget,
        List<TransactionResponse> recent,
        List<GoalResponse> goals) {

    public record CategorySlice(Long categoryId, String name, String icon, String color, BigDecimal total,
            int percent) {
    }

    public record MonthTrend(String period, BigDecimal income, BigDecimal expense) {
    }

    public record BudgetSummary(BigDecimal totalLimit, BigDecimal totalSpent, int percent, List<BudgetLine> alerts) {
    }
}
