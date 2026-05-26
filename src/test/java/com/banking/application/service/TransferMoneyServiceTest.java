package com.banking.application.service;

import com.banking.application.saga.TransferSagaOrchestrator;
import com.banking.domain.exception.AccountNotFoundException;
import com.banking.domain.model.Account;
import com.banking.domain.model.Money;
import com.banking.domain.model.Transaction;
import com.banking.domain.model.TransactionStatus;
import com.banking.domain.port.in.command.TransferMoneyCommand;
import com.banking.domain.port.out.AccountRepository;
import com.banking.domain.port.out.DistributedLockPort;
import com.banking.domain.port.out.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferMoneyServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private DistributedLockPort distributedLock;
    @Mock private TransferSagaOrchestrator sagaOrchestrator;

    @InjectMocks
    private TransferMoneyService service;

    private Account source;
    private Account target;

    @BeforeEach
    void setUp() {
        source = Account.create("alice", Money.of("1000.00", "USD"));
        target = Account.create("bob", Money.of("0.00", "USD"));

        // Make lock run the action synchronously
        doAnswer(inv -> {
            Runnable action = inv.getArgument(1);
            action.run();
            return null;
        }).when(distributedLock).executeWithLocks(anyList(), any(Runnable.class));
    }

    @Test
    void transfer_happyPath_createsPendingThenInvokesSaga() {
        var command = new TransferMoneyCommand("key-1", source.getId(), target.getId(),
            Money.of("250.00", "USD"));

        Transaction pendingTxn = Transaction.createTransfer("key-1", source.getId(), target.getId(),
            Money.of("250.00", "USD"));
        Transaction completedTxn = Transaction.createTransfer("key-1", source.getId(), target.getId(),
            Money.of("250.00", "USD"));
        completedTxn.markProcessing();
        completedTxn.markCompleted();

        when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        // Pre-lock: existsById for fail-fast validation
        when(accountRepository.existsById(source.getId())).thenReturn(true);
        when(accountRepository.existsById(target.getId())).thenReturn(true);
        // Inside lock: findById for fresh account loads
        when(accountRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transactionRepository.findById(any()))
            .thenReturn(Optional.of(pendingTxn))   // inside lock: PENDING → saga runs
            .thenReturn(Optional.of(completedTxn)); // after lock: final result

        Transaction result = service.transfer(command);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        verify(sagaOrchestrator).execute(any(), eq(source), eq(target), eq(Money.of("250.00", "USD")));
    }

    @Test
    void transfer_concurrentDuplicate_transactionAlreadyTerminalInsideLock_skipsSaga() {
        var command = new TransferMoneyCommand("key-concurrent", source.getId(), target.getId(),
            Money.of("100.00", "USD"));

        Transaction alreadyCompleted = Transaction.createTransfer("key-concurrent",
            source.getId(), target.getId(), Money.of("100.00", "USD"));
        alreadyCompleted.markProcessing();
        alreadyCompleted.markCompleted();

        when(transactionRepository.findByIdempotencyKey("key-concurrent")).thenReturn(Optional.empty());
        when(accountRepository.existsById(source.getId())).thenReturn(true);
        when(accountRepository.existsById(target.getId())).thenReturn(true);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // Re-read inside lock finds COMPLETED — the concurrent duplicate already finished
        when(transactionRepository.findById(any())).thenReturn(Optional.of(alreadyCompleted));

        Transaction result = service.transfer(command);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        verifyNoInteractions(sagaOrchestrator);
    }

    @Test
    void transfer_idempotentReplay_returnsCachedResult_withoutInvokingSaga() {
        Transaction existing = Transaction.createTransfer("key-1", source.getId(), target.getId(),
            Money.of("250.00", "USD"));
        existing.markProcessing();
        existing.markCompleted();

        when(transactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        var command = new TransferMoneyCommand("key-1", source.getId(), target.getId(),
            Money.of("250.00", "USD"));

        Transaction result = service.transfer(command);

        assertThat(result).isSameAs(existing);
        verifyNoInteractions(sagaOrchestrator, distributedLock, accountRepository);
    }

    @Test
    void transfer_nonTerminalExistingTransaction_retried_sagaRuns() {
        // A PENDING transaction (e.g. from a prior interrupted attempt) is not terminal.
        // The service must fall through and run the saga rather than returning the stale state.
        Transaction pending = Transaction.createTransfer("key-retry", source.getId(), target.getId(),
            Money.of("50.00", "USD"));
        Transaction completed = Transaction.createTransfer("key-retry", source.getId(), target.getId(),
            Money.of("50.00", "USD"));
        completed.markProcessing();
        completed.markCompleted();

        when(transactionRepository.findByIdempotencyKey("key-retry")).thenReturn(Optional.of(pending));
        when(accountRepository.existsById(source.getId())).thenReturn(true);
        when(accountRepository.existsById(target.getId())).thenReturn(true);
        when(accountRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(transactionRepository.findById(any()))
            .thenReturn(Optional.of(pending))    // inside lock: still non-terminal
            .thenReturn(Optional.of(completed)); // final fetch

        service.transfer(new TransferMoneyCommand("key-retry", source.getId(), target.getId(),
            Money.of("50.00", "USD")));

        verify(sagaOrchestrator).execute(eq(pending), any(), any(), any());
    }

    @Test
    void transfer_sourceAccountNotFound_throwsAccountNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        var command = new TransferMoneyCommand("key-2", unknownId, target.getId(),
            Money.of("100.00", "USD"));

        when(transactionRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());
        when(accountRepository.existsById(unknownId)).thenReturn(false);

        assertThatExceptionOfType(AccountNotFoundException.class)
            .isThrownBy(() -> service.transfer(command))
            .withMessageContaining(unknownId.toString());

        verifyNoInteractions(sagaOrchestrator);
    }

    @Test
    void transfer_targetAccountNotFound_throwsAccountNotFoundException() {
        UUID unknownId = UUID.randomUUID();
        var command = new TransferMoneyCommand("key-3", source.getId(), unknownId,
            Money.of("100.00", "USD"));

        when(transactionRepository.findByIdempotencyKey("key-3")).thenReturn(Optional.empty());
        when(accountRepository.existsById(source.getId())).thenReturn(true);
        when(accountRepository.existsById(unknownId)).thenReturn(false);

        assertThatExceptionOfType(AccountNotFoundException.class)
            .isThrownBy(() -> service.transfer(command))
            .withMessageContaining(unknownId.toString());

        verifyNoInteractions(sagaOrchestrator);
    }

    @Test
    void transfer_distributedLocksAcquiredWithSortedAccountIds() {
        var command = new TransferMoneyCommand("key-4", source.getId(), target.getId(),
            Money.of("50.00", "USD"));

        when(transactionRepository.findByIdempotencyKey("key-4")).thenReturn(Optional.empty());
        when(accountRepository.existsById(source.getId())).thenReturn(true);
        when(accountRepository.existsById(target.getId())).thenReturn(true);
        when(accountRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Transaction dummy = Transaction.createTransfer("key-4", source.getId(), target.getId(), Money.of("50.00", "USD"));
        when(transactionRepository.findById(any())).thenReturn(Optional.of(dummy));

        service.transfer(command);

        verify(distributedLock).executeWithLocks(argThat(ids -> {
            List<UUID> sorted = List.of(source.getId(), target.getId()).stream().sorted().toList();
            return ids.equals(sorted);
        }), any(Runnable.class));
    }

    @Test
    void transfer_transactionSavedBeforeLocksAcquired() {
        var command = new TransferMoneyCommand("key-5", source.getId(), target.getId(),
            Money.of("75.00", "USD"));

        when(transactionRepository.findByIdempotencyKey("key-5")).thenReturn(Optional.empty());
        when(accountRepository.existsById(source.getId())).thenReturn(true);
        when(accountRepository.existsById(target.getId())).thenReturn(true);
        when(accountRepository.findById(source.getId())).thenReturn(Optional.of(source));
        when(accountRepository.findById(target.getId())).thenReturn(Optional.of(target));

        Transaction dummy = Transaction.createTransfer("key-5", source.getId(), target.getId(), Money.of("75.00", "USD"));
        when(transactionRepository.findById(any())).thenReturn(Optional.of(dummy));

        var saveOrder = new java.util.concurrent.atomic.AtomicBoolean(false);
        doAnswer(inv -> {
            saveOrder.set(true);
            return inv.getArgument(0);
        }).when(transactionRepository).save(any());

        doAnswer(inv -> {
            assertThat(saveOrder.get()).as("transaction must be saved before locks acquired").isTrue();
            Runnable action = inv.getArgument(1);
            action.run();
            return null;
        }).when(distributedLock).executeWithLocks(anyList(), any(Runnable.class));

        service.transfer(command);
    }
}
