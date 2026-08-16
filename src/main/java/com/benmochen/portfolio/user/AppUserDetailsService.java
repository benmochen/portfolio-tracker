package com.benmochen.portfolio.user;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Tells Spring Security how to look a user up. It handles the password
 * comparison itself, using the PasswordEncoder configured in SecurityConfig.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public AppUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AppUser user = appUserRepository.findByUsername(username)
                // The message deliberately does not say whether the username
                // exists. Distinguishing "no such user" from "wrong password"
                // tells an attacker which usernames are real.
                .orElseThrow(() -> new UsernameNotFoundException("Bad credentials"));

        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(!user.isEnabled())
                .authorities(List.of())
                .build();
    }
}
