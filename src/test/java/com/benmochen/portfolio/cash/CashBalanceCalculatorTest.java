package com.benmochen.portfolio.cash;

import com.benmochen.portfolio.account.Account;
import com.benmochen.portfolio.account.AccountType;
import com.benmochen.portfolio.transaction.Transaction;
import com.benmochen.portfolio.transaction.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CashBalanceCalculatorTest {

    private final CashBalanceCalculator calculator = new CashBalanceCalculator();

    private static final Account ACCOUNT =
            new Account("test", "Test", AccountType.TFSA, "CAD", 1L);

    @Test
    void sumsSignedCashEffects() {
        // Deposit 1000, buy something for 600, collect a 12 dividend, pay a
        // 5 fee. 1000 - 600 + 12 - 5 = 407.
        var balances = calculator.calculate(List.of(
                cash(TransactionType.DEPOSIT, "1000", "CAD"),
                cash(TransactionType.BUY, "-600", "CAD"),
                cash(TransactionType.DIVIDEND, "12", "CAD"),
                cash(TransactionType.FEE, "-5", "CAD")));

        assertThat(balances).hasSize(1);
        assertThat(balances.get(0).currency()).isEqualTo("CAD");
        assertThat(balances.get(0).amount()).isEqualByComparingTo("407");
    }

    @Test
    void keepsCurrenciesSeparate() {
        // An FX conversion is a pair: money leaves one side and lands on the
        // other. They must not net to zero against each other.
        var balances = calculator.calculate(List.of(
                cash(TransactionType.DEPOSIT, "1000", "CAD"),
                cash(TransactionType.FX_CONVERSION, "-500", "CAD"),
                cash(TransactionType.FX_CONVERSION, "360", "USD")));

        assertThat(balances).hasSize(2);
        assertThat(balances.get(0).currency()).isEqualTo("CAD");
        assertThat(balances.get(0).amount()).isEqualByComparingTo("500");
        assertThat(balances.get(1).currency()).isEqualTo("USD");
        assertThat(balances.get(1).amount()).isEqualByComparingTo("360");
    }

    @Test
    void ignoresZeroValueLedgerEntries() {
        // Journalling and split rows carry no cash effect.
        var balances = calculator.calculate(List.of(
                cash(TransactionType.DEPOSIT, "100", "CAD"),
                cash(TransactionType.TRANSFER_OUT, "0", "CAD"),
                cash(TransactionType.SPLIT_ADJUSTMENT, "0", "CAD")));

        assertThat(balances).hasSize(1);
        assertThat(balances.get(0).amount()).isEqualByComparingTo("100");
    }

    private Transaction cash(TransactionType type, String net, String currency) {
        return new Transaction(
                ACCOUNT, null, type,
                LocalDate.parse("2025-01-01"), LocalDate.parse("2025-01-01"),
                null, null, null, BigDecimal.ZERO, new BigDecimal(net),
                currency, null, new byte[]{1}, (short) 1);
    }
}
