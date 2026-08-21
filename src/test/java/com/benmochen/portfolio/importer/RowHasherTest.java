package com.benmochen.portfolio.importer;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The hash is what makes re-importing an overlapping export safe. These tests
 * pin the two properties that matter: equal rows hash equal even when written
 * differently, and different rows never collide.
 */
class RowHasherTest {

    private final RowHasher hasher = new RowHasher();

    @Test
    void identicalRowsHashIdentically() {
        assertThat(hasher.hash(row("10", "14.13", "2026-01-15")))
                .isEqualTo(hasher.hash(row("10", "14.13", "2026-01-15")));
    }

    @Test
    void trailingZerosDoNotChangeTheHash() {
        // "10" and "10.00000" are the same quantity. If they hashed
        // differently, a second export written with more decimal places would
        // reimport every row as new.
        assertThat(hasher.hash(row("10", "14.13", "2026-01-15")))
                .isEqualTo(hasher.hash(row("10.00000", "14.130", "2026-01-15")));
    }

    @Test
    void differentAmountsHashDifferently() {
        assertThat(hasher.hash(row("10", "14.13", "2026-01-15")))
                .isNotEqualTo(hasher.hash(row("11", "14.13", "2026-01-15")));
    }

    @Test
    void differentDatesHashDifferently() {
        assertThat(hasher.hash(row("10", "14.13", "2026-01-15")))
                .isNotEqualTo(hasher.hash(row("10", "14.13", "2026-01-16")));
    }

    private ActivityRow row(String quantity, String price, String date) {
        return new ActivityRow(
                LocalDate.parse(date), LocalDate.parse(date),
                "Buy", "TEST", "TEST CORP WE ACTED AS AGENT",
                new BigDecimal(quantity), new BigDecimal(price),
                new BigDecimal("141.30"), BigDecimal.ZERO, new BigDecimal("-141.30"),
                "CAD", "12345678", "Trades", "Individual TFSA");
    }
}
