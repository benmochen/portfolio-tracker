package com.benmochen.portfolio.importer;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.StringJoiner;

/**
 * Produces the SHA-256 digest that makes imports idempotent.
 *
 * Questrade's export has no per-row identifier, so identity has to be derived
 * from the content. The digest covers every field that distinguishes one
 * activity from another, and deliberately excludes anything that could vary
 * between two exports of the same underlying activity.
 *
 * BigDecimal values are normalised with stripTrailingZeros() first, because
 * "10" and "10.00000" are the same amount but different strings, and a
 * different string would produce a different digest and therefore a duplicate.
 */
@Component
public class RowHasher {

    public byte[] hash(ActivityRow row) {
        StringJoiner joiner = new StringJoiner("\u001F");
        joiner.add(String.valueOf(row.accountNumber()));
        joiner.add(String.valueOf(row.transactionDate()));
        joiner.add(String.valueOf(row.settlementDate()));
        joiner.add(String.valueOf(row.action()));
        joiner.add(String.valueOf(row.symbol()));
        joiner.add(String.valueOf(row.description()));
        joiner.add(normalise(row.quantity()));
        joiner.add(normalise(row.price()));
        joiner.add(normalise(row.grossAmount()));
        joiner.add(normalise(row.commission()));
        joiner.add(normalise(row.netAmount()));
        joiner.add(String.valueOf(row.currency()));

        return digest(joiner.toString());
    }

    public byte[] digest(String value) {
        return digest(value.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] digest(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required to exist on every JVM, so this cannot happen.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String normalise(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }
}
