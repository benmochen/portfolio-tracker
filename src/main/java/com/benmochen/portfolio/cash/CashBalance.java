package com.benmochen.portfolio.cash;

import java.math.BigDecimal;

/** Uninvested money sitting in one currency of an account. */
public record CashBalance(String currency, BigDecimal amount) {
}
