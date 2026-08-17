package com.benmochen.portfolio.pricing;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One day's closing price, as returned by a market data provider. */
public record DailyClose(LocalDate date, BigDecimal close) {
}
