package com.banking.application.service;

import com.banking.domain.exception.TransactionNotFoundException;
import com.banking.domain.model.Money;
import com.banking.domain.model.Transaction;
import com.banking.domain.port.out.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetTransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private GetTransactionService service;

    @Test
    void getTransaction_returnsTransaction_whenFound() {
        Transaction txn = makeTransfer("key-1", UUID.randomUUID(), UUID.randomUUID());
        when(transactionRepository.findById(txn.getId())).thenReturn(Optional.of(txn));

        Transaction result = service.getTransaction(txn.getId());

        assertThat(result).isEqualTo(txn);
    }

    @Test
    void getTransaction_throwsTransactionNotFoundException_whenNotFound() {
        UUID id = UUID.randomUUID();
        when(transactionRepository.findById(id)).thenReturn(Optional.empty());

        assertThatExceptionOfType(TransactionNotFoundException.class)
            .isThrownBy(() -> service.getTransaction(id))
            .withMessageContaining(id.toString());
    }

    @Test
    void getTransactionsByAccount_combinesOutgoingAndIncoming() {
        UUID accountId = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        Transaction outgoing = makeTransfer("k1", accountId, other);
        Transaction incoming = makeTransfer("k2", other, accountId);

        when(transactionRepository.findByFromAccountId(accountId)).thenReturn(List.of(outgoing));
        when(transactionRepository.findByToAccountId(accountId)).thenReturn(List.of(incoming));

        List<Transaction> result = service.getTransactionsByAccount(accountId);

        assertThat(result).hasSize(2)
            .containsExactlyInAnyOrder(outgoing, incoming);
    }

    @Test
    void getTransactionsByAccount_sortsNewestFirst() throws InterruptedException {
        UUID accountId = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        Transaction older = makeTransfer("k1", accountId, other);
        Thread.sleep(5);
        Transaction newer = makeTransfer("k2", accountId, other);

        when(transactionRepository.findByFromAccountId(accountId)).thenReturn(List.of(older, newer));
        when(transactionRepository.findByToAccountId(accountId)).thenReturn(List.of());

        List<Transaction> result = service.getTransactionsByAccount(accountId);

        assertThat(result.get(0).getIdempotencyKey()).isEqualTo("k2");
        assertThat(result.get(1).getIdempotencyKey()).isEqualTo("k1");
    }

    @Test
    void getTransactionsByAccount_returnsEmpty_whenNoTransactions() {
        UUID accountId = UUID.randomUUID();
        when(transactionRepository.findByFromAccountId(accountId)).thenReturn(List.of());
        when(transactionRepository.findByToAccountId(accountId)).thenReturn(List.of());

        List<Transaction> result = service.getTransactionsByAccount(accountId);

        assertThat(result).isEmpty();
    }

    private Transaction makeTransfer(String key, UUID from, UUID to) {
        return Transaction.createTransfer(key, from, to, Money.of("100.00", "USD"));
    }
}
