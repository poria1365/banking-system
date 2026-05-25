package com.banking.adapter.in.web;

import com.banking.adapter.in.web.dto.AccountResponse;
import com.banking.adapter.in.web.dto.ApiResponse;
import com.banking.adapter.in.web.dto.CreateAccountRequest;
import com.banking.domain.model.Account;
import com.banking.domain.model.Money;
import com.banking.domain.port.in.CreateAccountUseCase;
import com.banking.domain.port.in.GetAccountUseCase;
import com.banking.domain.port.in.command.CreateAccountCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

// Inbound web adapter for account operations.
// Translates HTTP requests into use-case commands — no business logic lives here.
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final CreateAccountUseCase createAccountUseCase;
    private final GetAccountUseCase getAccountUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {

        Account account = createAccountUseCase.createAccount(new CreateAccountCommand(
            request.ownerId(),
            Money.of(request.initialBalance(), request.currency())
        ));

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(AccountResponse.from(account)));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccount(
            @PathVariable UUID accountId) {

        Account account = getAccountUseCase.getAccount(accountId);
        return ResponseEntity.ok(ApiResponse.ok(AccountResponse.from(account)));
    }
}
