package com.benmochen.portfolio.returns;

import com.benmochen.portfolio.account.AccountRepository;
import com.benmochen.portfolio.cash.CashBalance;
import com.benmochen.portfolio.cash.CashBalanceCalculator;
import com.benmochen.portfolio.common.NotFoundException;
import com.benmochen.portfolio.security.CurrentUser;
import com.benmochen.portfolio.position.Position;
import com.benmochen.portfolio.position.PositionCalculator;
import com.benmochen.portfolio.pricing.FxRateService;
import com.benmochen.portfolio.pricing.Price;
import com.benmochen.portfolio.pricing.PriceRepository;
import com.benmochen.portfolio.transaction.Transaction;
import com.benmochen.portfolio.transaction.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Rolls the whole account up into one set of numbers, in one currency.
 *
 * Everything here is derived from the ledger and the price and FX tables.
 * Nothing is stored, so a corrected transaction changes every figure on the
 * next request rather than leaving a stale total behind.
 */
@Service
public class PortfolioSummaryService {

    private static final String REPORTING_CURRENCY = "CAD";

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PositionCalculator calculator;
    private final CashBalanceCalculator cashCalculator;
    private final PriceRepository priceRepository;
    private final FxRateService fxRateService;
    private final CurrentUser currentUser;

    public PortfolioSummaryService(AccountRepository accountRepository,
                                   TransactionRepository transactionRepository,
                                   PositionCalculator calculator,
                                   CashBalanceCalculator cashCalculator,
                                   PriceRepository priceRepository,
                                   FxRateService fxRateService,
                                   CurrentUser currentUser) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.calculator = calculator;
        this.cashCalculator = cashCalculator;
        this.priceRepository = priceRepository;
        this.fxRateService = fxRateService;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public SummaryDtos.AccountSummary summarise(Long accountId, LocalDate asOf) {
        if (!accountRepository.existsByIdAndUserId(accountId, currentUser.requireId())) {
            throw NotFoundException.of("Account", accountId);
        }
        LocalDate valuationDate = asOf == null ? LocalDate.now() : asOf;

        List<Transaction> ledger = asOf == null
                ? transactionRepository.findLedger(accountId)
                : transactionRepository.findLedgerAsOf(accountId, asOf);

        List<String> warnings = new ArrayList<>();

        BigDecimal marketValue = BigDecimal.ZERO;
        BigDecimal costBasis = BigDecimal.ZERO;
        BigDecimal realised = BigDecimal.ZERO;
        BigDecimal dividends = BigDecimal.ZERO;

        for (Position position : calculator.calculate(ledger).values()) {
            // Realised gains and dividends belong to closed positions too, so
            // they are summed for every position, not just open ones.
            realised = realised.add(toReporting(position.getRealisedGain(),
                    position.getCurrency(), valuationDate));
            dividends = dividends.add(toReporting(position.getDividendsReceived(),
                    position.getCurrency(), valuationDate));

            if (!position.isOpen()) {
                continue;
            }
            costBasis = costBasis.add(toReporting(position.getCostBasis(),
                    position.getCurrency(), valuationDate));

            Price price = priceRepository
                    .findLatestOnOrBefore(position.getInstrumentId(), valuationDate)
                    .orElse(null);
            if (price == null) {
                // Counted as missing rather than as zero: a holding valued at
                // nothing would understate the total and look like a loss.
                warnings.add("No price for " + position.getSymbol()
                        + " (" + position.getCurrency() + "), so it is excluded "
                        + "from market value.");
                continue;
            }
            BigDecimal value = price.getClose().multiply(position.getQuantity());
            marketValue = marketValue.add(
                    toReporting(value, position.getCurrency(), valuationDate));
        }

        // Uninvested cash is part of what the account is worth. Leaving it out
        // understates the ending value, and therefore understates the return:
        // every dividend and sale proceeds sits here until it is reinvested.
        List<CashBalance> cash = cashCalculator.calculate(ledger);
        BigDecimal cashInReporting = BigDecimal.ZERO;
        for (CashBalance balance : cash) {
            cashInReporting = cashInReporting.add(
                    toReporting(balance.amount(), balance.currency(), valuationDate));
        }
        BigDecimal totalValue = marketValue.add(cashInReporting);

        List<CashFlow> flows = externalCashFlows(ledger, valuationDate);
        BigDecimal netDeposits = flows.stream()
                .map(CashFlow::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .negate();

        // The ending value is the final cash flow: as if you liquidated today.
        // Without it the IRR would see only money going out and never coming
        // back, and no rate exists for that.
        List<CashFlow> withEndingValue = new ArrayList<>(flows);
        withEndingValue.add(new CashFlow(valuationDate, totalValue));

        SummaryDtos.TimeWeightedReturnView twr =
                timeWeightedReturn(ledger, valuationDate, warnings);

        Double rate = MoneyWeightedReturn.calculate(withEndingValue);
        if (rate == null && !warnings.isEmpty()) {
            warnings.add("Money-weighted return could not be computed, most likely "
                    + "because the missing prices above leave the ending value wrong.");
        }

        return new SummaryDtos.AccountSummary(
                accountId,
                asOf,
                REPORTING_CURRENCY,
                money(marketValue),
                cash.stream()
                        .map(b -> new SummaryDtos.CashBalanceView(
                                b.currency(), money(b.amount())))
                        .toList(),
                money(totalValue),
                money(costBasis),
                money(marketValue.subtract(costBasis)),
                money(realised),
                money(dividends),
                money(netDeposits),
                rate == null ? null
                        : BigDecimal.valueOf(rate * 100).setScale(2, RoundingMode.HALF_UP),
                twr,
                List.copyOf(warnings));
    }

    /**
     * The money that actually crossed the account boundary.
     *
     * Only deposits, withdrawals and transfers count. Buys, sells, dividends
     * and fees move money AROUND inside the account, and including them would
     * double-count: a purchase is not new money, it is the same money in a
     * different form.
     */
    private List<CashFlow> externalCashFlows(List<Transaction> ledger, LocalDate valuationDate) {
        List<CashFlow> flows = new ArrayList<>();
        for (Transaction t : ledger) {
            if (!t.getType().isExternalCashFlow()) {
                continue;
            }
            // A journal between the CAD and USD sides of the same account is
            // not external money, and its legs carry a zero net amount anyway.
            if (t.getInstrument() != null) {
                continue;
            }
            BigDecimal amount = toReporting(t.getNetAmount(), t.getCurrency(), t.getTradeDate());
            if (amount == null || amount.signum() == 0) {
                continue;
            }
            // A deposit arrives as a positive net amount on the account but is
            // negative to the investor: it is money leaving your pocket.
            flows.add(new CashFlow(t.getTradeDate(), amount.negate()));
        }
        return flows;
    }

    private BigDecimal toReporting(BigDecimal amount, String currency, LocalDate asOf) {
        if (amount == null || amount.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return fxRateService.convert(amount, currency, REPORTING_CURRENCY, asOf);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Time-weighted return over the window for which prices actually exist.
     *
     * The free market data tier returns only the last hundred trading days, so
     * this cannot reach back to the start of the ledger. Rather than refuse,
     * it measures the window it can and reports where that window begins, so
     * the number is never mistaken for a since-inception figure.
     */
    private SummaryDtos.TimeWeightedReturnView timeWeightedReturn(
            List<Transaction> ledger, LocalDate end, List<String> warnings) {

        LocalDate start = windowStart(ledger, end);
        if (start == null || !start.isBefore(end)) {
            return null;
        }

        // Cut the timeline at every external flow inside the window. Each
        // resulting sub-period contains no flows, so its return reflects the
        // investments alone.
        List<LocalDate> boundaries = new java.util.ArrayList<>();
        boundaries.add(start);
        for (Transaction t : ledger) {
            if (!t.getType().isExternalCashFlow() || t.getInstrument() != null) {
                continue;
            }
            LocalDate date = t.getTradeDate();
            if (date.isAfter(start) && date.isBefore(end) && !boundaries.contains(date)) {
                boundaries.add(date);
            }
        }
        boundaries.add(end);
        java.util.Collections.sort(boundaries);

        List<TimeWeightedReturn.ValuationPoint> points = new java.util.ArrayList<>();
        for (LocalDate date : boundaries) {
            points.add(new TimeWeightedReturn.ValuationPoint(
                    date, valueAt(ledger, date), flowOn(ledger, date)));
        }

        TimeWeightedReturn.Result result = TimeWeightedReturn.calculate(points);
        if (result == null) {
            return null;
        }

        long days = java.time.temporal.ChronoUnit.DAYS.between(start, end);
        if (days < 365) {
            warnings.add("Time-weighted return covers " + days + " days, from " + start
                    + ", not the full history. Annualising anything shorter than a year "
                    + "extrapolates, so read cumulativePct rather than annualisedPct.");
        }

        return new SummaryDtos.TimeWeightedReturnView(
                start,
                end,
                BigDecimal.valueOf(result.cumulative() * 100).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(result.annualised() * 100).setScale(2, RoundingMode.HALF_UP),
                result.subPeriods());
    }

    /**
     * The earliest date from which the whole window can be valued.
     *
     * Not simply the earliest price of what you hold TODAY. A holding you
     * owned during the window and have since sold still has to be priceable,
     * or the opening valuation silently omits it. An understated opening value
     * turns into apparent growth and inflates every sub-period after it, which
     * is exactly the bug this replaced: BCE and NVDA were missing from a March
     * valuation and the return read 35% instead of the truth.
     *
     * BCE has no data at all on the free market tier, so in practice this
     * pushes the window start to after BCE was sold.
     */
    private LocalDate windowStart(List<Transaction> ledger, LocalDate end) {
        LocalDate earliest = earliestPriceAcrossHoldings(ledger, end);
        if (earliest == null) {
            return null;
        }

        // Candidate starts: the earliest priced date, then every date the
        // portfolio's composition changes. The window can only begin once
        // everything held is priceable and stays that way.
        java.util.TreeSet<LocalDate> candidates = new java.util.TreeSet<>();
        candidates.add(earliest);
        for (Transaction t : ledger) {
            if (t.getTradeDate().isAfter(earliest) && t.getTradeDate().isBefore(end)) {
                candidates.add(t.getTradeDate());
            }
        }

        // Walk backwards from the end: the answer is the first candidate after
        // the LAST date at which something could not be valued.
        LocalDate lastUnpriceable = null;
        for (LocalDate candidate : candidates) {
            if (!fullyPriceable(ledger, candidate)) {
                lastUnpriceable = candidate;
            }
        }
        if (lastUnpriceable == null) {
            return earliest;
        }
        LocalDate after = candidates.higher(lastUnpriceable);
        return after != null ? after : null;
    }

    /** Earliest close stored for any instrument open at the end of the window. */
    private LocalDate earliestPriceAcrossHoldings(List<Transaction> ledger, LocalDate end) {
        LocalDate latestStart = null;
        for (Position position : calculator.calculate(ledger).values()) {
            if (!position.isOpen()) {
                continue;
            }
            LocalDate earliest = priceRepository
                    .findEarliestDate(position.getInstrumentId())
                    .orElse(null);
            if (earliest == null) {
                return null;
            }
            if (latestStart == null || earliest.isAfter(latestStart)) {
                latestStart = earliest;
            }
        }
        return latestStart;
    }

    /** True when every position open on this date has a price on or before it. */
    private boolean fullyPriceable(List<Transaction> ledger, LocalDate date) {
        List<Transaction> upTo = ledger.stream()
                .filter(t -> !t.getTradeDate().isAfter(date))
                .toList();

        for (Position position : calculator.calculate(upTo).values()) {
            if (!position.isOpen()) {
                continue;
            }
            if (priceRepository.findLatestOnOrBefore(position.getInstrumentId(), date)
                    .isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Total value on a date: securities at that date's close, plus cash. */
    private BigDecimal valueAt(List<Transaction> ledger, LocalDate date) {
        List<Transaction> upTo = ledger.stream()
                .filter(t -> !t.getTradeDate().isAfter(date))
                .toList();

        BigDecimal total = BigDecimal.ZERO;

        for (Position position : calculator.calculate(upTo).values()) {
            if (!position.isOpen()) {
                continue;
            }
            Price price = priceRepository
                    .findLatestOnOrBefore(position.getInstrumentId(), date)
                    .orElse(null);
            if (price == null) {
                continue;
            }
            total = total.add(toReporting(
                    price.getClose().multiply(position.getQuantity()),
                    position.getCurrency(), date));
        }

        for (CashBalance balance : cashCalculator.calculate(upTo)) {
            total = total.add(toReporting(balance.amount(), balance.currency(), date));
        }
        return total;
    }

    /** External money moving on exactly this date, in reporting currency. */
    private BigDecimal flowOn(List<Transaction> ledger, LocalDate date) {
        BigDecimal total = BigDecimal.ZERO;
        for (Transaction t : ledger) {
            if (!t.getType().isExternalCashFlow() || t.getInstrument() != null) {
                continue;
            }
            if (!t.getTradeDate().equals(date)) {
                continue;
            }
            total = total.add(toReporting(t.getNetAmount(), t.getCurrency(), date));
        }
        return total;
    }
}
