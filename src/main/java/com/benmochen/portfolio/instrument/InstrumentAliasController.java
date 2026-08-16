package com.benmochen.portfolio.instrument;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lets you see how every raw broker symbol was interpreted. This is the
 * check that the resolution actually did the right thing, rather than
 * trusting it silently.
 */
@RestController
@RequestMapping("/api/instrument-aliases")
public class InstrumentAliasController {

    private final InstrumentAliasRepository aliasRepository;
    private final InstrumentRepository instrumentRepository;

    public InstrumentAliasController(InstrumentAliasRepository aliasRepository,
                                     InstrumentRepository instrumentRepository) {
        this.aliasRepository = aliasRepository;
        this.instrumentRepository = instrumentRepository;
    }

    public record AliasView(String rawSymbol, String currency, String resolvedSymbol,
                            String companyKey, InstrumentAlias.Source source) {
    }

    @GetMapping
    public List<AliasView> list() {
        return aliasRepository.findAll().stream()
                .map(alias -> {
                    Instrument instrument = instrumentRepository
                            .findById(alias.getInstrumentId()).orElse(null);
                    return new AliasView(
                            alias.getRawSymbol(),
                            alias.getCurrency(),
                            instrument == null ? null : instrument.getSymbol(),
                            instrument == null ? null : instrument.getCompanyKey(),
                            alias.getSource());
                })
                .sorted(java.util.Comparator.comparing(AliasView::rawSymbol))
                .toList();
    }
}
