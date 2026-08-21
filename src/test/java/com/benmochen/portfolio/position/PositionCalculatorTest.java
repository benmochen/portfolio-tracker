package com.benmochen.portfolio.position;

import com.benmochen.portfolio.account.Account;
import com.benmochen.portfolio.account.AccountType;
import com.benmochen.portfolio.instrument.Instrument;
import com.benmochen.portfolio.instrument.InstrumentType;
import com.benmochen.portfolio.transaction.Transaction;
import com.benmochen.portfolio.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit tests: no Spring, no database. Every expected number below was
 * worked out by hand first, which is the point. A test that asserts whatever
 * the code happens to produce proves nothing.
 */
class PositionCalculatorTest {

    private final PositionCalculator calculator = new PositionCalculator();

    private static final Account ACCOUNT =
            new Account("test", "Test", AccountType.TFSA, "CAD", 1L);
    private static final Instrument ACME =
            new Instrument("ACME", null, "CAD", InstrumentType.EQUITY, "ACME INC");

    /** The same fund's USD listing: a separate instrument with its own basis. */
    private static final Instrument ACME_USD =
            new Instrument("ACMEU", null, "USD", InstrumentType.EQUITY, "ACME INC");

    @Test
    void averagesCostAcrossTwoPurchases() {
        // 10 units at $10 (+$5 fee) then 10 units at $20 (+$5 fee).
        // Pooled cost = 100 + 5 + 200 + 5 = 310 over 20 units = $15.50 each.
        var positions = calculator.calculate(List.of(
                buy("10", "10", "100", "5", "2024-01-01"),
                buy("10", "20", "200", "5", "2024-02-01")));

        Position acme = positions.get("ACME:CAD");
        assertThat(acme.getQuantity()).isEqualByComparingTo("20");
        assertThat(acme.getCostBasis()).isEqualByComparingTo("310");
        assertThat(acme.costPerUnit()).isEqualByComparingTo("15.50");
    }

    @Test
    void sellsAtAverageCostNotAtPurchaseCost() {
        // Same two buys, then sell 10 units for $250 gross with a $5 fee.
        // Cost released = 10 x 15.50 = 155. Gain = 250 - 5 - 155 = 90.
        // FIFO would have released the first 10 units at 105 and reported a
        // gain of 140, which is the wrong answer under Canadian rules.
        var positions = calculator.calculate(List.of(
                buy("10", "10", "100", "5", "2024-01-01"),
                buy("10", "20", "200", "5", "2024-02-01"),
                sell("-10", "25", "250", "5", "2024-03-01")));

        Position acme = positions.get("ACME:CAD");
        assertThat(acme.getQuantity()).isEqualByComparingTo("10");
        assertThat(acme.getCostBasis()).isEqualByComparingTo("155");
        assertThat(acme.getRealisedGain()).isEqualByComparingTo("90");
    }

    @Test
    void splitAddsUnitsWithoutChangingCost() {
        // 10 units costing 105 in total, then a 6-for-1 split delivers 50 more.
        // Cost stays at 105; per-unit cost drops from 10.50 to 1.75.
        var positions = calculator.calculate(List.of(
                buy("10", "10", "100", "5", "2024-01-01"),
                transaction(TransactionType.SPLIT_ADJUSTMENT, "50", "0", "0", "0", "0",
                        "2024-06-01")));

        Position acme = positions.get("ACME:CAD");
        assertThat(acme.getQuantity()).isEqualByComparingTo("60");
        assertThat(acme.getCostBasis()).isEqualByComparingTo("105");
        assertThat(acme.costPerUnit()).isEqualByComparingTo("1.75");
    }

