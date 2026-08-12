package com.benmochen.portfolio.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data generates the implementation at runtime. Method names are parsed
 * into queries: findByExternalId becomes
 * "select ... from account where external_id = ?".
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByExternalId(String externalId);

    boolean existsByExternalId(String externalId);
}
