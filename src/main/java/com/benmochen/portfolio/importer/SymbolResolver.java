package com.benmochen.portfolio.importer;

import com.benmochen.portfolio.instrument.Instrument;
import com.benmochen.portfolio.instrument.InstrumentAlias;
import com.benmochen.portfolio.instrument.InstrumentAliasRepository;
import com.benmochen.portfolio.instrument.InstrumentRepository;
import com.benmochen.portfolio.instrument.InstrumentType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Turns a raw broker symbol into the one instrument it actually refers to.
 *
 * The Questrade export names the same holding three ways:
 *   T.TO       on trade rows
 *   .T         on dividend rows
 *   N003056    an internal code, on some dividend and corporate-action rows
 *
 * Resolution runs in order, cheapest and most certain first:
 *   1. a previously recorded alias for this exact string
 *   2. the normalised ticker (leading dot and ".TO" suffix removed)
 *   3. the issuer name parsed out of the row description
 *   4. give up, create a placeholder, and report it as a warning
 *
 * Step 4 exists so a symbol we cannot place is visible rather than silently
 * becoming a second copy of a holding you already own.
 */
@Component
public class SymbolResolver {

    /** Questrade internal identifiers look like one letter then six digits. */
    private static final Pattern OPAQUE_CODE = Pattern.compile("^[A-Z]\\d{6}$");

    /**
     * Boilerplate that follows the issuer name in a description. The text
     * before the earliest of these markers is the issuer.
     */
    private static final List<String> DESCRIPTION_MARKERS = List.of(
            "WE ACTED AS AGENT",
            "CASH DIV ON",
            "DIST ON",
            "NON-RES TAX WITHHELD",
            "STK SPLIT ON",
            "JOURNAL POSITION",
            "BOOK VALUE",
            "CONVERSION -",
            "AVG PRICE",
            " REC ",
            " PAY ");

    private final InstrumentRepository instrumentRepository;
    private final InstrumentAliasRepository aliasRepository;

    public SymbolResolver(InstrumentRepository instrumentRepository,
                          InstrumentAliasRepository aliasRepository) {
        this.instrumentRepository = instrumentRepository;
        this.aliasRepository = aliasRepository;
    }

    /**
     * First pass of an import: register every row that carries a real ticker,
     * so the company-name index exists before any opaque code is resolved.
     *
     * Without this, resolution depends on the order of rows in the file. A
     * Questrade export lists newest activity first, so a dividend row bearing
     * an internal code like N003056 is processed before the older purchase row
     * that says NVDA. The index is then empty, the code cannot be placed, and
     * the same holding ends up split across two instruments.
     */
    public void prime(List<ActivityRow> rows, List<String> warnings) {
        for (ActivityRow row : rows) {
            String symbol = row.symbol();
            if (symbol == null || symbol.isBlank()) {
                continue;
            }
            if (OPAQUE_CODE.matcher(symbol.trim().toUpperCase(Locale.ROOT)).matches()) {
                continue;
            }
            resolve(symbol, row.description(), row.currency(), warnings);
        }
    }

