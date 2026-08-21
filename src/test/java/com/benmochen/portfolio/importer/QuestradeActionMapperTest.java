package com.benmochen.portfolio.importer;

import com.benmochen.portfolio.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every case here comes from a real Questrade export, not from documentation.
 * The awkward ones are the point: the obvious Buy and Sell codes were never
 * where the bugs were.
 */
class QuestradeActionMapperTest {

    private final QuestradeActionMapper mapper = new QuestradeActionMapper();

    @Test
    void mapsTheStraightforwardCodes() {
        assertThat(mapper.map(row("Buy", "Trades", "10", null))).isEqualTo(TransactionType.BUY);
        assertThat(mapper.map(row("Sell", "Trades", "-10", null))).isEqualTo(TransactionType.SELL);
        assertThat(mapper.map(row("DIV", "Dividends", "0", null)))
                .isEqualTo(TransactionType.DIVIDEND);
        assertThat(mapper.map(row("CON", "Deposits", "0", null)))
                .isEqualTo(TransactionType.DEPOSIT);
        assertThat(mapper.map(row("FXT", "FX conversion", "0", null)))
                .isEqualTo(TransactionType.FX_CONVERSION);
        assertThat(mapper.map(row("FCH", "Fees and rebates", "0", null)))
                .isEqualTo(TransactionType.FEE);
    }

    @Test
    void treatsABlankActionAsItsActivityType() {
        // 23 rows in the real export have no Action at all. Their Activity
        // Type says Dividends and the description reads "DIST ON n SHS".
        // Without this fallback the import aborts on the first one.
        assertThat(mapper.map(row("", "Dividends", "0", "DIST ON 56 SHS REC 06/30 PAY 07/07")))
                .isEqualTo(TransactionType.DIVIDEND);
    }

    @Test
    void readsJournalDirectionFromTheQuantitySign() {
        // BRW is Norbert's Gambit. The same code appears on both legs, so the
        // only thing distinguishing them is whether units are arriving or
        // leaving. Getting this backwards moves cost the wrong way.
        assertThat(mapper.map(row("BRW", "Other", "-376", "JOURNAL POSITION TO USD")))
                .isEqualTo(TransactionType.TRANSFER_OUT);
        assertThat(mapper.map(row("BRW", "Other", "376", "JOURNAL POSITION FROM CAD")))
                .isEqualTo(TransactionType.TRANSFER_IN);
    }

    @Test
    void mapsDisToASplitRatherThanADistribution() {
        // DIS looks like "distribution" and is not: the real row reads
        // "... STK SPLIT ON 10 SHS". Treating it as a dividend would leave
        // 50 delivered shares out of the position entirely.
        assertThat(mapper.map(row("DIS", "Other", "50", "VANGUARD GROWTH ETF STK SPLIT ON 10 SHS")))
                .isEqualTo(TransactionType.SPLIT_ADJUSTMENT);
    }

    @Test
    void isCaseInsensitiveAboutCodes() {
        assertThat(mapper.map(row("buy", "Trades", "10", null))).isEqualTo(TransactionType.BUY);
        assertThat(mapper.map(row("  Sell  ", "Trades", "-10", null)))
                .isEqualTo(TransactionType.SELL);
    }

    @Test
    void failsLoudlyOnAnUnknownActivity() {
        // Silently defaulting an unrecognised row would corrupt a position and
        // leave nothing to find later. Refusing names the row instead.
        assertThatThrownBy(() -> mapper.map(row("ZZZ", "Something new", "0", null)))
                .isInstanceOf(ImportException.class)
                .hasMessageContaining("Unrecognised activity");
    }

    private ActivityRow row(String action, String activityType, String quantity,
                            String description) {
        return new ActivityRow(
                LocalDate.parse("2026-01-15"), LocalDate.parse("2026-01-17"),
                action, "TEST", description,
                quantity == null ? null : new BigDecimal(quantity),
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN,
                "CAD", "12345678", activityType, "Individual TFSA");
    }
}
