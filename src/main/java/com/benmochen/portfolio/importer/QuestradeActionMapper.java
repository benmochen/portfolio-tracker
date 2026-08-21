package com.benmochen.portfolio.importer;

import com.benmochen.portfolio.transaction.TransactionType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Translates Questrade's Action codes into our TransactionType.
 *
 * The mapping was derived from a real export rather than from documentation,
 * so each non-obvious case is justified here:
 *
 *   CON  contributions into a registered account          -> DEPOSIT
 *   FXT  "CONVERSION - USD/CAD"                           -> FX_CONVERSION
 *   FCH  journaled fee/GST/QST lines                      -> FEE
 *   DIS  "... STK SPLIT ON n SHS ..."                     -> SPLIT_ADJUSTMENT
 *   BRW  "JOURNAL POSITION FROM CAD" / "TO USD", i.e. the
 *        two legs of Norbert's Gambit                     -> TRANSFER_OUT / TRANSFER_IN
 *   ""   blank Action on rows whose Activity Type is
 *        "Dividends" and whose description reads
 *        "DIST ON n SHS REC ... PAY ..."                  -> DIVIDEND
 *
 * BRW matters more than its row count suggests: treating those legs as an
 * ordinary buy and sell would double-count the position and corrupt cost
 * basis, because the same units are moving between the CAD and USD sides of
 * one account rather than being bought and sold.
 */
@Component
public class QuestradeActionMapper {

    public TransactionType map(ActivityRow row) {
        String action = row.action() == null ? "" : row.action().trim().toUpperCase(Locale.ROOT);
        String activityType = row.activityType() == null
                ? "" : row.activityType().trim().toUpperCase(Locale.ROOT);

        return switch (action) {
            case "BUY" -> TransactionType.BUY;
            case "SELL" -> TransactionType.SELL;
            case "DIV" -> TransactionType.DIVIDEND;
            case "CON" -> TransactionType.DEPOSIT;
            case "WDR" -> TransactionType.WITHDRAWAL;
            case "FXT" -> TransactionType.FX_CONVERSION;
            case "FCH" -> TransactionType.FEE;
            case "INT" -> TransactionType.INTEREST;
            case "TAX", "NRT" -> TransactionType.TAX;
            case "DIS" -> TransactionType.SPLIT_ADJUSTMENT;
            case "BRW" -> transferDirection(row);
            case "" -> fromActivityType(activityType, row);
            default -> fromActivityType(activityType, row);
        };
    }

    private TransactionType transferDirection(ActivityRow row) {
        BigDecimal quantity = row.quantity();
        boolean leaving = quantity != null && quantity.signum() < 0;
        return leaving ? TransactionType.TRANSFER_OUT : TransactionType.TRANSFER_IN;
    }

    /**
     * Fallback when the Action cell is blank or unrecognised. The Activity
     * Type column is coarser but always populated.
     */
    private TransactionType fromActivityType(String activityType, ActivityRow row) {
        return switch (activityType) {
            case "DIVIDENDS" -> TransactionType.DIVIDEND;
            case "DEPOSITS" -> TransactionType.DEPOSIT;
            case "WITHDRAWALS" -> TransactionType.WITHDRAWAL;
            case "FEES AND REBATES" -> TransactionType.FEE;
            case "INTEREST" -> TransactionType.INTEREST;
            case "FX CONVERSION" -> TransactionType.FX_CONVERSION;
            case "TRADES" -> tradeDirection(row);
            default -> throw new ImportException(
                    "Unrecognised activity on " + row.transactionDate()
                    + ": action='" + row.action() + "', type='" + row.activityType() + "'. "
                    + "Add a mapping for it in QuestradeActionMapper.");
        };
    }

    private TransactionType tradeDirection(ActivityRow row) {
        BigDecimal quantity = row.quantity();
        if (quantity == null) {
            throw new ImportException("Trade row with no quantity on " + row.transactionDate());
        }
        return quantity.signum() < 0 ? TransactionType.SELL : TransactionType.BUY;
    }
}
