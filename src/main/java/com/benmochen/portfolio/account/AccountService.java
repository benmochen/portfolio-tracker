package com.benmochen.portfolio.account;

import com.benmochen.portfolio.account.AccountDtos.AccountResponse;
import com.benmochen.portfolio.account.AccountDtos.CreateAccountRequest;
import com.benmochen.portfolio.account.AccountDtos.UpdateAccountRequest;
import com.benmochen.portfolio.common.ConflictException;
import com.benmochen.portfolio.common.NotFoundException;
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

    /**
     * Constructor injection: Spring sees one constructor and supplies the
     * repository. Preferred over field injection because the dependency is
     * explicit and the class is testable without starting Spring.
     */
    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> findAll() {
        return accountRepository.findAll().stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse findById(Long id) {
        return accountRepository.findById(id)
                .map(AccountResponse::from)
                .orElseThrow(() -> NotFoundException.of("Account", id));
    }

    @Transactional(readOnly = true)
    public Account requireByExternalId(String externalId) {
        return accountRepository.findByExternalId(externalId)
                .orElseThrow(() -> NotFoundException.of("Account", externalId));
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        // Checked here so the client gets a clean 409 rather than a raw
        // constraint violation. The database UNIQUE constraint is still the
        // real guarantee: this check alone would race under concurrency.
        if (accountRepository.existsByExternalId(request.externalId())) {
            throw new ConflictException("Account already exists: " + request.externalId());
        }

        Account account = new Account(
                request.externalId(),
                request.name(),
                request.accountType(),
                request.currency());

        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    public AccountResponse rename(Long id, UpdateAccountRequest request) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> NotFoundException.of("Account", id));

        account.setName(request.name());
        // No explicit save() needed: inside a transaction the entity is
        // "managed", so Hibernate flushes the change on commit.
        return AccountResponse.from(account);
    }

    @Transactional
    public void delete(Long id) {
        if (!accountRepository.existsById(id)) {
            throw NotFoundException.of("Account", id);
        }
        accountRepository.deleteById(id);
    }
}
