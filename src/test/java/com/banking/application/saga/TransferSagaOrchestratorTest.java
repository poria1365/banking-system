package com.banking.application.saga;

import com.banking.domain.event.*;
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
        assertThat(events.get(1)).isInstanceOf(AccountDebitedEvent.class);
        assertThat(events.get(2)).isInstanceOf(AccountCreditedEvent.class);
        assertThat(events.get(3)).isInstanceOf(TransferCompletedEvent.class);
    }

    @Test
    void execute_happyPath_savesBothAccountsAndTransaction() {
        orchestrator.execute(transaction, source, target, transferAmount);

        // Processing + Completed
        verify(transactionRepository, times(2)).save(transaction);
        // Source (after debit) + Target (after credit)
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

    // ─── Debit Fails (no money moved) ─────────────────────────────────────────

    @Test
    void execute_insufficientFunds_transactionMarkedFailed() {
        Money tooMuch = Money.of("9999.00", "USD");
        Transaction t = Transaction.createTransfer("key-2", source.getId(), target.getId(), tooMuch);

        orchestrator.execute(t, source, target, tooMuch);

        assertThat(t.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(t.getFailureReason()).isNotBlank();
    }

    @Test
    void execute_insufficientFunds_noCompensationNecessary() {
        Money tooMuch = Money.of("9999.00", "USD");
        Transaction t = Transaction.createTransfer("key-2", source.getId(), target.getId(), tooMuch);

        orchestrator.execute(t, source, target, tooMuch);

        // Source balance unchanged — debit never happened
        assertThat(source.getBalance()).isEqualTo(Money.of("1000.00", "USD"));
    }

    @Test
    void execute_insufficientFunds_publishesTransferFailedEvent_notDebitReversed() {
        Money tooMuch = Money.of("9999.00", "USD");
        Transaction t = Transaction.createTransfer("key-2", source.getId(), target.getId(), tooMuch);

        orchestrator.execute(t, source, target, tooMuch);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publish(captor.capture());

        assertThat(captor.getAllValues()).noneMatch(e -> e instanceof AccountDebitedEvent);
        assertThat(captor.getAllValues()).noneMatch(e -> e instanceof DebitReversedEvent);
        assertThat(captor.getAllValues()).anyMatch(e -> e instanceof TransferFailedEvent);
    }

    // ─── Credit Fails — Successful Compensation ────────────────────────────────

    @Test
    void execute_creditFails_sourceBalanceRestoredByCompensation() {
        // Target is frozen — credit will fail
        Account frozenTarget = buildFrozenAccount("carol");
        Transaction t = Transaction.createTransfer("key-3", source.getId(), frozenTarget.getId(), transferAmount);

        orchestrator.execute(t, source, frozenTarget, transferAmount);

        // Debit happened (-400) then compensation credited back (+400)
        assertThat(source.getBalance()).isEqualTo(Money.of("1000.00", "USD"));
    }

    @Test
    void execute_creditFails_transactionMarkedCompensated() {
        Account frozenTarget = buildFrozenAccount("carol");
        Transaction t = Transaction.createTransfer("key-3", source.getId(), frozenTarget.getId(), transferAmount);

        orchestrator.execute(t, source, frozenTarget, transferAmount);

        assertThat(t.getStatus()).isEqualTo(TransactionStatus.COMPENSATED);
        assertThat(t.getFailureReason()).isNotBlank();
    }

    @Test
    void execute_creditFails_publishesDebitReversedAndTransferFailed() {
        Account frozenTarget = buildFrozenAccount("carol");
        Transaction t = Transaction.createTransfer("key-3", source.getId(), frozenTarget.getId(), transferAmount);

        orchestrator.execute(t, source, frozenTarget, transferAmount);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publish(captor.capture());

        List<DomainEvent> events = captor.getAllValues();
        assertThat(events).anyMatch(e -> e instanceof DebitReversedEvent);
        assertThat(events).anyMatch(e -> e instanceof TransferFailedEvent);
        assertThat(events).noneMatch(e -> e instanceof TransferCompletedEvent);
    }

    // ─── Both Accounts Updated but Status Save Fails ──────────────────────────

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
    void execute_statusSaveFails_accountBalancesAreNotRolledBack() {
        when(transactionRepository.save(any()))
            .thenAnswer(inv -> inv.getArgument(0))
            .thenThrow(new RuntimeException("DB timeout"))
            .thenAnswer(inv -> inv.getArgument(0));

        orchestrator.execute(transaction, source, target, transferAmount);

        // Money was transferred correctly — must not be reversed
        assertThat(source.getBalance()).isEqualTo(Money.of("600.00", "USD"));
        assertThat(target.getBalance()).isEqualTo(Money.of("400.00", "USD"));

        // Each account saved exactly once (debit + credit) — no extra save for compensation
        verify(accountRepository, times(1)).save(source);
        verify(accountRepository, times(1)).save(target);
    }

    @Test
    void execute_statusSaveFails_doesNotPublishDebitReversedEvent() {
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

    // ─── Compensation Itself Fails ─────────────────────────────────────────────

    @Test
    void execute_compensationFails_transactionMarkedNeedsManualReview() {
        Account frozenTarget = buildFrozenAccount("carol");
        Transaction t = Transaction.createTransfer("key-4", source.getId(), frozenTarget.getId(), transferAmount);

        // First save of source (debit) succeeds; second save (compensation) throws
        when(accountRepository.save(any(Account.class)))
            .thenReturn(source)
            .thenThrow(new RuntimeException("DB write failed during compensation"));

        orchestrator.execute(t, source, frozenTarget, transferAmount);

        assertThat(t.getStatus()).isEqualTo(TransactionStatus.NEEDS_MANUAL_REVIEW);
        assertThat(t.getFailureReason()).contains("compensation");
    }

    @Test
    void execute_compensationFails_publishesTransferFailed_withCombinedReason() {
        Account frozenTarget = buildFrozenAccount("carol");
        Transaction t = Transaction.createTransfer("key-4", source.getId(), frozenTarget.getId(), transferAmount);

        when(accountRepository.save(any(Account.class)))
            .thenReturn(source)
            .thenThrow(new RuntimeException("DB write failed"));

        orchestrator.execute(t, source, frozenTarget, transferAmount);

        ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher, atLeastOnce()).publish(captor.capture());

        TransferFailedEvent failedEvent = captor.getAllValues().stream()
            .filter(e -> e instanceof TransferFailedEvent)
            .map(e -> (TransferFailedEvent) e)
            .findFirst().orElseThrow();

        assertThat(failedEvent.getReason()).contains("Primary:").contains("Compensation:");
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private Account buildFrozenAccount(String owner) {
        Account acc = Account.create(owner, Money.of("0.00", "USD"));
        acc.freeze();
        return acc;
    }
}
