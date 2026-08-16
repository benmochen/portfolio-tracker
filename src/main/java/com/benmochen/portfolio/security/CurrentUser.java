package com.benmochen.portfolio.security;

import com.benmochen.portfolio.user.AppUser;
import com.benmochen.portfolio.user.AppUserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Who is making this request.
 *
 * Services ask this rather than accepting a user id as a parameter, because a
 * parameter can be supplied by the caller and the caller is the person we are
 * defending against. The identity comes from the authenticated session only.
 */
@Component
public class CurrentUser {

    private final AppUserRepository appUserRepository;

    public CurrentUser(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    public AppUser require() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user on this request");
        }
        return appUserRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user is not in the database"));
    }

    public Long requireId() {
        return require().getId();
    }
}
