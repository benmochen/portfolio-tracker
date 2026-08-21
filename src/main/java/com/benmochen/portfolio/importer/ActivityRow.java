package com.benmochen.portfolio.importer;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One raw row of the Questrade Activities sheet, already converted from text
 * into real types but not yet interpreted.
 *
 * Kept separate from the Transaction entity on purpose: this is the shape of
 * the broker's file, which you do not control. Interpretation happens in
 * ImportService, so a change to Questrade's export format touches only the
 * reader and this record.
 */
public record ActivityRow(
        LocalDate transactionDate,
        LocalDate settlementDate,
        String action,
        String symbol,
        String description,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal grossAmount,
        BigDecimal commission,
        BigDecimal netAmount,
        String currency,
        String accountNumber,
        String activityType,
        String accountTypeLabel
) {
}
