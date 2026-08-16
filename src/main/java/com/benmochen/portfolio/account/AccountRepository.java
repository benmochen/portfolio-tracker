package com.benmochen.portfolio.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Every lookup is scoped by user. There is deliberately no plain
     * findById in use: an unscoped read is how one user ends up seeing
     * another's holdings by changing a number in the URL.
     */
    Optional<Account> findByIdAndUserId(Long id, Long userId);

    List<Account> findByUserIdOrderByIdAsc(Long userId);

    Optional<Account> findByExternalIdAndUserId(String externalId, Long userId);

    boolean existsByExternalIdAndUserId(String externalId, Long userId);

    boolean existsByIdAndUserId(Long id, Long userId);

    /** One-time claim of accounts that were imported before users existed. */
    @Modifying
    @Query("update Account a set a.userId = :userId where a.userId is null")
    int claimUnownedAccounts(@Param("userId") Long userId);
}
