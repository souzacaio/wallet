package br.com.datum.wallet.service.dto;

import br.com.datum.wallet.domain.Transaction;

// replayed=true means the transaction already existed for this idempotency key.
public record TransactionResult(Transaction transaction, boolean replayed, String message) {
}
