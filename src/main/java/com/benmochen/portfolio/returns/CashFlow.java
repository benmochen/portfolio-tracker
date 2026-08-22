package com.benmochen.portfolio.returns;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One external movement of money, from the investor's point of view.
 *
 * Sign convention: money you put in is NEGATIVE (it left your pocket), money
 * you take out or still hold is POSITIVE. Getting this backwards flips the
 * sign of the answer, which is the classic way to break an IRR calculation.
 */
public record CashFlow(LocalDate date, BigDecimal amount) {
}
