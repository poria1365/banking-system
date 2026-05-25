package com.banking.adapter.out.persistence;

import com.banking.adapter.out.persistence.entity.AccountEntity;
import com.banking.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaTransactionRepositoryAdapter.class)
@ActiveProfiles("test")
class JpaTransactionRepositoryAdapterTest {

    @Autowired
    private JpaTransactionRepositoryAdapter adapter;

    @Autowired
    private TestEntityManager entityManager;

    private UUID fromAccountId;
    private UUID toAccountId;

    @BeforeEach
    void setUp() {
        fromAccountId = persistAccount("owner-from");
        toAccountId   = persistAccount("owner-to");
    }

    // ─── save ─────────────────────────────────────────────────────────────────

    @Test
    void save_persistsNewTransaction() {
        Transaction tx = newTransfer("key-1");

        Transaction saved = adapter.save(tx);

        assertThat(saved.getId()).isEqualTo(tx.getId());
        assertThat(saved.getIdempotencyKey()).isEqualTo("key-1");
        assertThat(saved.getFromAccountId()).isEqualTo(fromAccountId);
        assertThat(saved.getToAccountId()).isEqualTo(toAccountId);
        assertThat(saved.getAmount().getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.PENDING);
    }

    @Test
    void save_updatesExistingTransaction_statusChange() {
        Transaction tx = newTransfer("key-update");
        adapter.save(tx);

        tx.markCompleted();
        Transaction updated = adapter.save(tx);

        assertThat(updated.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(updated.getCompletedAt()).isNotNull();
    }

    @Test
    void save_duplicateIdempotencyKey_returnsExistingTransaction() {
        Transaction first = newTransfer("key-dup");
        Transaction saved = adapter.save(first);

        // Simulate a concurrent request with the same idempotency key
        Transaction duplicate = Transaction.createTransfer(
            "key-dup", fromAccountId, toAccountId, money("100.00", "USD"));
        Transaction result = adapter.save(duplicate);

        // Should return the original, not throw
        assertThat(result.getId()).isEqualTo(saved.getId());
        assertThat(result.getIdempotencyKey()).isEqualTo("key-dup");
    }

    @Test
    void save_persistsFailureReason() {
        Transaction tx = newTransfer("key-fail");
        adapter.save(tx);
        tx.markFailed("Insufficient funds");
        Transaction failed = adapter.save(tx);

        assertThat(failed.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("Insufficient funds");
    }

    // ─── findById ─────────────────────────────────────────────────────────────

    @Test
    void findById_returnsEmpty_whenNotExists() {
        assertThat(adapter.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findById_returnsTransaction_whenExists() {
        Transaction tx = newTransfer("key-find");
        adapter.save(tx);

        Optional<Transaction> found = adapter.findById(tx.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getIdempotencyKey()).isEqualTo("key-find");
    }

    // ─── findByIdempotencyKey ─────────────────────────────────────────────────

    @Test
    void findByIdempotencyKey_returnsEmpty_whenNotExists() {
        assertThat(adapter.findByIdempotencyKey("nonexistent")).isEmpty();
    }

    @Test
    void findByIdempotencyKey_returnsTransaction_whenExists() {
        adapter.save(newTransfer("key-idem"));

        Optional<Transaction> found = adapter.findByIdempotencyKey("key-idem");

        assertThat(found).isPresent();
        assertThat(found.get().getFromAccountId()).isEqualTo(fromAccountId);
    }

    // ─── findByFromAccountId / findByToAccountId ──────────────────────────────

    @Test
    void findByFromAccountId_returnsOnlyTransactionsFromThatAccount() {
        adapter.save(newTransfer("key-from-1"));
        adapter.save(newTransfer("key-from-2"));

        List<Transaction> results = adapter.findByFromAccountId(fromAccountId);

        assertThat(results).hasSize(2);
        assertThat(results).allMatch(t -> t.getFromAccountId().equals(fromAccountId));
    }

    @Test
    void findByToAccountId_returnsOnlyTransactionsToThatAccount() {
        adapter.save(newTransfer("key-to-1"));

        List<Transaction> results = adapter.findByToAccountId(toAccountId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getToAccountId()).isEqualTo(toAccountId);
    }

    @Test
    void findByFromAccountId_returnsEmpty_whenNoTransactions() {
        List<Transaction> results = adapter.findByFromAccountId(UUID.randomUUID());

        assertThat(results).isEmpty();
    }

    @Test
    void findByFromAccountId_returnsNewestFirst() throws InterruptedException {
        adapter.save(newTransfer("key-order-1"));
        Thread.sleep(5); // ensure distinct createdAt
        adapter.save(newTransfer("key-order-2"));

        List<Transaction> results = adapter.findByFromAccountId(fromAccountId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getCreatedAt())
            .isAfterOrEqualTo(results.get(1).getCreatedAt());
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Transaction newTransfer(String idempotencyKey) {
        return Transaction.createTransfer(
            idempotencyKey, fromAccountId, toAccountId, money("100.00", "USD"));
    }

    private Money money(String amount, String currency) {
        return Money.of(new BigDecimal(amount), currency);
    }

    private UUID persistAccount(String ownerId) {
        AccountEntity entity = new AccountEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerId(ownerId);
        entity.setBalance(new BigDecimal("10000.00"));
        entity.setCurrency("USD");
        entity.setStatus(AccountStatus.ACTIVE);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        entity.setVersion(0L);
        entityManager.persist(entity);
        entityManager.flush();
        return entity.getId();
    }
}
