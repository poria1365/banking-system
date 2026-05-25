package com.banking.adapter.in.web;

import com.banking.domain.exception.AccountNotFoundException;
import com.banking.domain.exception.InsufficientFundsException;
import com.banking.domain.exception.TransactionNotFoundException;
import com.banking.domain.model.*;
import com.banking.domain.port.in.GetTransactionUseCase;
import com.banking.domain.port.in.TransferMoneyUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock private TransferMoneyUseCase transferMoneyUseCase;
    @Mock private GetTransactionUseCase getTransactionUseCase;

    @InjectMocks
    private TransactionController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID fromId;
    private UUID toId;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
            .standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .setValidator(validator)
            .build();

        fromId = UUID.randomUUID();
        toId = UUID.randomUUID();
    }

    // ─── POST /transactions/transfer ───────────────────────────────────────────

    @Test
    void transfer_completedTransaction_returns201() throws Exception {
        Transaction txn = completedTransfer(fromId, toId);
        when(transferMoneyUseCase.transfer(any())).thenReturn(txn);

        mockMvc.perform(post("/api/v1/transactions/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validTransferBody(fromId, toId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.amount").value(250.00))
            .andExpect(jsonPath("$.data.currency").value("USD"));
    }

    @Test
    void transfer_insufficientFunds_returns422() throws Exception {
        when(transferMoneyUseCase.transfer(any()))
            .thenThrow(new InsufficientFundsException(fromId,
                Money.of("50.00", "USD"), Money.of("250.00", "USD")));

        mockMvc.perform(post("/api/v1/transactions/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validTransferBody(fromId, toId)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value(containsString("Insufficient")));
    }

    @Test
    void transfer_accountNotFound_returns404() throws Exception {
        when(transferMoneyUseCase.transfer(any()))
            .thenThrow(new AccountNotFoundException(fromId));

        mockMvc.perform(post("/api/v1/transactions/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validTransferBody(fromId, toId)))
            .andExpect(status().isNotFound());
    }

    @Test
    void transfer_missingIdempotencyKey_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "fromAccountId", fromId,
            "toAccountId", toId,
            "amount", 100.00,
            "currency", "USD"
        ));

        mockMvc.perform(post("/api/v1/transactions/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(transferMoneyUseCase);
    }

    @Test
    void transfer_zeroAmount_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "idempotencyKey", "key-1",
            "fromAccountId", fromId,
            "toAccountId", toId,
            "amount", 0.00,
            "currency", "USD"
        ));

        mockMvc.perform(post("/api/v1/transactions/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(transferMoneyUseCase);
    }

    @Test
    void transfer_missingFromAccountId_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
            "idempotencyKey", "key-1",
            "toAccountId", toId,
            "amount", 100.00,
            "currency", "USD"
        ));

        mockMvc.perform(post("/api/v1/transactions/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_pendingTransaction_returns202() throws Exception {
        Transaction txn = pendingTransfer(fromId, toId);
        when(transferMoneyUseCase.transfer(any())).thenReturn(txn);

        mockMvc.perform(post("/api/v1/transactions/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validTransferBody(fromId, toId)))
            .andExpect(status().isAccepted());
    }

    // ─── GET /transactions/{id} ────────────────────────────────────────────────

    @Test
    void getTransaction_existingId_returns200() throws Exception {
        Transaction txn = completedTransfer(fromId, toId);
        when(getTransactionUseCase.getTransaction(txn.getId())).thenReturn(txn);

        mockMvc.perform(get("/api/v1/transactions/{id}", txn.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("COMPLETED"))
            .andExpect(jsonPath("$.data.idempotencyKey").value("test-key"));
    }

    @Test
    void getTransaction_unknownId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(getTransactionUseCase.getTransaction(id))
            .thenThrow(new TransactionNotFoundException(id));

        mockMvc.perform(get("/api/v1/transactions/{id}", id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false));
    }

    // ─── GET /transactions/account/{accountId} ─────────────────────────────────

    @Test
    void getTransactionsByAccount_returnsListSortedNewestFirst() throws Exception {
        Transaction t1 = completedTransfer(fromId, toId);
        Transaction t2 = pendingTransfer(toId, fromId);
        when(getTransactionUseCase.getTransactionsByAccount(fromId)).thenReturn(List.of(t1, t2));

        mockMvc.perform(get("/api/v1/transactions/account/{accountId}", fromId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    void getTransactionsByAccount_emptyHistory_returnsEmptyList() throws Exception {
        when(getTransactionUseCase.getTransactionsByAccount(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/transactions/account/{id}", UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private String validTransferBody(UUID from, UUID to) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
            "idempotencyKey", "test-key",
            "fromAccountId", from,
            "toAccountId", to,
            "amount", 250.00,
            "currency", "USD"
        ));
    }

    private Transaction completedTransfer(UUID from, UUID to) {
        Transaction t = Transaction.createTransfer("test-key", from, to, Money.of("250.00", "USD"));
        t.markProcessing();
        t.markCompleted();
        return t;
    }

    private Transaction pendingTransfer(UUID from, UUID to) {
        return Transaction.createTransfer("test-key", from, to, Money.of("250.00", "USD"));
    }
}
