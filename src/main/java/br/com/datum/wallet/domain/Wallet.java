package br.com.datum.wallet.domain;

import br.com.datum.wallet.domain.exception.InsufficientBalanceException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "holder_name", nullable = false)
    private String holderName;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {
    }

    public static Wallet create(String holderName) {
        Wallet wallet = new Wallet();
        wallet.id = UUID.randomUUID();
        wallet.holderName = holderName;
        wallet.balance = BigDecimal.ZERO.setScale(2);
        Instant now = Instant.now();
        wallet.createdAt = now;
        wallet.updatedAt = now;
        return wallet;
    }

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
        this.updatedAt = Instant.now();
    }

    public void debit(BigDecimal amount) {
        BigDecimal result = this.balance.subtract(amount);
        if (result.signum() < 0) {
            throw new InsufficientBalanceException(id, balance, amount);
        }
        this.balance = result;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getHolderName() {
        return holderName;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