    @Test
    void journalCarriesCostIntoTheReceivingCurrency() {
        // Norbert's Gambit, with the real shape of the data:
        // 376 units bought on the CAD side for 5355.04 CAD, journalled to the
        // USD side where the broker states BOOK VALUE: $3785.55 CNV@ 1.4109,
        // then sold there for 3784.00 USD.
        //
        // The book value is ALREADY in USD. The arriving cost is therefore
        // 3785.55, and selling at 3784.00 realises a loss of 1.55: a currency
        // conversion is not a capital gain. Dividing by the rate a second time
        // would put the cost at 2683.09 and invent a 1100 dollar profit.
        var positions = calculator.calculate(List.of(
                buy("376", "14.24", "5355.04", "0", "2026-07-20"),
                transaction(TransactionType.TRANSFER_OUT, "-376", "0", "0", "0", "0",
                        "2026-07-22"),
                transactionOnUsd(TransactionType.TRANSFER_IN, "376", "0",
                        "JOURNAL POSITION FROM CAD BOOK VALUE: $3785.55 CNV@ 1.4109",
                        "2026-07-22"),
                sellUsd("-376", "3784.00", "2026-07-23")));

        Position cadSide = positions.get("ACME:CAD");
        assertThat(cadSide.getQuantity()).isEqualByComparingTo("0");
        assertThat(cadSide.getCostBasis()).isEqualByComparingTo("0");
        assertThat(cadSide.getRealisedGain()).isEqualByComparingTo("0");

        Position usdSide = positions.get("ACMEU:USD");
        assertThat(usdSide.getQuantity()).isEqualByComparingTo("0");
        assertThat(usdSide.getRealisedGain()).isEqualByComparingTo("-1.55");
    }

    @Test
    void fullySoldPositionClosesCleanly() {
        var positions = calculator.calculate(List.of(
                buy("10", "10", "100", "0", "2024-01-01"),
                sell("-10", "12", "120", "0", "2024-02-01")));

        Position acme = positions.get("ACME:CAD");
        assertThat(acme.isOpen()).isFalse();
        assertThat(acme.getCostBasis()).isEqualByComparingTo("0");
        assertThat(acme.getRealisedGain()).isEqualByComparingTo("20");
    }

    @Test
    void dividendsDoNotAffectCostBasis() {
        var positions = calculator.calculate(List.of(
                buy("10", "10", "100", "0", "2024-01-01"),
                transaction(TransactionType.DIVIDEND, "0", "0", "0", "0", "7.50",
                        "2024-03-01")));

        Position acme = positions.get("ACME:CAD");
        assertThat(acme.getCostBasis()).isEqualByComparingTo("100");
        assertThat(acme.getDividendsReceived()).isEqualByComparingTo("7.50");
    }

    private Transaction buy(String qty, String price, String gross, String commission,
                            String date) {
        return transaction(TransactionType.BUY, qty, price, gross, commission,
                "-" + gross, date);
    }

    private Transaction sell(String qty, String price, String gross, String commission,
                             String date) {
        return transaction(TransactionType.SELL, qty, price, gross, commission, gross, date);
    }

    private Transaction transactionOnUsd(TransactionType type, String qty, String net,
                                         String description, String date) {
        return new Transaction(
                ACCOUNT, ACME_USD, type,
                LocalDate.parse(date), LocalDate.parse(date),
                new BigDecimal(qty), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal(net),
                "USD", description, new byte[]{2}, (short) 1);
    }

    private Transaction sellUsd(String qty, String gross, String date) {
        return new Transaction(
                ACCOUNT, ACME_USD, TransactionType.SELL,
                LocalDate.parse(date), LocalDate.parse(date),
                new BigDecimal(qty), BigDecimal.ZERO, new BigDecimal(gross),
                BigDecimal.ZERO, new BigDecimal(gross),
                "USD", null, new byte[]{3}, (short) 1);
    }

    private Transaction transaction(TransactionType type, String qty, String price,
                                    String gross, String commission, String net,
                                    String date) {
        return new Transaction(
                ACCOUNT, ACME, type,
                LocalDate.parse(date), LocalDate.parse(date),
                new BigDecimal(qty), new BigDecimal(price), new BigDecimal(gross),
                new BigDecimal(commission), new BigDecimal(net),
                "CAD", null, new byte[]{1}, (short) 1);
    }
}
