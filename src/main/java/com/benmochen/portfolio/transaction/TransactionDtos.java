package com.benmochen.portfolio.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    public record TransactionResponse(
            Long id,
            Long accountId,
            String symbol,
            TransactionType type,
            LocalDate tradeDate,
            BigDecimal quantity,
            BigDecimal price,
            BigDecimal commission,
            BigDecimal netAmount,
            String currency,
            String description
    ) {
        public static TransactionResponse from(Transaction t) {
            return new TransactionResponse(
                    t.getId(),
                    t.getAccount().getId(),
                    t.getInstrument() == null ? null : t.getInstrument().getSymbol(),
                    t.getType(),
                    t.getTradeDate(),
                    t.getQuantity(),
                    t.getPrice(),
                    t.getCommission(),
                    t.getNetAmount(),
                    t.getCurrency(),
                    t.getDescription());
        }
    }
}
