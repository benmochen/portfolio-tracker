package com.benmochen.portfolio.pricing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PriceRepository extends JpaRepository<Price, PriceId> {

    /**
     * The most recent close on or before a date.
     *
     * Needed because markets close on weekends and holidays: valuing a
     * portfolio "as of Saturday" must fall back to Friday's close rather than
     * finding nothing.
     */
    @Query("""
            select p from Price p
            where p.id.instrumentId = :instrumentId
              and p.id.priceDate <= :asOf
            order by p.id.priceDate desc
            limit 1
            """)
    Optional<Price> findLatestOnOrBefore(@Param("instrumentId") Long instrumentId,
                                        @Param("asOf") LocalDate asOf);

    /**
     * The latest close on or before a date, for many instruments at once.
     *
     * Replaces one query per position. DISTINCT ON is PostgreSQL's way of
     * saying "the first row in each group": ordering by instrument then date
     * descending makes the first row per instrument its most recent close.
     *
     * Native SQL rather than JPQL because DISTINCT ON has no JPQL equivalent,
     * and the alternatives (a correlated subquery, or a window function) are
     * both harder to read and slower here. This ties the query to PostgreSQL,
     * which is a deliberate trade: the application already depends on
     * PostgreSQL for other reasons, and the tests run against a real one.
     *
     * Callers must not pass an empty collection; "in ()" is not valid SQL.
     */
    @Query(value = """
            select distinct on (p.instrument_id)
                   p.instrument_id, p.price_date, p.close, p.source, p.fetched_at
            from price p
            where p.instrument_id in (:instrumentIds)
              and p.price_date <= :asOf
            order by p.instrument_id, p.price_date desc
            """, nativeQuery = true)
    List<Price> findLatestOnOrBeforeForAll(
            @Param("instrumentIds") java.util.Collection<Long> instrumentIds,
            @Param("asOf") LocalDate asOf);

    /**
     * Earliest stored close for an instrument. Time-weighted return cannot
     * start before every held instrument has a price, or the opening
     * valuation would silently omit part of the portfolio.
     */
    @Query("select min(p.id.priceDate) from Price p where p.id.instrumentId = :instrumentId")
    Optional<LocalDate> findEarliestDate(@Param("instrumentId") Long instrumentId);
}
