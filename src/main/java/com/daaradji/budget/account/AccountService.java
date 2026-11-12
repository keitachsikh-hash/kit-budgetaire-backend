package com.daaradji.budget.account;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.daaradji.budget.account.AccountDtos.AccountRequest;
import com.daaradji.budget.account.AccountDtos.AccountResponse;
import com.daaradji.budget.common.BusinessException;
import com.daaradji.budget.common.NotFoundException;
import com.daaradji.budget.transaction.TransactionRepository;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    public AccountService(AccountRepository accounts, TransactionRepository transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(Long userId) {
        Map<Long, BigDecimal> movements = transactions.netByAccount(userId);
        return accounts.findByUserIdOrderByArchivedAscNameAsc(userId).stream()
                .map(a -> toResponse(a, movements.getOrDefault(a.getId(), BigDecimal.ZERO)))
                .toList();
    }

    /** Solde total des comptes actifs. */
    @Transactional(readOnly = true)
    public BigDecimal totalBalance(Long userId) {
        Map<Long, BigDecimal> movements = transactions.netByAccount(userId);
        return accounts.findByUserIdAndArchivedFalse(userId).stream()
                .map(a -> a.getInitialBalance().add(movements.getOrDefault(a.getId(), BigDecimal.ZERO)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public AccountResponse create(Long userId, AccountRequest req) {
        Account a = new Account();
        a.setUserId(userId);
        apply(a, req);
        return toResponse(accounts.save(a), BigDecimal.ZERO);
    }

    @Transactional
    public AccountResponse update(Long userId, Long id, AccountRequest req) {
        Account a = get(userId, id);
        apply(a, req);
        return toResponse(a, transactions.netByAccount(userId).getOrDefault(id, BigDecimal.ZERO));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Account a = get(userId, id);
        if (transactions.existsByAccountId(id)) {
            throw new BusinessException("Ce compte contient des opérations : archivez-le plutôt que de le supprimer");
        }
        accounts.delete(a);
    }

    @Transactional
    public Account createDefault(Long userId) {
        Account a = new Account();
        a.setUserId(userId);
        a.setName("Espèces");
        a.setType(AccountType.ESPECES);
        a.setColor("#198754");
        return accounts.save(a);
    }

    public Account get(Long userId, Long id) {
        return accounts.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Compte introuvable"));
    }

    private static void apply(Account a, AccountRequest req) {
        a.setName(req.name().trim());
        a.setType(req.type());
        a.setInitialBalance(req.initialBalance() == null ? BigDecimal.ZERO : req.initialBalance());
        a.setColor(req.color() == null || req.color().isBlank() ? "#0d6efd" : req.color());
        a.setArchived(req.archived());
    }

    private static AccountResponse toResponse(Account a, BigDecimal movements) {
        return new AccountResponse(a.getId(), a.getName(), a.getType(), a.getInitialBalance(),
                a.getInitialBalance().add(movements), a.getColor(), a.isArchived());
    }
}
