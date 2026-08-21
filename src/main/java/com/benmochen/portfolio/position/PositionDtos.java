package com.benmochen.portfolio.position;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

public final class PositionDtos {

    private PositionDtos() {
    }

    public record PositionResponse(
            String symbol,
            String currency,
            BigDecimal quantity,
            BigDecimal costBasis,
            BigDecimal costPerUnit,
            BigDecimal realisedGain,
            BigDecimal dividendsReceived,
            BigDecimal commissionsPaid,
            /** Null when no price has been fetched for this instrument yet. */
            BigDecimal lastClose,
            LocalDate lastCloseDate,
            BigDecimal marketValue,
            BigDecimal unrealisedGain
    ) {
        static PositionResponse from(Position p) {
            return from(p, null, null);
        }

        static PositionResponse from(Position p, BigDecimal lastClose, LocalDate lastCloseDate) {
            BigDecimal marketValue = lastClose == null
                    ? null
                    : lastClose.multiply(p.getQuantity());
            BigDecimal unrealised = marketValue == null
                    ? null
                    : marketValue.subtract(p.getCostBasis());
            return new PositionResponse(
                    p.getSymbol(),
                    p.getCurrency(),
                    // Quantities keep more precision than money because
                    // fractional shares and splits produce long decimals.
                    p.getQuantity().setScale(6, RoundingMode.HALF_UP).stripTrailingZeros(),
                    money(p.getCostBasis()),
                    p.costPerUnit().setScale(4, RoundingMode.HALF_UP),
                    money(p.getRealisedGain()),
                    money(p.getDividendsReceived()),
                    money(p.getCommissionsPaid()),
                    lastClose == null ? null : lastClose.setScale(4, RoundingMode.HALF_UP),
                    lastCloseDate,
                    marketValue == null ? null : money(marketValue),
                    unrealised == null ? null : money(unrealised));
        }

        private static BigDecimal money(BigDecimal value) {
            return value.setScale(2, RoundingMode.HALF_UP);
        }
    }

    public record PositionsResponse(
            Long accountId,
            LocalDate asOf,
            List<PositionResponse> open,
            List<PositionResponse> closed
    ) {
    }
}
