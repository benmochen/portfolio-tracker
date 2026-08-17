package com.benmochen.portfolio.pricing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface FxRateRepository extends JpaRepository<FxRate, FxRateId> {

    /**
     * Most recent rate on or before a date. Needed because currency markets
     * close at weekends and on holidays: valuing on a Sunday must fall back to
     * Friday rather than finding nothing.
     */
    @Query("""
            select f from FxRate f
            where f.id.baseCurrency = :base
              and f.id.quoteCurrency = :quote
              and f.id.rateDate <= :asOf
            order by f.id.rateDate desc
            limit 1
            """)
    Optional<FxRate> findLatestOnOrBefore(@Param("base") String base,
                                         @Param("quote") String quote,
                                         @Param("asOf") LocalDate asOf);

    @Query("select max(f.id.rateDate) from FxRate f "
            + "where f.id.baseCurrency = :base and f.id.quoteCurrency = :quote")
    Optional<LocalDate> findLatestDate(@Param("base") String base,
                                       @Param("quote") String quote);
}
