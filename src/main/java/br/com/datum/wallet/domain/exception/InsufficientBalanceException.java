package br.com.datum.wallet.domain.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientBalanceException extends RuntimeException {

    private final UUID walletId;
    private final BigDecimal balance;
    private final BigDecimal amount;

    public InsufficientBalanceException(UUID walletId, BigDecimal balance, BigDecimal amount) {
        super("Debit of " + amount + " would make wallet " + walletId + " balance negative (current: " + balance + ")");
        this.walletId = walletId;
        this.balance = balance;
        this.amount = amount;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
