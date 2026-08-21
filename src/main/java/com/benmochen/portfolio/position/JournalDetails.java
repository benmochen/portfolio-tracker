package com.benmochen.portfolio.position;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The cost that travels with journalled units, read out of the broker's
 * description text.
 *
 * Questrade writes the receiving leg of a journal like this:
 *
 *   ... JOURNAL POSITION FROM CAD BOOK VALUE: $3785.55 CNV@ 1.4109
 *
 * That is the broker stating the cost basis to carry across and the rate to
 * carry it at, which is exactly what is needed and would otherwise have to be
 * reconstructed from separate FX data.
 */
public record JournalDetails(BigDecimal bookValue, BigDecimal conversionRate) {

    private static final Pattern BOOK_VALUE =
            Pattern.compile("BOOK VALUE:\\s*\\$?\\s*([0-9]+(?:\\.[0-9]+)?)");

    private static final Pattern CONVERSION_RATE =
            Pattern.compile("CNV@\\s*([0-9]+(?:\\.[0-9]+)?)");

    /** @return null when the description carries no book value */
    public static JournalDetails parse(String description) {
        if (description == null) {
            return null;
        }
        Matcher book = BOOK_VALUE.matcher(description);
        if (!book.find()) {
            return null;
        }
        Matcher rate = CONVERSION_RATE.matcher(description);
        BigDecimal conversion = rate.find() ? new BigDecimal(rate.group(1)) : BigDecimal.ONE;
        return new JournalDetails(new BigDecimal(book.group(1)), conversion);
    }

    /**
     * The cost basis to attach to the arriving units.
     *
     * The book value is already stated in the RECEIVING currency; CNV@ merely
     * records the rate the broker used to get there. Dividing by it a second
     * time understates the cost and turns a break-even conversion into a large
     * fictitious gain.
     *
     * Checked against real data: three journals whose CAD purchases cost
     * 19,701.46 carry stated book values totalling 14,241.47, and the USD
     * sales that followed brought in 14,238.15. Cost and proceeds agree to
     * about three dollars, which is what a currency conversion should look
     * like. Dividing by the rate would have made the cost 10,304.85 and
     * invented a 3,933 gain.
     */
    public BigDecimal costInReceivingCurrency() {
        return bookValue;
    }
}
