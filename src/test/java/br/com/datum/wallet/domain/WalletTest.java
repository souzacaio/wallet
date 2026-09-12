package br.com.datum.wallet.domain;

import br.com.datum.wallet.domain.exception.InsufficientBalanceException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTest {

    @Test
    void newWalletStartsWithZeroBalance() {
        Wallet wallet = Wallet.create("Alice");
        assertThat(wallet.getBalance()).isEqualByComparingTo("0.00");
        assertThat(wallet.getId()).isNotNull();
    }

    @Test
    void creditIncreasesBalance() {
        Wallet wallet = Wallet.create("Alice");
        wallet.credit(new BigDecimal("100.50"));
        assertThat(wallet.getBalance()).isEqualByComparingTo("100.50");
    }

    @Test
    void debitDecreasesBalance() {
        Wallet wallet = Wallet.create("Alice");
        wallet.credit(new BigDecimal("100.00"));
        wallet.debit(new BigDecimal("30.00"));
        assertThat(wallet.getBalance()).isEqualByComparingTo("70.00");
    }

    @Test
    void debitToExactlyZeroIsAllowed() {
        Wallet wallet = Wallet.create("Alice");
        wallet.credit(new BigDecimal("40.00"));
        wallet.debit(new BigDecimal("40.00"));
        assertThat(wallet.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void debitThatWouldGoNegativeIsRejected() {
        Wallet wallet = Wallet.create("Alice");
        wallet.credit(new BigDecimal("10.00"));
        assertThatThrownBy(() -> wallet.debit(new BigDecimal("10.01")))
                .isInstanceOf(InsufficientBalanceException.class);
        assertThat(wallet.getBalance()).isEqualByComparingTo("10.00");
    }
}
