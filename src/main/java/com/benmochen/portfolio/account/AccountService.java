package com.benmochen.portfolio.account;

import com.benmochen.portfolio.account.AccountDtos.AccountResponse;
import com.benmochen.portfolio.account.AccountDtos.CreateAccountRequest;
import com.benmochen.portfolio.account.AccountDtos.UpdateAccountRequest;
import com.benmochen.portfolio.common.ConflictException;
import com.benmochen.portfolio.common.NotFoundException;
import com.benmochen.portfolio.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * All account business logic lives here. Controllers stay thin and never call
 * each other; they call services.
 */
@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CurrentUser currentUser;

    /**
     * Constructor injection: Spring sees one constructor and supplies the
     * dependencies. Preferred over field injection because they are explicit
     * and the class is testable without starting Spring.
     */
    public AccountService(AccountRepository accountRepository, CurrentUser currentUser) {
        this.accountRepository = accountRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findAll() {
        return accountRepository.findByUserIdOrderByIdAsc(currentUser.requireId()).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse findById(Long id) {
        // Not found rather than forbidden when the account belongs to someone
        // else: a 403 would confirm that the id exists, which is itself
        // information the caller is not entitled to.
        return accountRepository.findByIdAndUserId(id, currentUser.requireId())
                .map(AccountResponse::from)
                .orElseThrow(() -> NotFoundException.of("Account", id));
    }

    @Transactional(readOnly = true)
    public Account requireByExternalId(String externalId) {
        return accountRepository.findByExternalIdAndUserId(externalId, currentUser.requireId())
                .orElseThrow(() -> NotFoundException.of("Account", externalId));
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        // Checked here so the client gets a clean 409 rather than a raw
        // constraint violation. The database UNIQUE constraint is still the
        // real guarantee: this check alone would race under concurrency.
        Long userId = currentUser.requireId();
        if (accountRepository.existsByExternalIdAndUserId(request.externalId(), userId)) {
            throw new ConflictException("Account already exists: " + request.externalId());
        }

        Account account = new Account(
                request.externalId(),
                request.name(),
                request.accountType(),
                request.currency(),
                userId);

        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    public AccountResponse rename(Long id, UpdateAccountRequest request) {
        Account account = accountRepository.findByIdAndUserId(id, currentUser.requireId())
                .orElseThrow(() -> NotFoundException.of("Account", id));

        account.setName(request.name());
        // No explicit save() needed: inside a transaction the entity is
        // "managed", so Hibernate flushes the change on commit.
        return AccountResponse.from(account);
    }

    @Transactional
    public void delete(Long id) {
        if (!accountRepository.existsByIdAndUserId(id, currentUser.requireId())) {
            throw NotFoundException.of("Account", id);
        }
        accountRepository.deleteById(id);
    }
}
