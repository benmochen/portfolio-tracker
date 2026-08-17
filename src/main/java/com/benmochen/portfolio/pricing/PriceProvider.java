package com.benmochen.portfolio.pricing;

import java.time.LocalDate;
import java.util.List;

/**
 * A source of historical closing prices.
 *
 * An interface rather than a direct call to one vendor, because free market
 * data tiers change often and unfavourably: Alpha Vantage's daily allowance
 * went from 500 to 100 to 25 requests. Swapping providers should be a new
 * class and a config change, not a rewrite of everything that reads prices.
 */
public interface PriceProvider {

    /** A short name recorded on every stored price, so bad data can be traced. */
    String name();

    /**
     * @param providerSymbol the symbol as THIS provider spells it, which is
     *                       not necessarily how the broker spells it
     * @param from           earliest date wanted; the provider may return more
     */
    List<DailyClose> fetchDailyCloses(String providerSymbol, LocalDate from);

    /** Look up how this provider spells a ticker. Used to fix bad mappings. */
    List<String> searchSymbols(String query);
}
