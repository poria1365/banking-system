package com.banking.application.saga;

import com.banking.domain.event.*;
import com.banking.domain.exception.AccountInactiveException;
import com.banking.domain.model.*;
import com.banking.domain.port.out.AccountRepository;
import com.banking.domain.port.out.EventPublisher;
import com.banking.domain.port.out.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferSagaOrchestratorTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private EventPublisher eventPublisher;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks
    private TransferSagaOrchestrator orchestrator;

    private Account source;
    private Account target;
    private Transaction transaction;
    private Money transferAmount;

    @BeforeEach
    void setUp() {
        source = Account.create("alice", Money.of("1000.00", "USD"));
        target = Account.create("bob", Money.of("0.00", "USD"));
        transferAmount = Money.of("400.00", "USD");
        transaction = Transaction.createTransfer("key-1", source.getId(), target.getId(), transferAmount);

        when(accountRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Make the template actually run the callback so balance changes take effect in tests
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            org.springframework.transaction.support.TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });
    }

    // ─── Happy Path ────────────────────────────────────────────────────────────

    @Test
    void execute_happyPath_debitsSourceAndCreditsTarget() {
        orchestrator.execute(transaction, source, target, transferAmount);

        assertThat(source.getBalance()).isEqualTo(Money.of("600.00", "USD"));
        assertThat(target.getBalance()).isEqualTo(Money.of("400.00", "USD"));
    }

    @Test
    void execute_happyPath_transactionMarkedCompleted() {
        orchestrator.execute(transaction, source, target, transferAmount);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transaction.getCompletedAt()).isNotNull();
    }

    @Test
    void execute_happyPath_publishesEventsInCorrectOrder() {
        orchestrator.execute(transaction, source, target, transferAmount);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, times(4)).publish(captor.capture());

        List<DomainEvent> events = captor.getAllValues();
        assertThat(events.get(0)).isInstanceOf(TransferInitiatedEvent.class);
        // Debit/credit events published AFTER atomic commit, before Phase 3
        assertThat(events.get(1)).isInstanceOf(AccountDebitedEvent.class);
        assertThat(events.get(2)).isInstanceOf(AccountCreditedEvent.class);
        assertThat(events.get(3)).isInstanceOf(TransferCompletedEvent.class);
    }

    @Test
    void execute_happyPath_savesBothAccountsAndTransaction() {
        orchestrator.execute(transaction, source, target, transferAmount);

        // PROCESSING + COMPLETED
        verify(transactionRepository, times(2)).save(transaction);
        // debit save + credit save, each once inside the atomic template
        verify(accountRepository, times(1)).save(source);
        verify(accountRepository, times(1)).save(target);
    }

    @Test
    void execute_happyPath_debitedEventContainsCorrectData() {
        orchestrator.execute(transaction, source, target, transferAmount);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publish(captor.capture());

        AccountDebitedEvent debitedEvent = captor.getAllValues().stream()
            .filter(e -> e instanceof AccountDebitedEvent)
            .map(e -> (AccountDebitedEvent) e)
            .findFirst().orElseThrow();

        assertThat(debitedEvent.getAccountId()).isEqualTo(source.getId());
        assertThat(debitedEvent.getAmount()).isEqualTo(transferAmount);
        assertThat(debitedEvent.getTransactionId()).isEqualTo(transaction.getId());
    }

    // ─── Business Failure in Atomic Block ─────────────────────────────────────

    @Test
    void execute_insufficientFunds_transactionMarkedFailed() {
        Money tooMuch = Money.of("9999.00", "USD");
        Transaction t = Transaction.createTransfer("key-2", source.getId(), target.getId(), tooMuch);

        orchestrator.execute(t, source, target, tooMuch);

        assertThat(t.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(t.getFailureReason()).isNotBlank();
    }

    @Test
    void execute_insufficientFunds_accountBalancesUnchanged() {
        Money tooMuch = Money.of("9999.00", "USD");
        Transaction t = Transaction.createTransfer("key-2", source.getId(), target.getId(), tooMuch);

        // Debit throws before any save; template would roll back even if save had happened
        orchestrator.execute(t, source, target, tooMuch);

        assertThat(source.getBalance()).isEqualTo(Money.of("1000.00", "USD"));
        assertThat(target.getBalance()).isEqualTo(Money.of("0.00", "USD"));
    }

    @Test
    void execute_insufficientFunds_publishesTransferFailed_noDebitOrCreditEvents() {
        Money tooMuch = Money.of("9999.00", "USD");
        Transaction t = Transaction.createTransfer("key-2", source.getId(), target.getId(), tooMuch);

        orchestrator.execute(t, source, target, tooMuch);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publish(captor.capture());

        assertThat(captor.getAllValues()).noneMatch(e -> e instanceof AccountDebitedEvent);
        assertThat(captor.getAllValues()).noneMatch(e -> e instanceof AccountCreditedEvent);
        assertThat(captor.getAllValues()).anyMatch(e -> e instanceof TransferFailedEvent);
    }

    @Test
    void execute_creditFails_transactionMarkedFailed() {
        // Target frozen — credit throws AccountInactiveException (a DomainException).
        // DB rolls back the debit automatically; no application compensation needed.
        Account frozenTarget = buildFrozenAccount("carol");
        Transaction t = Transaction.createTransfer("key-3", source.getId(), frozenTarget.getId(), transferAmount);

        orchestrator.execute(t, source, frozenTarget, transferAmount);

        assertThat(t.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(t.getFailureReason()).isNotBlank();
    }

    @Test
    void execute_creditFails_noDebitReversalAttempted() {
        // With an atomic template, the DB rollback undoes the debit — no explicit compensation.
        Account frozenTarget = buildFrozenAccount("carol");
        Transaction t = Transaction.createTransfer("key-3", source.getId(), frozenTarget.getId(), transferAmount);

        orchestrator.execute(t, source, frozenTarget, transferAmount);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publish(captor.capture());

        assertThat(captor.getAllValues()).noneMatch(e -> e instanceof DebitReversedEvent);
        assertThat(captor.getAllValues()).noneMatch(e -> e instanceof AccountDebitedEvent);
        assertThat(captor.getAllValues()).anyMatch(e -> e instanceof TransferFailedEvent);
    }

    @Test
    void execute_transientException_fromAtomicBlock_propagates() {
        // A non-DomainException (e.g. DB timeout) must bubble up so @Retry can re-attempt.
        RuntimeException dbTimeout = new RuntimeException("Connection reset");
        when(transactionTemplate.execute(any())).thenThrow(dbTimeout);

        assertThatThrownBy(() -> orchestrator.execute(transaction, source, target, transferAmount))
            .isSameAs(dbTimeout);
    }

    // ─── Status Save Fails After Successful Transfer (Scenario C) ─────────────

    @Test
    void execute_statusSaveFails_afterBothAccountsUpdated_marksNeedsManualReview() {
        // PROCESSING save succeeds; COMPLETED save throws; NEEDS_MANUAL_REVIEW save succeeds
        when(transactionRepository.save(any()))
            .thenAnswer(inv -> inv.getArgument(0))
            .thenThrow(new RuntimeException("DB timeout saving COMPLETED status"))
            .thenAnswer(inv -> inv.getArgument(0));

        orchestrator.execute(transaction, source, target, transferAmount);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.NEEDS_MANUAL_REVIEW);
        assertThat(transaction.getFailureReason()).contains("status record failed");
    }

    @Test
    void execute_statusSaveFails_accountBalancesAreCorrect() {
        when(transactionRepository.save(any()))
            .thenAnswer(inv -> inv.getArgument(0))
            .thenThrow(new RuntimeException("DB timeout"))
            .thenAnswer(inv -> inv.getArgument(0));

        orchestrator.execute(transaction, source, target, transferAmount);

        // Money moved correctly — the atomic block committed before Phase 3 failed
        assertThat(source.getBalance()).isEqualTo(Money.of("600.00", "USD"));
        assertThat(target.getBalance()).isEqualTo(Money.of("400.00", "USD"));

        // No extra account saves after the status failure — accounts are not touched again
        verify(accountRepository, times(1)).save(source);
        verify(accountRepository, times(1)).save(target);
    }

    @Test
    void execute_statusSaveFails_doesNotPublishDebitReversedOrCompleted() {
        when(transactionRepository.save(any()))
            .thenAnswer(inv -> inv.getArgument(0))
            .thenThrow(new RuntimeException("DB timeout"))
            .thenAnswer(inv -> inv.getArgument(0));

        orchestrator.execute(transaction, source, target, transferAmount);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publish(captor.capture());

        assertThat(captor.getAllValues()).noneMatch(e -> e instanceof DebitReversedEvent);
        assertThat(captor.getAllValues()).noneMatch(e -> e instanceof TransferCompletedEvent);
        assertThat(captor.getAllValues()).anyMatch(e -> e instanceof TransferFailedEvent);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private Account buildFrozenAccount(String owner) {
        Account acc = Account.create(owner, Money.of("500.00", "USD"));
        acc.freeze();
        return acc;
    }
}
