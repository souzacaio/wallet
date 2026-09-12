package br.com.datum.wallet.web.dto;

import br.com.datum.wallet.domain.Transaction;
import br.com.datum.wallet.domain.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID walletId,
        TransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String idempotencyKey,
        Instant createdAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getWalletId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getIdempotencyKey(),
                transaction.getCreatedAt()
        );
    }
}
