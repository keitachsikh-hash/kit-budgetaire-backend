package com.daaradji.budget.dashboard;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.daaradji.budget.account.AccountService;
import com.daaradji.budget.budget.BudgetDtos.BudgetOverview;
import com.daaradji.budget.budget.BudgetDtos.BudgetStatus;
import com.daaradji.budget.budget.BudgetService;
import com.daaradji.budget.category.CategoryType;
import com.daaradji.budget.dashboard.DashboardResponse.BudgetSummary;
import com.daaradji.budget.dashboard.DashboardResponse.CategorySlice;
import com.daaradji.budget.dashboard.DashboardResponse.MonthTrend;
import com.daaradji.budget.goal.GoalService;
import com.daaradji.budget.transaction.BudgetTransaction;
import com.daaradji.budget.transaction.TransactionDtos.TransactionResponse;
import com.daaradji.budget.transaction.TransactionRepository;

@Service
public class DashboardService {

    private static final int TREND_MONTHS = 6;

    private final TransactionRepository transactions;
    private final AccountService accountService;
    private final BudgetService budgetService;
    private final GoalService goalService;

    public DashboardService(TransactionRepository transactions, AccountService accountService,
            BudgetService budgetService, GoalService goalService) {
        this.transactions = transactions;
        this.accountService = accountService;
        this.budgetService = budgetService;
        this.goalService = goalService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse build(Long userId, YearMonth period) {
        BigDecimal income = transactions.total(userId, CategoryType.INCOME, period.atDay(1), period.atEndOfMonth());
        BigDecimal expense = transactions.total(userId, CategoryType.EXPENSE, period.atDay(1), period.atEndOfMonth());
        YearMonth previous = period.minusMonths(1);
        BigDecimal previousExpense = transactions.total(userId, CategoryType.EXPENSE, previous.atDay(1),
                previous.atEndOfMonth());

        List<CategorySlice> slices = transactions
                .totalsByCategory(userId, CategoryType.EXPENSE, period.atDay(1), period.atEndOfMonth()).stream()
                .map(c -> new CategorySlice(c.getCategoryId(), c.getName(), c.getIcon(), c.getColor(), c.getTotal(),
                        percent(c.getTotal(), expense)))
                .toList();

        BudgetOverview budget = budgetService.overview(userId, period);
        BudgetSummary budgetSummary = new BudgetSummary(budget.totalLimit(), budget.totalSpent(), budget.percent(),
                budget.lines().stream().filter(l -> l.status() != BudgetStatus.OK).toList());

        List<TransactionResponse> recent = transactions.findTop6ByUserIdOrderByDateDescIdDesc(userId).stream()
                .map(TransactionResponse::of).toList();

        Integer change = previousExpense.signum() == 0 ? null
                : expense.subtract(previousExpense).multiply(BigDecimal.valueOf(100))
                        .divide(previousExpense, 0, RoundingMode.HALF_UP).intValue();
        int savingsRate = income.signum() == 0 ? 0 : percent(income.subtract(expense), income);

        return new DashboardResponse(period.toString(), income, expense, income.subtract(expense), savingsRate, change,
                accountService.totalBalance(userId), slices, trend(userId, period), budgetSummary, recent,
                goalService.list(userId).stream().filter(g -> !g.completed()).limit(3).toList());
    }

    private List<MonthTrend> trend(Long userId, YearMonth period) {
        YearMonth start = period.minusMonths(TREND_MONTHS - 1L);
        Map<YearMonth, BigDecimal[]> buckets = new LinkedHashMap<>();
        for (int i = 0; i < TREND_MONTHS; i++) {
            buckets.put(start.plusMonths(i), new BigDecimal[] { BigDecimal.ZERO, BigDecimal.ZERO });
        }
        for (BudgetTransaction t : transactions.findByUserIdAndDateBetweenOrderByDateDescIdDesc(userId,
                start.atDay(1), period.atEndOfMonth())) {
            BigDecimal[] bucket = buckets.get(YearMonth.from(t.getDate()));
            int idx = t.getType() == CategoryType.INCOME ? 0 : 1;
            bucket[idx] = bucket[idx].add(t.getAmount());
        }
        List<MonthTrend> result = new ArrayList<>();
        buckets.forEach((ym, v) -> result.add(new MonthTrend(ym.toString(), v[0], v[1])));
        return result;
    }

    private static int percent(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) {
            return 0;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).intValue();
    }
}
