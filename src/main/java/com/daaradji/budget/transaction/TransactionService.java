package com.daaradji.budget.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.daaradji.budget.account.Account;
import com.daaradji.budget.account.AccountService;
import com.daaradji.budget.category.Category;
import com.daaradji.budget.category.CategoryService;
import com.daaradji.budget.category.CategoryType;
import com.daaradji.budget.common.BusinessException;
import com.daaradji.budget.common.NotFoundException;
import com.daaradji.budget.transaction.TransactionDtos.PageResponse;
import com.daaradji.budget.transaction.TransactionDtos.SummaryResponse;
import com.daaradji.budget.transaction.TransactionDtos.TransactionFilter;
import com.daaradji.budget.transaction.TransactionDtos.TransactionRequest;
import com.daaradji.budget.transaction.TransactionDtos.TransactionResponse;

import jakarta.persistence.criteria.Predicate;

@Service
public class TransactionService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id"));

    private final TransactionRepository transactions;
    private final AccountService accountService;
    private final CategoryService categoryService;

    public TransactionService(TransactionRepository transactions, AccountService accountService,
            CategoryService categoryService) {
        this.transactions = transactions;
        this.accountService = accountService;
        this.categoryService = categoryService;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> search(Long userId, TransactionFilter filter, int page, int size) {
        int safeSize = Math.clamp(size, 1, 100);
        return PageResponse.of(transactions
                .findAll(spec(userId, filter), PageRequest.of(Math.max(page, 0), safeSize, DEFAULT_SORT))
                .map(TransactionResponse::of));
    }

    @Transactional(readOnly = true)
    public SummaryResponse summary(Long userId, TransactionFilter filter) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        List<BudgetTransaction> all = transactions.findAll(spec(userId, filter));
        for (BudgetTransaction t : all) {
            if (t.getType() == CategoryType.INCOME) {
                income = income.add(t.getAmount());
            } else {
                expense = expense.add(t.getAmount());
            }
        }
        return new SummaryResponse(income, expense, income.subtract(expense), all.size());
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> export(Long userId, TransactionFilter filter) {
        return transactions.findAll(spec(userId, filter), PageRequest.of(0, 10_000, DEFAULT_SORT))
                .map(TransactionResponse::of).getContent();
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(Long userId, Long id) {
        return TransactionResponse.of(find(userId, id));
    }

    @Transactional
    public TransactionResponse create(Long userId, TransactionRequest req) {
        BudgetTransaction t = new BudgetTransaction();
        t.setUserId(userId);
        apply(userId, t, req);
        return TransactionResponse.of(transactions.save(t));
    }

    /** Tout ou rien : si une ligne est invalide, aucune opération n'est enregistrée. */
    @Transactional
    public List<TransactionResponse> createAll(Long userId, List<TransactionRequest> items) {
        List<TransactionResponse> created = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            try {
                created.add(create(userId, items.get(i)));
            } catch (BusinessException | NotFoundException e) {
                throw new BusinessException("Ligne " + (i + 1) + " : " + e.getMessage());
            }
        }
        return created;
    }

    @Transactional
    public TransactionResponse update(Long userId, Long id, TransactionRequest req) {
        BudgetTransaction t = find(userId, id);
        apply(userId, t, req);
        return TransactionResponse.of(t);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        transactions.delete(find(userId, id));
    }

    private BudgetTransaction find(Long userId, Long id) {
        return transactions.findById(id).filter(t -> t.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Opération introuvable"));
    }

    private void apply(Long userId, BudgetTransaction t, TransactionRequest req) {
        Account account = accountService.get(userId, req.accountId());
        Category category = categoryService.get(userId, req.categoryId());
        if (category.getType() != req.type()) {
            throw new BusinessException("La catégorie ne correspond pas au type d'opération");
        }
        if (req.date().isAfter(LocalDate.now().plusYears(1))) {
            throw new BusinessException("La date est trop éloignée dans le futur");
        }
        if (req.operator() != null && req.type() != CategoryType.EXPENSE) {
            throw new BusinessException("L'opérateur ne concerne que les dépenses de crédit");
        }
        t.setType(req.type());
        t.setAmount(req.amount());
        t.setDate(req.date());
        t.setAccount(account);
        t.setCategory(category);
        t.setOperator(req.operator());
        t.setDescription(req.description() == null || req.description().isBlank() ? null : req.description().trim());
    }

    private static Specification<BudgetTransaction> spec(Long userId, TransactionFilter f) {
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("userId"), userId));
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("date"), f.from()));
            }
            if (f.to() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("date"), f.to()));
            }
            if (f.type() != null) {
                p.add(cb.equal(root.get("type"), f.type()));
            }
            if (f.accountId() != null) {
                p.add(cb.equal(root.get("account").get("id"), f.accountId()));
            }
            if (f.categoryId() != null) {
                p.add(cb.equal(root.get("category").get("id"), f.categoryId()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                p.add(cb.like(cb.lower(root.get("description")), "%" + f.q().trim().toLowerCase() + "%"));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
