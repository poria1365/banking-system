package com.banking.adapter.in.web;

import com.banking.domain.exception.AccountNotFoundException;
import com.banking.domain.model.Account;
import com.banking.domain.model.AccountStatus;
import com.banking.domain.model.Money;
import com.banking.domain.port.in.CreateAccountUseCase;
import com.banking.domain.port.in.GetAccountUseCase;
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

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock private CreateAccountUseCase createAccountUseCase;
    @Mock private GetAccountUseCase getAccountUseCase;

    @InjectMocks
    private AccountController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

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
    }

    // ─── POST /accounts ────────────────────────────────────────────────────────

    @Test
    void createAccount_validRequest_returns201WithAccountData() throws Exception {
        Account account = Account.create("alice", Money.of("500.00", "USD"));
        when(createAccountUseCase.createAccount(any())).thenReturn(account);

        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("ownerId", "alice", "initialBalance", 500.00, "currency", "USD"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.ownerId").value("alice"))
            .andExpect(jsonPath("$.data.balance").value(500.00))
            .andExpect(jsonPath("$.data.currency").value("USD"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.id").isNotEmpty());
    }

    @Test
    void createAccount_missingOwnerId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("initialBalance", 100.00, "currency", "USD"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value(containsString("Validation")));

        verifyNoInteractions(createAccountUseCase);
    }

    @Test
    void createAccount_missingCurrency_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("ownerId", "alice", "initialBalance", 100.00))))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(createAccountUseCase);
    }

    @Test
    void createAccount_negativBalance_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("ownerId", "alice", "initialBalance", -1.0, "currency", "USD"))))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(createAccountUseCase);
    }

    @Test
    void createAccount_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());
    }

    // ─── GET /accounts/{id} ────────────────────────────────────────────────────

    @Test
    void getAccount_existingId_returns200WithAccount() throws Exception {
        Account account = Account.create("bob", Money.of("1000.00", "USD"));
        when(getAccountUseCase.getAccount(account.getId())).thenReturn(account);

        mockMvc.perform(get("/api/v1/accounts/{id}", account.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.ownerId").value("bob"))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void getAccount_unknownId_returns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(getAccountUseCase.getAccount(unknownId))
            .thenThrow(new AccountNotFoundException(unknownId));

        mockMvc.perform(get("/api/v1/accounts/{id}", unknownId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value(containsString(unknownId.toString())));
    }

    @Test
    void getAccount_invalidUuidFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/not-a-valid-uuid"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createAccount_responseBodyContainsTimestamps() throws Exception {
        Account account = Account.create("dave", Money.of("0.00", "USD"));
        when(createAccountUseCase.createAccount(any())).thenReturn(account);

        mockMvc.perform(post("/api/v1/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("ownerId", "dave", "initialBalance", BigDecimal.ZERO, "currency", "USD"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
            .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());
    }
}