    /**
     * @param warnings collector for symbols that could not be placed; the
     *                 caller surfaces these in the import response
     */
    public Instrument resolve(String rawSymbol, String description, String currency,
                              List<String> warnings) {
        if (rawSymbol == null || rawSymbol.isBlank()) {
            return null;
        }
        String raw = rawSymbol.trim().toUpperCase(Locale.ROOT);

        Optional<InstrumentAlias> known =
                aliasRepository.findByRawSymbolAndCurrency(raw, currency);
        if (known.isPresent()) {
            return instrumentRepository.getReferenceById(known.get().getInstrumentId());
        }

        String companyKey = companyKey(description);
        String ticker = normaliseTicker(raw);

        // An opaque code carries no ticker information at all, so the issuer
        // name is the only thing that can place it.
        if (OPAQUE_CODE.matcher(raw).matches()) {
            Optional<Instrument> byCompany = companyKey == null
                    ? Optional.empty()
                    : instrumentRepository.findByCompanyKeyAndCurrency(companyKey, currency);

            if (byCompany.isPresent()) {
                record(raw, currency, byCompany.get(), InstrumentAlias.Source.COMPANY_KEY);
                return byCompany.get();
            }

            Instrument placeholder = createInstrument(raw, currency, companyKey);
            record(raw, currency, placeholder, InstrumentAlias.Source.UNRESOLVED);
            warnings.add("Could not match internal symbol '" + raw + "'"
                    + (companyKey == null ? "" : " (" + companyKey + ")")
                    + " to a known holding. It was stored on its own; add a manual "
                    + "alias if it belongs to an existing instrument.");
            return placeholder;
        }

        Optional<Instrument> byTicker =
                instrumentRepository.findBySymbolAndCurrency(ticker, currency);
        if (byTicker.isPresent()) {
            Instrument instrument = byTicker.get();
            // Backfill the issuer name if this row supplies one and the
            // instrument was created from a row that did not.
            if (instrument.getCompanyKey() == null && companyKey != null
                    && instrumentRepository
                        .findByCompanyKeyAndCurrency(companyKey, currency).isEmpty()) {
                instrument.setCompanyKey(companyKey);
            }
            record(raw, currency, instrument, raw.equals(ticker)
                    ? InstrumentAlias.Source.EXACT
                    : InstrumentAlias.Source.NORMALISED);
            return instrument;
        }

        if (companyKey != null) {
            Optional<Instrument> byCompany =
                    instrumentRepository.findByCompanyKeyAndCurrency(companyKey, currency);
            if (byCompany.isPresent()) {
                record(raw, currency, byCompany.get(), InstrumentAlias.Source.COMPANY_KEY);
                return byCompany.get();
            }
        }

        Instrument created = createInstrument(ticker, currency, companyKey);
        record(raw, currency, created, raw.equals(ticker)
                ? InstrumentAlias.Source.EXACT
                : InstrumentAlias.Source.NORMALISED);
        return created;
    }

    private Instrument createInstrument(String symbol, String currency, String companyKey) {
        // Everything is typed EQUITY here. Most of these are actually ETFs,
        // which is a known inaccuracy: nothing in the export distinguishes
        // them, and nothing downstream depends on the distinction yet.
        Instrument instrument = new Instrument(
                symbol, null, currency, InstrumentType.EQUITY, companyKey);
        instrument.setCompanyKey(companyKey);
        return instrumentRepository.save(instrument);
    }

    private void record(String rawSymbol, String currency, Instrument instrument,
                        InstrumentAlias.Source source) {
        aliasRepository.save(
                new InstrumentAlias(rawSymbol, currency, instrument.getId(), source));
    }

    /**
     * ".T" and "T.TO" both mean Telus. Strips the leading dot used on
     * dividend rows and the ".TO" exchange suffix used on trade rows.
     */
    String normaliseTicker(String raw) {
        String ticker = raw;
        if (ticker.startsWith(".")) {
            ticker = ticker.substring(1);
        }
        if (ticker.endsWith(".TO")) {
            ticker = ticker.substring(0, ticker.length() - 3);
        }
        return ticker;
    }

    /**
     * Extracts the issuer name from a description by cutting at the first
     * piece of broker boilerplate.
     *
     *   "NVIDIA CORP CASH DIV ON 1 SHS REC ..."  -> "NVIDIA CORP"
     *   "NVIDIA CORP WE ACTED AS AGENT"          -> "NVIDIA CORP"
     *
     * Both rows therefore agree on the issuer even though one says NVDA and
     * the other says N003056.
     */
    String companyKey(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String text = description.toUpperCase(Locale.ROOT).replaceAll("\\s+", " ").trim();

        int cut = text.length();
        for (String marker : DESCRIPTION_MARKERS) {
            int at = text.indexOf(marker);
            if (at > 0 && at < cut) {
                cut = at;
            }
        }
        String key = text.substring(0, cut).trim();
        if (key.length() > 255) {
            key = key.substring(0, 255).trim();
        }
        return key.isEmpty() ? null : key;
    }
}
