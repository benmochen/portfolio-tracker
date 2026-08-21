package com.benmochen.portfolio.position;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * A holding as of some date, derived by replaying the ledger. Never stored.
 *
 * Cost basis uses the Canadian adjusted cost base (ACB) method: all units of
 * one security share a single average cost, recomputed on every purchase.
 * This is what CRA requires, and it differs from the FIFO method used in most
 * American examples, which would produce different realised gains.
 */
public final class Position {

    private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

    private final Long instrumentId;
    private final String symbol;
    private final String currency;

    private BigDecimal quantity = BigDecimal.ZERO;
    /** Total cost of the units currently held, including commissions paid. */
    private BigDecimal costBasis = BigDecimal.ZERO;
    private BigDecimal realisedGain = BigDecimal.ZERO;
    private BigDecimal dividendsReceived = BigDecimal.ZERO;
    private BigDecimal commissionsPaid = BigDecimal.ZERO;

    Position(Long instrumentId, String symbol, String currency) {
        this.instrumentId = instrumentId;
        this.symbol = symbol;
        this.currency = currency;
    }

    public Long getInstrumentId() {
        return instrumentId;
    }

    /**
     * A purchase raises both the unit count and the pooled cost.
     * Commission is part of the cost of acquiring, so it enters the basis.
     */
    void buy(BigDecimal units, BigDecimal cost, BigDecimal commission) {
        quantity = quantity.add(units);
        costBasis = costBasis.add(cost).add(commission);
        commissionsPaid = commissionsPaid.add(commission);
    }

    /**
     * A sale removes units at the AVERAGE cost, not at what those particular
     * units cost. The gain is proceeds minus the average cost of the units
     * released, and the remaining pool keeps the same per-unit cost.
     */
    void sell(BigDecimal unitsSold, BigDecimal proceeds, BigDecimal commission) {
        BigDecimal absUnits = unitsSold.abs();
        BigDecimal costRemoved = costPerUnit().multiply(absUnits, MC);

        quantity = quantity.subtract(absUnits);
        costBasis = costBasis.subtract(costRemoved);
        realisedGain = realisedGain.add(proceeds.abs().subtract(commission).subtract(costRemoved));
        commissionsPaid = commissionsPaid.add(commission);

        // Floating remainders from repeated averaging can leave a basis of a
        // few billionths on a fully closed position. Snap it to zero so the
        // position reads as closed rather than as holding 0 units at a cost.
        if (quantity.signum() == 0) {
            costBasis = BigDecimal.ZERO;
        }
    }

    /**
     * A stock split delivers extra units at no cost. The pooled cost is
     * unchanged, so the per-unit cost falls automatically. Nothing else needs
     * adjusting, which is why splits are cheap to handle when the ledger
     * records them as their own event.
     */
    void split(BigDecimal extraUnits) {
        quantity = quantity.add(extraUnits);
    }

    /**
     * Units leave for the other currency side of the same account. They stop
     * being held here and take their share of the pooled cost with them. No
     * gain is realised: nothing was sold.
     */
    void journalOut(BigDecimal units) {
        BigDecimal costRemoved = costPerUnit().multiply(units, MC);
        quantity = quantity.subtract(units);
        costBasis = costBasis.subtract(costRemoved);
        if (quantity.signum() == 0) {
            costBasis = BigDecimal.ZERO;
        }
    }

    /**
     * Units arrive from the other currency side, carrying a cost the broker
     * states in the row description, already converted into this currency.
     *
     * This is what makes Norbert's Gambit come out right: the units are then
     * sold at close to the cost they arrived with, so the realised gain is
     * near zero, which is correct. A currency conversion is not a capital gain.
     */
    void journalIn(BigDecimal units, BigDecimal cost) {
        quantity = quantity.add(units);
        costBasis = costBasis.add(cost);
    }

    void dividend(BigDecimal amount) {
        dividendsReceived = dividendsReceived.add(amount);
    }

    public BigDecimal costPerUnit() {
        if (quantity.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return costBasis.divide(quantity, MC);
    }

    public boolean isOpen() {
        return quantity.signum() != 0;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getCostBasis() {
        return costBasis;
    }

    public BigDecimal getRealisedGain() {
        return realisedGain;
    }

    public BigDecimal getDividendsReceived() {
        return dividendsReceived;
    }

    public BigDecimal getCommissionsPaid() {
        return commissionsPaid;
    }
}
