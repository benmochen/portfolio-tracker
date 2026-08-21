package com.benmochen.portfolio.importer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Symbol resolution collapsed 39 apparent instruments into 21 real ones.
 * These tests pin the two mechanisms that did it, without needing a database:
 * ticker normalisation and issuer-name extraction.
 */
class SymbolNormalisationTest {

    private final SymbolResolver resolver = new SymbolResolver(null, null);

    @Test
    void stripsTheExchangeSuffixAndTheLeadingDot() {
        // Trade rows say T.TO, dividend rows on the same stock say .T.
        assertThat(resolver.normaliseTicker("T.TO")).isEqualTo("T");
        assertThat(resolver.normaliseTicker(".T")).isEqualTo("T");
        assertThat(resolver.normaliseTicker("BCE.TO")).isEqualTo("BCE");
        assertThat(resolver.normaliseTicker(".ENB")).isEqualTo("ENB");
        assertThat(resolver.normaliseTicker("NVDA")).isEqualTo("NVDA");
    }

    @Test
    void extractsTheSameIssuerFromDifferentBoilerplate() {
        // This is what lets an opaque internal code find its ticker: both rows
        // name the issuer, then diverge into different broker boilerplate.
        String fromTrade = resolver.companyKey("NVIDIA CORP WE ACTED AS AGENT");
        String fromDividend = resolver.companyKey("NVIDIA CORP CASH DIV ON 1 SHS REC 09/11");

        assertThat(fromTrade).isEqualTo("NVIDIA CORP");
        assertThat(fromDividend).isEqualTo("NVIDIA CORP");
    }

    @Test
    void handlesTheOtherBoilerplateForms() {
        assertThat(resolver.companyKey(
                "ISHARES 20 PLUS YEAR TREASURY BOND ETF DIST ON 56.24910 SHS"))
                .isEqualTo("ISHARES 20 PLUS YEAR TREASURY BOND ETF");

        assertThat(resolver.companyKey(
                "GLOBAL X US DLR CURRENCY ETF UNIT CL A JOURNAL POSITION FROM CAD"))
                .isEqualTo("GLOBAL X US DLR CURRENCY ETF UNIT CL A");

        assertThat(resolver.companyKey(
                "ISHARES 20 PLUS YEAR TREASURY BOND ETF NON-RES TAX WITHHELD"))
                .isEqualTo("ISHARES 20 PLUS YEAR TREASURY BOND ETF");
    }

    @Test
    void collapsesWhitespaceAndReturnsNullForNothing() {
        assertThat(resolver.companyKey("  NVIDIA   CORP   WE ACTED AS AGENT "))
                .isEqualTo("NVIDIA CORP");
        assertThat(resolver.companyKey(null)).isNull();
        assertThat(resolver.companyKey("   ")).isNull();
    }
}
