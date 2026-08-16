package com.benmochen.portfolio.instrument;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InstrumentRepository extends JpaRepository<Instrument, Long> {

    /**
     * An instrument is identified by symbol AND currency: DLR.TO and DLR.U are
     * the same fund listed twice, but they are separate holdings with separate
     * cost bases.
     */
    Optional<Instrument> findBySymbolAndCurrency(String symbol, String currency);

    Optional<Instrument> findByCompanyKeyAndCurrency(String companyKey, String currency);

    /**
     * Instruments whose stored prices are missing or stale, least recently
     * fetched first. Nulls sort first so a never-fetched holding is filled in
     * before one that only needs topping up.
     *
     * This ordering is what makes a 25-request daily quota workable: each run
     * spends its calls on whatever is furthest behind.
     */
    @Query("""
            select i from Instrument i
            where i.providerSymbol is not null
              and (i.pricesFetchedThrough is null or i.pricesFetchedThrough < :through)
            order by i.pricesFetchedThrough asc nulls first, i.id asc
            """)
    List<Instrument> findNeedingPrices(@Param("through") LocalDate through);

    /**
     * Same as above, but only instruments you actually still hold.
     *
     * Net units are the signed sum of the ledger: buys and journals in are
     * positive, sells and journals out negative, so a sum of zero means the
     * position is closed. A closed holding has no market value worth knowing,
     * and with a 25-request daily quota, spending calls on securities you sold
     * two years ago is the difference between covering your portfolio and not.
     */
    @Query("""
            select i from Instrument i
            where i.providerSymbol is not null
              and (i.pricesFetchedThrough is null or i.pricesFetchedThrough < :through)
              and (select coalesce(sum(t.quantity), 0) from Transaction t
                     where t.instrument = i) <> 0
            order by i.pricesFetchedThrough asc nulls first, i.id asc
            """)
    List<Instrument> findHeldNeedingPrices(@Param("through") LocalDate through);
}
