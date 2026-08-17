package com.benmochen.portfolio.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * The ledger for one account in chronological order, with each row's
     * instrument already attached.
     *
     * "left join fetch" is what makes this one query instead of many. Without
     * it, Transaction.instrument is LAZY, so the first call to getInstrument()
     * on each distinct instrument triggers its own SELECT. Measured on a real
     * account: 21 extra queries, one per instrument, regardless of whether the
     * ledger held 237 rows or 5,000.
     *
     * LEFT rather than inner, because deposits, fees and FX conversions have
     * no instrument and an inner join would silently drop them from the
     * ledger, which would corrupt every derived figure.
     */
    @Query("""
            select t from Transaction t
            left join fetch t.instrument
            where t.account.id = :accountId
            order by t.tradeDate asc, t.sequenceNo asc
            """)
    List<Transaction> findLedger(@Param("accountId") Long accountId);

    @Query("""
            select t from Transaction t
            left join fetch t.instrument
            where t.account.id = :accountId and t.tradeDate <= :asOf
            order by t.tradeDate asc, t.sequenceNo asc
            """)
    List<Transaction> findLedgerAsOf(@Param("accountId") Long accountId,
                                     @Param("asOf") LocalDate asOf);

    /**
     * Used by the importer to decide how many copies of an identical row are
     * already stored, which determines the next `occurrence` value.
     */
    @Query("select coalesce(max(t.occurrence), 0) from Transaction t where t.rowHash = :rowHash")
    short maxOccurrenceForHash(@Param("rowHash") byte[] rowHash);
}
