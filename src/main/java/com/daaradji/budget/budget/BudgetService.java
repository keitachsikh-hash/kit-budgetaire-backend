package com.daaradji.budget.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.daaradji.budget.budget.BudgetDtos.BudgetLine;
import com.daaradji.budget.budget.BudgetDtos.BudgetOverview;
import com.daaradji.budget.budget.BudgetDtos.BudgetRequest;
import com.daaradji.budget.budget.BudgetDtos.BudgetStatus;
import com.daaradji.budget.category.Category;
import com.daaradji.budget.category.CategoryService;
import com.daaradji.budget.category.CategoryType;
import com.daaradji.budget.common.BusinessException;
import com.daaradji.budget.common.NotFoundException;
import com.daaradji.budget.transaction.CategoryTotal;
import com.daaradji.budget.transaction.TransactionRepository;

@Service
public class BudgetService {

    private final MonthlyBudgetRepository budgets;
    private final TransactionRepository transactions;
    private final CategoryService categoryService;

    public BudgetService(MonthlyBudgetRepository budgets, TransactionRepository transactions,
            CategoryService categoryService) {
        this.budgets = budgets;
        this.transactions = transactions;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public BudgetOverview overview(Long userId, YearMonth period) {
        Map<Long, BigDecimal> spentByCategory = transactions
                .totalsByCategory(userId, CategoryType.EXPENSE, period.atDay(1), period.atEndOfMonth()).stream()
                .collect(Collectors.toMap(CategoryTotal::getCategoryId, CategoryTotal::getTotal));

        List<BudgetLine> lines = budgets.findByUserIdAndPeriod(userId, period.toString()).stream()
                .map(b -> toLine(b, spentByCategory.getOrDefault(b.getCategory().getId(), BigDecimal.ZERO)))
                .sorted(Comparator.comparingInt(BudgetLine::percent).reversed())
                .toList();

        BigDecimal totalLimit = lines.stream().map(BudgetLine::limitAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSpent = lines.stream().map(BudgetLine::spent).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BudgetOverview(period.toString(), totalLimit, totalSpent, totalLimit.subtract(totalSpent),
                percent(totalSpent, totalLimit), lines);
    }

    @Transactional
    public void create(Long userId, BudgetRequest req) {
        Category category = expenseCategory(userId, req.categoryId());
        if (budgets.existsByUserIdAndCategoryIdAndPeriod(userId, category.getId(), req.period())) {
            throw new BusinessException("Un budget existe déjà pour cette catégorie sur ce mois");
        }
        MonthlyBudget b = new MonthlyBudget();
        b.setUserId(userId);
        b.setCategory(category);
        b.setPeriod(req.period());
        b.setLimitAmount(req.limitAmount());
        budgets.save(b);
    }

    @Transactional
    public void update(Long userId, Long id, BudgetRequest req) {
        MonthlyBudget b = find(userId, id);
        if (!b.getCategory().getId().equals(req.categoryId()) || !b.getPeriod().equals(req.period())) {
            if (budgets.existsByUserIdAndCategoryIdAndPeriod(userId, req.categoryId(), req.period())) {
                throw new BusinessException("Un budget existe déjà pour cette catégorie sur ce mois");
            }
            b.setCategory(expenseCategory(userId, req.categoryId()));
            b.setPeriod(req.period());
        }
        b.setLimitAmount(req.limitAmount());
    }

    @Transactional
    public void delete(Long userId, Long id) {
        budgets.delete(find(userId, id));
    }

    /** Recopie les budgets du mois précédent qui n'existent pas encore sur la période. */
    @Transactional
    public int copyFromPrevious(Long userId, YearMonth period) {
        String target = period.toString();
        int copied = 0;
        for (MonthlyBudget previous : budgets.findByUserIdAndPeriod(userId, period.minusMonths(1).toString())) {
            if (!budgets.existsByUserIdAndCategoryIdAndPeriod(userId, previous.getCategory().getId(), target)) {
                MonthlyBudget b = new MonthlyBudget();
                b.setUserId(userId);
                b.setCategory(previous.getCategory());
                b.setPeriod(target);
                b.setLimitAmount(previous.getLimitAmount());
                budgets.save(b);
                copied++;
            }
        }
        return copied;
    }

    private MonthlyBudget find(Long userId, Long id) {
        return budgets.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Budget introuvable"));
    }

    private Category expenseCategory(Long userId, Long categoryId) {
        Category category = categoryService.get(userId, categoryId);
        if (category.getType() != CategoryType.EXPENSE) {
            throw new BusinessException("Un budget ne peut porter que sur une catégorie de dépense");
        }
        return category;
    }

    private static BudgetLine toLine(MonthlyBudget b, BigDecimal spent) {
        int pct = percent(spent, b.getLimitAmount());
        BudgetStatus status = pct > 100 ? BudgetStatus.EXCEEDED : pct >= 80 ? BudgetStatus.WARNING : BudgetStatus.OK;
        Category c = b.getCategory();
        return new BudgetLine(b.getId(), c.getId(), c.getName(), c.getIcon(), c.getColor(), b.getLimitAmount(), spent,
                b.getLimitAmount().subtract(spent), pct, status);
    }

    private static int percent(BigDecimal part, BigDecimal total) {
        if (total.signum() == 0) {
            return 0;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).intValue();
    }
}
