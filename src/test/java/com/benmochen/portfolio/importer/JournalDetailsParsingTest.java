package com.benmochen.portfolio.importer;

import com.benmochen.portfolio.position.JournalDetails;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The journal description is the only place the broker states the cost basis
 * that travels with journalled units. Misreading it produced a $14,238
 * phantom gain, so the parsing is pinned here.
 */
class JournalDetailsParsingTest {

    @Test
    void readsBookValueAndRateFromARealDescription() {
        JournalDetails details = JournalDetails.parse(
                "GLOBAL X US DLR CURRENCY ETF UNIT CL A "
                + "JOURNAL POSITION FROM CAD BOOK VALUE: $3785.55 CNV@ 1.4109");

        assertThat(details).isNotNull();
        assertThat(details.bookValue()).isEqualByComparingTo("3785.55");
        assertThat(details.conversionRate()).isEqualByComparingTo("1.4109");
    }

    @Test
    void bookValueIsAlreadyInTheReceivingCurrency() {
        // Verified against real data: three journals whose CAD purchases cost
        // 19,701.46 carry stated book values totalling 14,241.47, and the USD
        // sales that followed brought in 14,238.15. Dividing by the rate again
        // would put the cost at 10,304.85 and invent a 3,933 gain.
        JournalDetails details = JournalDetails.parse(
                "JOURNAL POSITION FROM CAD BOOK VALUE: $3785.55 CNV@ 1.4109");

        assertThat(details.costInReceivingCurrency()).isEqualByComparingTo("3785.55");
    }

    @Test
    void returnsNullWhenThereIsNoBookValue() {
        // The sending leg carries no book value. Returning null lets the
        // calculator refuse rather than assume a cost of zero, which is
        // exactly the assumption that caused the phantom gain.
        assertThat(JournalDetails.parse("JOURNAL POSITION TO USD")).isNull();
        assertThat(JournalDetails.parse(null)).isNull();
    }
}
