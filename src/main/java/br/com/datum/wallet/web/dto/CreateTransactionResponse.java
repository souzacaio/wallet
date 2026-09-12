package br.com.datum.wallet.web.dto;

public record CreateTransactionResponse(String message, boolean replayed, TransactionResponse transaction) {
}
