package com.benmochen.portfolio.returns;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Time-weighted return: how the investments performed, independent of when
 * money was added or removed.
 *
 * This is the counterpart to money-weighted return. If you deposit a large sum
 * just before a rally, your money-weighted return looks great because most of
 * your capital caught the rise. Time-weighted return ignores that entirely,
 * which is why it is the standard for comparing funds and benchmarks: it
 * measures the strategy, not the timing of contributions.
 *
 * The method is chain-linking. The timeline is cut at every deposit and
 * withdrawal, each sub-period's return is computed on its own, and the results
 * are multiplied together. Because each sub-period has no flows inside it, its
 * return cannot be distorted by one.
 */
public final class TimeWeightedReturn {

    private static final double DAYS_PER_YEAR = 365.0;

    private TimeWeightedReturn() {
    }

    /**
     * A moment where the portfolio is valued.
     *
     * @param valueBeforeFlow total value just BEFORE any money moved that day
     * @param flow            external money in (positive) or out (negative)
     */
    public record ValuationPoint(LocalDate date, BigDecimal valueBeforeFlow, BigDecimal flow) {
    }

    public record Result(double cumulative, double annualised, int subPeriods) {
    }

    /**
     * @param points sorted by date; the first is the opening valuation and the
     *               last is the closing one, whose flow is ignored
     * @return null when the points do not describe a measurable period
     */
    public static Result calculate(List<ValuationPoint> points) {
        if (points == null || points.size() < 2) {
            return null;
        }

        double compounded = 1.0;
        int counted = 0;

        for (int i = 0; i < points.size() - 1; i++) {
            ValuationPoint from = points.get(i);
            ValuationPoint to = points.get(i + 1);

            // The sub-period starts with whatever was there plus whatever was
            // added that day. Adding the flow to the START is what removes its
            // effect from the return: the new money is treated as having been
            // present the whole sub-period rather than as a gain.
            double start = from.valueBeforeFlow().doubleValue() + from.flow().doubleValue();
            double end = to.valueBeforeFlow().doubleValue();

            // A sub-period that starts at zero or negative has no meaningful
            // return: dividing by it would produce infinity, and skipping it
            // is correct because no capital was at work.
            if (start <= 0) {
                continue;
            }
            compounded *= end / start;
            counted++;
        }

        if (counted == 0) {
            return null;
        }

        double cumulative = compounded - 1.0;

        long days = ChronoUnit.DAYS.between(
                points.get(0).date(), points.get(points.size() - 1).date());
        if (days <= 0) {
            return new Result(cumulative, cumulative, counted);
        }

        // Annualising a short window magnifies it: a 2% gain over three weeks
        // becomes about 40% a year. Reported anyway, but the caller should
        // show the window length alongside it.
        double annualised = Math.pow(compounded, DAYS_PER_YEAR / days) - 1.0;

        return new Result(cumulative, annualised, counted);
    }
}
