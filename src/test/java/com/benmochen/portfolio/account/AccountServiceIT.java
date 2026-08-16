package com.benmochen.portfolio.account;

import com.benmochen.portfolio.TestcontainersConfiguration;
import com.benmochen.portfolio.account.AccountDtos.CreateAccountRequest;
import com.benmochen.portfolio.common.ConflictException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration test against a real Postgres started by Testcontainers, not an
 * in-memory database. That matters here: H2 would not enforce the CHECK
 * constraints or the CHAR(3) semantics this schema relies on, so a test that
 * passed on H2 could still fail in production.
 *
 * The IT suffix marks this as an integration test rather than a unit test.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@WithMockUser(username = "accounttester")
class AccountServiceIT {

    @Autowired
    private AccountService accountService;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private com.benmochen.portfolio.user.AppUserRepository appUserRepository;

    @org.junit.jupiter.api.BeforeEach
    void seedUser() {
        if (appUserRepository.findByUsername("accounttester").isEmpty()) {
            appUserRepository.save(new com.benmochen.portfolio.user.AppUser(
                    "accounttester", "not-a-real-hash"));
        }
    }

    @Test
    void createsAndReadsBackAnAccount() {
        var created = accountService.create(new CreateAccountRequest(
                "12345678", "TFSA main", AccountType.TFSA, "CAD"));

        assertThat(created.id()).isNotNull();

        // Push the insert to the database and empty the persistence context so
        // the next read comes from Postgres rather than Hibernate's in-memory
        // copy. Without this, createdAt reads as null: the column is filled by
        // a DEFAULT now(), and the object in memory has never seen that value.
        //
        // Asserting after a real round trip is the stronger test anyway: it
        // proves the value reached the database, not just the object.
        entityManager.flush();
        entityManager.clear();

        var fetched = accountService.findById(created.id());
        assertThat(fetched.externalId()).isEqualTo("12345678");
        assertThat(fetched.accountType()).isEqualTo(AccountType.TFSA);
        assertThat(fetched.createdAt()).isNotNull();
    }

    @Test
    void rejectsDuplicateExternalId() {
        accountService.create(new CreateAccountRequest(
                "99999999", "RRSP", AccountType.RRSP, "CAD"));

        assertThatThrownBy(() -> accountService.create(new CreateAccountRequest(
                "99999999", "RRSP copy", AccountType.RRSP, "CAD")))
                .isInstanceOf(ConflictException.class);
    }
}
