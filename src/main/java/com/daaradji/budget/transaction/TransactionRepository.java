package com.daaradji.budget.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.daaradji.budget.category.CategoryType;

public interface TransactionRepository
        extends JpaRepository<BudgetTransaction, Long>, JpaSpecificationExecutor<BudgetTransaction> {

    @Override
    @EntityGraph(attributePaths = { "account", "category" })
    Page<BudgetTransaction> findAll(Specification<BudgetTransaction> spec, Pageable pageable);

    @EntityGraph(attributePaths = { "account", "category" })
    List<BudgetTransaction> findByUserIdAndDateBetweenOrderByDateDescIdDesc(Long userId, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = { "account", "category" })
    List<BudgetTransaction> findTop6ByUserIdOrderByDateDescIdDesc(Long userId);

    boolean existsByCategoryId(Long categoryId);

    boolean existsByAccountId(Long accountId);

    @Query("""
            select c.id as categoryId, c.name as name, c.icon as icon, c.color as color, sum(t.amount) as total
            from BudgetTransaction t join t.category c
            where t.userId = :userId and t.type = :type and t.date between :from and :to
            group by c.id, c.name, c.icon, c.color
            order by sum(t.amount) desc
            """)
    List<CategoryTotal> totalsByCategory(@Param("userId") Long userId, @Param("type") CategoryType type,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select coalesce(sum(t.amount), 0) from BudgetTransaction t
            where t.userId = :userId and t.type = :type and t.date between :from and :to
            """)
    BigDecimal total(@Param("userId") Long userId, @Param("type") CategoryType type,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select t.account.id, sum(case when t.type = com.daaradji.budget.category.CategoryType.INCOME
                                          then t.amount else -t.amount end)
            from BudgetTransaction t where t.userId = :userId group by t.account.id
            """)
    List<Object[]> rawNetByAccount(@Param("userId") Long userId);

    /** Mouvement net (revenus - dépenses) par compte. */
    default Map<Long, BigDecimal> netByAccount(Long userId) {
        Map<Long, BigDecimal> result = new HashMap<>();
        for (Object[] row : rawNetByAccount(userId)) {
            result.put((Long) row[0], (BigDecimal) row[1]);
        }
        return result;
    }
}
