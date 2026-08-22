package com.benmochen.portfolio.returns;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

/**
 * Internal rate of return over irregularly timed cash flows, the calculation
 * spreadsheets call XIRR.
 *
 * This is the return that answers "how did MY money do", because it accounts
 * for when each dollar arrived. Adding money right before a rally earns you a
 * better money-weighted return than adding it after, even though the fund
 * performed identically. That is the difference from time-weighted return,
 * which deliberately ignores timing to measure the fund itself.
 *
 * There is no closed-form solution: the rate appears in an exponent, so it is
 * found numerically. Newton-Raphson converges quickly when it converges, and
 * diverges on awkward inputs, so bisection is the fallback: slower, but it
 * cannot run away as long as the answer is bracketed.
 */
public final class MoneyWeightedReturn {

    private static final int MAX_ITERATIONS = 200;
    private static final double TOLERANCE = 1e-9;
    private static final double DAYS_PER_YEAR = 365.0;

    private MoneyWeightedReturn() {
    }

    /**
     * @return the annualised rate as a decimal (0.0734 means 7.34%), or null
     *         when the flows do not determine one
     */
    public static Double calculate(List<CashFlow> flows) {
        if (flows == null || flows.size() < 2) {
            return null;
        }

        List<CashFlow> sorted = flows.stream()
                .sorted(Comparator.comparing(CashFlow::date))
                .toList();

        // An IRR only exists if money went both in and out. All-negative or
        // all-positive flows have no rate that brings the net present value to
        // zero, and a solver given one will happily return nonsense.
        boolean hasPositive = sorted.stream().anyMatch(f -> f.amount().signum() > 0);
        boolean hasNegative = sorted.stream().anyMatch(f -> f.amount().signum() < 0);
        if (!hasPositive || !hasNegative) {
            return null;
        }

        LocalDate start = sorted.get(0).date();
        double[] amounts = sorted.stream().mapToDouble(f -> f.amount().doubleValue()).toArray();
        double[] years = sorted.stream()
                .mapToDouble(f -> ChronoUnit.DAYS.between(start, f.date()) / DAYS_PER_YEAR)
                .toArray();

        Double newton = solveByNewton(amounts, years);
        return newton != null ? newton : solveByBisection(amounts, years);
    }

    private static Double solveByNewton(double[] amounts, double[] years) {
        double rate = 0.1;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double npv = netPresentValue(amounts, years, rate);
            double derivative = netPresentValueDerivative(amounts, years, rate);

            if (Math.abs(derivative) < 1e-12) {
                return null;
            }
            double next = rate - npv / derivative;

            // A rate at or below -100% means the discount factor is zero or
            // negative, which is not a meaningful answer. Hand over to
            // bisection rather than iterating into nonsense.
            if (next <= -0.9999 || Double.isNaN(next) || Double.isInfinite(next)) {
                return null;
            }
            if (Math.abs(next - rate) < TOLERANCE) {
                return next;
            }
            rate = next;
        }
        return null;
    }

    /**
     * Brackets the answer between -99.99% and +1000%, then halves the interval
     * until it closes. Slow but reliable, and used only when Newton-Raphson
     * fails.
     */
    private static Double solveByBisection(double[] amounts, double[] years) {
        double low = -0.9999;
        double high = 10.0;

        double npvLow = netPresentValue(amounts, years, low);
        double npvHigh = netPresentValue(amounts, years, high);

        // Same sign at both ends means no root in this range.
        if (npvLow * npvHigh > 0) {
            return null;
        }

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double mid = (low + high) / 2;
            double npvMid = netPresentValue(amounts, years, mid);

            if (Math.abs(npvMid) < TOLERANCE || (high - low) < TOLERANCE) {
                return mid;
            }
            if (npvLow * npvMid < 0) {
                high = mid;
                npvHigh = npvMid;
            } else {
                low = mid;
                npvLow = npvMid;
            }
        }
        return (low + high) / 2;
    }

    /** Sum of every flow discounted back to the first flow's date. */
    private static double netPresentValue(double[] amounts, double[] years, double rate) {
        double total = 0;
        for (int i = 0; i < amounts.length; i++) {
            total += amounts[i] / Math.pow(1 + rate, years[i]);
        }
        return total;
    }

    /** d(NPV)/d(rate), which Newton-Raphson needs to pick its next guess. */
    private static double netPresentValueDerivative(double[] amounts, double[] years,
                                                    double rate) {
        double total = 0;
        for (int i = 0; i < amounts.length; i++) {
            total -= years[i] * amounts[i] / Math.pow(1 + rate, years[i] + 1);
        }
        return total;
    }
}
