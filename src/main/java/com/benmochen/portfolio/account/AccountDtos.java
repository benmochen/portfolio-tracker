package com.benmochen.portfolio.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;

/**
 * Request and response shapes for the account API.
 *
 * Deliberately separate from the Account entity: exposing entities over HTTP
 * couples the database schema to the public API, so a column rename becomes a
 * breaking API change.
 */
public final class AccountDtos {

    private AccountDtos() {
    }

    public record CreateAccountRequest(
            @NotBlank String externalId,
            @NotBlank String name,
            @NotNull AccountType accountType,
            @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO code")
            String currency
    ) {
    }

    public record UpdateAccountRequest(
            @NotBlank String name
    ) {
    }

    public record AccountResponse(
            Long id,
            String externalId,
            String name,
            AccountType accountType,
            String currency,
            Instant createdAt
    ) {
        public static AccountResponse from(Account a) {
            return new AccountResponse(
                    a.getId(),
                    a.getExternalId(),
                    a.getName(),
                    a.getAccountType(),
                    a.getCurrency(),
                    a.getCreatedAt());
        }
    }
}
