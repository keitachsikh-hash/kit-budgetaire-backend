package com.daaradji.budget.account;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByUserIdOrderByArchivedAscNameAsc(Long userId);

    List<Account> findByUserIdAndArchivedFalse(Long userId);

    Optional<Account> findByIdAndUserId(Long id, Long userId);
}
