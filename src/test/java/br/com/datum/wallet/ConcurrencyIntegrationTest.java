package br.com.datum.wallet;

import br.com.datum.wallet.domain.TransactionType;
import br.com.datum.wallet.domain.Wallet;
import br.com.datum.wallet.domain.exception.IdempotencyConflictException;
import br.com.datum.wallet.domain.exception.InsufficientBalanceException;
import br.com.datum.wallet.repository.TransactionRepository;
import br.com.datum.wallet.service.TransactionService;
import br.com.datum.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void concurrentDebitsNeverDriveBalanceNegative() throws InterruptedException {
        Wallet wallet = walletService.create("Concurrent");
        UUID walletId = wallet.getId();
        transactionService.apply(walletId, TransactionType.CREDIT, new BigDecimal("100.00"), "seed");

        int threads = 50; // each attempts to debit 10.00 -> only 10 can succeed
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final String key = "debit-" + i;
            pool.submit(() -> {
                try {
                    start.await();
                    transactionService.apply(walletId, TransactionType.DEBIT, new BigDecimal("10.00"), key);
                    succeeded.incrementAndGet();
                } catch (InsufficientBalanceException e) {
                    rejected.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(rejected.get()).isEqualTo(40);
        assertThat(walletService.getById(walletId).getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void concurrentReplaysOfSameKeyApplyExactlyOnce() throws InterruptedException {
        Wallet wallet = walletService.create("Replay");
        UUID walletId = wallet.getId();

        int threads = 30;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger conflicts = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    transactionService.apply(walletId, TransactionType.CREDIT, new BigDecimal("15.00"), "same-key");
                } catch (IdempotencyConflictException e) {
                    conflicts.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(conflicts.get()).isZero();
        // 30 concurrent replays of the same key must apply exactly once (30x15 would be 450).
        assertThat(walletService.getById(walletId).getBalance()).isEqualByComparingTo("15.00");
        assertThat(transactionRepository.findByWalletIdAndIdempotencyKey(walletId, "same-key")).isPresent();
    }
}
