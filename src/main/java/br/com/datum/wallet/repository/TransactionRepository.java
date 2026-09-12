package br.com.datum.wallet.repository;

import br.com.datum.wallet.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByWalletIdAndIdempotencyKey(UUID walletId, String idempotencyKey);

    @Query("""
            select t from Transaction t
            where t.walletId = :walletId
              and (cast(:from as Instant) is null or t.createdAt >= :from)
              and (cast(:to as Instant) is null or t.createdAt <= :to)
            """)
    Page<Transaction> search(@Param("walletId") UUID walletId,
                             @Param("from") Instant from,
                             @Param("to") Instant to,
                             Pageable pageable);
}
