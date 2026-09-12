package br.com.datum.wallet.service;

import br.com.datum.wallet.domain.Transaction;
import br.com.datum.wallet.domain.TransactionType;
import br.com.datum.wallet.domain.Wallet;
import br.com.datum.wallet.domain.exception.IdempotencyConflictException;
import br.com.datum.wallet.domain.exception.WalletNotFoundException;
import br.com.datum.wallet.repository.TransactionRepository;
import br.com.datum.wallet.repository.WalletRepository;
import br.com.datum.wallet.service.dto.TransactionResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class TransactionService {

    private static final String MESSAGE_CREATED = "Transação criada com sucesso.";
    private static final String MESSAGE_REPLAYED =
            "Transação já processada anteriormente para esta Idempotency-Key; nenhuma alteração foi aplicada.";

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public TransactionService(WalletRepository walletRepository, TransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransactionResult apply(UUID walletId, TransactionType type, BigDecimal amount, String idempotencyKey) {

        Wallet wallet = walletRepository.findByIdForUpdate(walletId)
                .orElseThrow(() -> new WalletNotFoundException(walletId));


        var existing = transactionRepository.findByWalletIdAndIdempotencyKey(walletId, idempotencyKey);
        if (existing.isPresent()) {
            Transaction previous = existing.get();
            if (previous.getType() != type || previous.getAmount().compareTo(amount) != 0) {
                throw new IdempotencyConflictException(idempotencyKey);
            }
            return new TransactionResult(previous, true, MESSAGE_REPLAYED);
        }

        if (type == TransactionType.CREDIT) {
            wallet.credit(amount);
        } else {
            wallet.debit(amount);
        }

        Transaction transaction = Transaction.of(walletId, type, amount, wallet.getBalance(), idempotencyKey);
        return new TransactionResult(transactionRepository.save(transaction), false, MESSAGE_CREATED);
    }

    @Transactional(readOnly = true)
    public Page<Transaction> list(UUID walletId, Instant from, Instant to, Pageable pageable) {
        if (!walletRepository.existsById(walletId)) {
            throw new WalletNotFoundException(walletId);
        }
        return transactionRepository.search(walletId, from, to, pageable);
    }
}
