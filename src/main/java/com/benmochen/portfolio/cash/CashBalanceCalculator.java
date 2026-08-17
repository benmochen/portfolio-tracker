package com.benmochen.portfolio.cash;

import com.benmochen.portfolio.transaction.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cash balances, derived by replaying the ledger.
 *
 * The rule is simpler than it looks: every transaction already records its
 * signed effect on cash in netAmount. A purchase is negative, a sale positive,
 * a dividend positive, a fee negative. Summing netAmount per currency is the
 * balance, with no special case per transaction type.
 *
 * That is a direct payoff from storing the ledger as the source of truth. If
 * balances were stored and updated instead, every new transaction type would
 * need its own handling and any missed case would silently drift.
 *
 * Grouped by currency, not merged, because CAD and USD cash are genuinely
 * separate pools inside one Questrade account. Merging them would need an
 * exchange rate and would hide the FX conversions the ledger records.
 */
@Component
public class CashBalanceCalculator {

    public List<CashBalance> calculate(List<Transaction> ledger) {
        Map<String, BigDecimal> byCurrency = new LinkedHashMap<>();

        for (Transaction t : ledger) {
            BigDecimal net = t.getNetAmount();
            if (net == null || net.signum() == 0) {
                continue;
            }
            byCurrency.merge(t.getCurrency(), net, BigDecimal::add);
        }

        List<CashBalance> balances = new ArrayList<>();
        byCurrency.forEach((currency, amount) -> balances.add(new CashBalance(currency, amount)));
        balances.sort(java.util.Comparator.comparing(CashBalance::currency));
        return balances;
    }
}
