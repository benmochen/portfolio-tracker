package com.benmochen.portfolio.returns;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class SummaryDtos {

    private SummaryDtos() {
    }

    public record CashBalanceView(String currency, BigDecimal amount) {
    }

    /**
     * Time-weighted return, with the window it covers.
     *
     * The window is reported because it is not since-inception: the free
     * market data tier supplies only the last hundred trading days, so an
     * unlabelled percentage here would be badly misleading.
     */
    public record TimeWeightedReturnView(
            LocalDate from,
            LocalDate to,
            BigDecimal cumulativePct,
            BigDecimal annualisedPct,
            int subPeriods
    ) {
    }

    public record AccountSummary(
            Long accountId,
            LocalDate asOf,
            String reportingCurrency,

            /** Securities only. */
            BigDecimal marketValue,
            /** Uninvested cash, per currency, unconverted. */
            List<CashBalanceView> cashBalances,
            /** Securities plus cash, converted. What the account is actually worth. */
            BigDecimal totalValue,

            BigDecimal costBasis,
            BigDecimal unrealisedGain,
            BigDecimal realisedGain,
            BigDecimal dividendsReceived,
            BigDecimal netDeposits,

            /** Annualised money-weighted return as a percentage. Null if undetermined. */
            BigDecimal moneyWeightedReturnPct,
            /** Null when prices do not cover a measurable window. */
            TimeWeightedReturnView timeWeightedReturn,
            List<String> warnings
    ) {
    }
}
