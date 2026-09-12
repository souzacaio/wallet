package br.com.datum.wallet.service;

import br.com.datum.wallet.domain.Transaction;
import br.com.datum.wallet.domain.TransactionType;
import br.com.datum.wallet.domain.Wallet;
import br.com.datum.wallet.domain.exception.IdempotencyConflictException;
import br.com.datum.wallet.domain.exception.InsufficientBalanceException;
import br.com.datum.wallet.domain.exception.WalletNotFoundException;
import br.com.datum.wallet.repository.TransactionRepository;
import br.com.datum.wallet.repository.WalletRepository;
import br.com.datum.wallet.service.dto.TransactionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;

    private Wallet wallet;
    private UUID walletId;

    @BeforeEach
    void setUp() {
        wallet = Wallet.create("Alice");
        wallet.credit(new BigDecimal("100.00"));
        walletId = wallet.getId();
    }

    @Test
    void creditIncreasesBalanceAndPersistsTransaction() {
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByWalletIdAndIdempotencyKey(walletId, "key-1")).thenReturn(Optional.empty());
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> inv.getArgument(0));

        TransactionResult result = transactionService.apply(walletId, TransactionType.CREDIT, new BigDecimal("50.00"), "key-1");

        assertThat(wallet.getBalance()).isEqualByComparingTo("150.00");
        assertThat(result.transaction().getBalanceAfter()).isEqualByComparingTo("150.00");
        assertThat(result.replayed()).isFalse();
        verify(transactionRepository).save(any(Transaction.class));
    }

    @Test
    void debitBeyondBalanceIsRejectedAndNothingPersisted() {
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByWalletIdAndIdempotencyKey(walletId, "key-2")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                transactionService.apply(walletId, TransactionType.DEBIT, new BigDecimal("100.01"), "key-2"))
                .isInstanceOf(InsufficientBalanceException.class);

        assertThat(wallet.getBalance()).isEqualByComparingTo("100.00");
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void replayWithSameKeyReturnsOriginalWithoutReapplying() {
        Transaction original = Transaction.of(walletId, TransactionType.CREDIT,
                new BigDecimal("50.00"), new BigDecimal("150.00"), "key-3");
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByWalletIdAndIdempotencyKey(walletId, "key-3")).thenReturn(Optional.of(original));

        TransactionResult result = transactionService.apply(walletId, TransactionType.CREDIT, new BigDecimal("50.00"), "key-3");

        assertThat(result.transaction()).isSameAs(original);
        assertThat(result.replayed()).isTrue();
        assertThat(wallet.getBalance()).isEqualByComparingTo("100.00");
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void reusingKeyForDifferentPayloadIsConflict() {
        Transaction original = Transaction.of(walletId, TransactionType.CREDIT,
                new BigDecimal("50.00"), new BigDecimal("150.00"), "key-4");
        when(walletRepository.findByIdForUpdate(walletId)).thenReturn(Optional.of(wallet));
        when(transactionRepository.findByWalletIdAndIdempotencyKey(walletId, "key-4")).thenReturn(Optional.of(original));

        assertThatThrownBy(() ->
                transactionService.apply(walletId, TransactionType.DEBIT, new BigDecimal("50.00"), "key-4"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void applyingToUnknownWalletThrowsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(walletRepository.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                transactionService.apply(unknown, TransactionType.CREDIT, new BigDecimal("10.00"), "key-5"))
                .isInstanceOf(WalletNotFoundException.class);
    }
}
