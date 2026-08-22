package com.benmochen.portfolio.returns;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TimeWeightedReturnTest {

    @Test
    void aDepositDoesNotCountAsAGain() {
        // Start at 1000. Halfway through, deposit 1000 with no market movement
        // at all, and end at 2000. Nothing was earned, so the return is zero.
        //
        // This is the whole reason the calculation exists: a naive
        // end-over-start comparison would report +100%.
        var result = TimeWeightedReturn.calculate(List.of(
                point("2026-01-01", "1000", "0"),
                point("2026-07-01", "1000", "1000"),
                point("2027-01-01", "2000", "0")));

        assertThat(result).isNotNull();
        assertThat(result.cumulative()).isCloseTo(0.0, within(0.0001));
        assertThat(result.subPeriods()).isEqualTo(2);
    }

    @Test
    void chainLinksTwoSubPeriods() {
        // Up 10%, then up 10% again: 1.10 x 1.10 = 1.21, so 21% cumulative,
        // not 20%.
        var result = TimeWeightedReturn.calculate(List.of(
                point("2026-01-01", "1000", "0"),
                point("2026-07-01", "1100", "0"),
                point("2027-01-01", "1210", "0")));

        assertThat(result.cumulative()).isCloseTo(0.21, within(0.0001));
    }

    @Test
    void ignoresTimingThatMoneyWeightedReturnWouldReward() {
        // Identical market performance, opposite contribution timing. Time
        // weighting must give the same answer for both; money weighting would
        // not.
        var depositEarly = TimeWeightedReturn.calculate(List.of(
                point("2026-01-01", "1000", "5000"),
                point("2026-07-01", "6600", "0"),
                point("2027-01-01", "7260", "0")));

        var depositLate = TimeWeightedReturn.calculate(List.of(
                point("2026-01-01", "1000", "0"),
                point("2026-07-01", "1100", "5000"),
                point("2027-01-01", "6710", "0")));

        assertThat(depositEarly.cumulative())
                .isCloseTo(depositLate.cumulative(), within(0.0001));
    }

    @Test
    void annualisesOverTheActualElapsedTime() {
        // 21% over exactly two years annualises to about 10%.
        var result = TimeWeightedReturn.calculate(List.of(
                point("2024-01-01", "1000", "0"),
                point("2026-01-01", "1210", "0")));

        assertThat(result.annualised()).isCloseTo(0.10, within(0.005));
    }

    @Test
    void skipsSubPeriodsThatStartWithNothingInvested() {
        // No capital was at work, so there is no return to measure. Dividing
        // by zero here would produce infinity.
        var result = TimeWeightedReturn.calculate(List.of(
                point("2026-01-01", "0", "0"),
                point("2026-02-01", "0", "1000"),
                point("2026-03-01", "1100", "0")));

        assertThat(result.subPeriods()).isEqualTo(1);
        assertThat(result.cumulative()).isCloseTo(0.10, within(0.0001));
    }

    @Test
    void returnsNullWithoutAtLeastTwoPoints() {
        assertThat(TimeWeightedReturn.calculate(List.of(point("2026-01-01", "1000", "0"))))
                .isNull();
    }

    private static TimeWeightedReturn.ValuationPoint point(String date, String value,
                                                           String flow) {
        return new TimeWeightedReturn.ValuationPoint(
                LocalDate.parse(date), new BigDecimal(value), new BigDecimal(flow));
    }
}
