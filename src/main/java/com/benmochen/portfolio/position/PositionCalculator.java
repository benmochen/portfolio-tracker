package com.benmochen.portfolio.position;

import com.benmochen.portfolio.transaction.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Replays a transaction ledger into positions.
 *
 * Pure computation: no database access, no Spring dependencies at runtime.
 * That is deliberate, because it makes this class testable with a handmade
 * list of transactions and a calculator you can check by hand, which is the
 * only way to be confident the numbers are right.
 *
 * Order matters. Cost basis is path-dependent: buy at 10 then buy at 20 then
 * sell is not the same as buy at 20 then buy at 10 then sell, so the caller
 * must supply transactions in chronological order.
 */
@Component
public class PositionCalculator {

    public Map<String, Position> calculate(List<Transaction> ledger) {
        Map<String, Position> positions = new LinkedHashMap<>();

        for (Transaction t : ledger) {
            if (t.getInstrument() == null) {
                // Deposits, fees, interest and FX conversions move cash but
                // touch no holding. Cash-level accounting is a separate
                // concern and is not modelled yet.
                continue;
            }

            String symbol = t.getInstrument().getSymbol();
            String currency = t.getInstrument().getCurrency();

            // Keyed by symbol AND currency, because that is what identifies an
            // instrument. Keying on the symbol alone re-merged DLR.TO and
            // DLR.U here even though the database had correctly separated
            // them, which pooled a CAD cost with USD proceeds all over again.
            String key = symbol + ":" + currency;
            Position position = positions.computeIfAbsent(key,
                    k -> new Position(t.getInstrument().getId(), symbol, currency));

            BigDecimal quantity = orZero(t.getQuantity());
            BigDecimal gross = orZero(t.getGrossAmount());
            BigDecimal commission = orZero(t.getCommission());
            BigDecimal net = orZero(t.getNetAmount());

            switch (t.getType()) {
                case BUY -> position.buy(quantity.abs(), gross.abs(), commission);
                case SELL -> position.sell(quantity, gross.abs(), commission);
                case SPLIT_ADJUSTMENT -> position.split(quantity);
                case TRANSFER_OUT -> position.journalOut(quantity.abs());
                case TRANSFER_IN -> {
                    // The receiving leg carries the cost basis in its
                    // description. Falling back to zero would repeat the old
                    // bug, so an unreadable description is worth knowing about
                    // rather than silently absorbing.
                    JournalDetails details = JournalDetails.parse(t.getDescription());
                    if (details == null) {
                        throw new IllegalStateException(
                                "Journal into " + symbol + " on " + t.getTradeDate()
                                + " has no BOOK VALUE in its description, so the cost "
                                + "basis that travelled with the units is unknown.");
                    }
                    position.journalIn(quantity.abs(), details.costInReceivingCurrency());
                }
                case DIVIDEND -> position.dividend(net);
                default -> {
                    // Nothing else changes a holding.
                }
            }
        }

        return positions;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
