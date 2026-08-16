package com.benmochen.portfolio.instrument;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InstrumentAliasRepository
        extends JpaRepository<InstrumentAlias, InstrumentAlias.Key> {

    Optional<InstrumentAlias> findByRawSymbolAndCurrency(String rawSymbol, String currency);

    List<InstrumentAlias> findBySource(InstrumentAlias.Source source);
}
