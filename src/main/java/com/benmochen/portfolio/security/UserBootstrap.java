package com.benmochen.portfolio.security;

import com.benmochen.portfolio.account.AccountRepository;
import com.benmochen.portfolio.user.AppUser;
import com.benmochen.portfolio.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first user from environment variables on startup, if no users
 * exist yet.
 *
 * Chosen over an open registration endpoint, which would let anyone who finds
 * the URL create an account, and over a seeded SQL migration, which would put
 * a password hash in the repository forever. The password is read from the
 * environment, hashed immediately, and never stored or logged in plain form.
 */
@Component
public class UserBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserBootstrap.class);

    private final AppUserRepository appUserRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    public UserBootstrap(AppUserRepository appUserRepository,
                         AccountRepository accountRepository,
                         PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (appUserRepository.count() > 0) {
            return;
        }

        String username = System.getenv("APP_ADMIN_USERNAME");
        String password = System.getenv("APP_ADMIN_PASSWORD");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            log.warn("No users exist and APP_ADMIN_USERNAME / APP_ADMIN_PASSWORD are not set. "
                    + "Every request will be rejected until a user exists.");
            return;
        }

        AppUser user = appUserRepository.save(
                new AppUser(username, passwordEncoder.encode(password)));

        // Accounts created by the importer before users existed have no owner.
        // Claiming them here is a one-time migration path, not a design: a
        // system built with users from the start would never have orphans.
        int claimed = accountRepository.claimUnownedAccounts(user.getId());

        log.info("Created initial user '{}' and assigned {} existing accounts to it",
                username, claimed);
    }
}
