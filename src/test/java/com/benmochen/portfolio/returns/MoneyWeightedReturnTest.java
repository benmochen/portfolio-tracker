package com.benmochen.portfolio.returns;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Expected values worked out independently, not read off the implementation.
 * The first two are checkable by hand; the third is the case that motivates
 * the whole calculation.
 */
class MoneyWeightedReturnTest {

    @Test
    void singleDepositDoublingOverOneYearReturns100Percent() {
        // 1000 in, 2000 out exactly one year later.
        Double rate = MoneyWeightedReturn.calculate(List.of(
                flow("2024-01-01", "-1000"),
                flow("2025-01-01", "2000")));

        assertThat(rate).isCloseTo(1.00, within(0.01));
    }

    @Test
    void flatValueReturnsZero() {
        Double rate = MoneyWeightedReturn.calculate(List.of(
                flow("2024-01-01", "-5000"),
                flow("2026-01-01", "5000")));

        assertThat(rate).isCloseTo(0.0, within(0.0001));
    }

    @Test
    void timingOfContributionsChangesTheAnswer() {
        // Same 2000 invested and the same 2200 ending value, but in the second
        // case most of the money arrived only a month before the end. Its
        // money-weighted return is therefore much higher: the gain was earned
        // on a smaller average balance over a shorter time.
        Double early = MoneyWeightedReturn.calculate(List.of(
                flow("2024-01-01", "-1000"),
                flow("2024-02-01", "-1000"),
                flow("2025-01-01", "2200")));

        Double late = MoneyWeightedReturn.calculate(List.of(
                flow("2024-01-01", "-1000"),
                flow("2024-12-01", "-1000"),
                flow("2025-01-01", "2200")));

        assertThat(late).isGreaterThan(early);
    }

    @Test
    void returnsNullWhenNoMoneyEverCameBack() {
        // Deposits only, no ending value: no rate can bring this to zero.
        Double rate = MoneyWeightedReturn.calculate(List.of(
                flow("2024-01-01", "-1000"),
                flow("2024-06-01", "-1000")));

        assertThat(rate).isNull();
    }

    @Test
    void handlesALossWithoutRunningAway() {
        Double rate = MoneyWeightedReturn.calculate(List.of(
                flow("2024-01-01", "-1000"),
                flow("2025-01-01", "500")));

        assertThat(rate).isCloseTo(-0.50, within(0.01));
    }

    private static CashFlow flow(String date, String amount) {
        return new CashFlow(LocalDate.parse(date), new BigDecimal(amount));
    }
}
