package com.banking.adapter.in.web;

import com.banking.adapter.in.web.dto.ApiResponse;
import com.banking.adapter.in.web.dto.TransactionResponse;
import com.banking.adapter.in.web.dto.TransferRequest;
import com.banking.domain.model.Money;
import com.banking.domain.model.Transaction;
import com.banking.domain.model.TransactionStatus;
import com.banking.domain.port.in.GetTransactionUseCase;
import com.banking.domain.port.in.TransferMoneyUseCase;
import com.banking.domain.port.in.command.TransferMoneyCommand;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// Inbound web adapter for transaction operations.
// Transfer returns 201 if completed synchronously, 202 if still in progress.
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransferMoneyUseCase transferMoneyUseCase;
    private final GetTransactionUseCase getTransactionUseCase;

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransferRequest request) {

        Transaction transaction = transferMoneyUseCase.transfer(new TransferMoneyCommand(
            request.idempotencyKey(),
            request.fromAccountId(),
            request.toAccountId(),
            Money.of(request.amount(), request.currency())
        ));

        // 201 = transfer done; 202 = saga still running (async path)
        HttpStatus status = transaction.getStatus() == TransactionStatus.COMPLETED
            ? HttpStatus.CREATED : HttpStatus.ACCEPTED;

        return ResponseEntity.status(status)
            .body(ApiResponse.ok(TransactionResponse.from(transaction)));
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(
            @PathVariable UUID transactionId) {

        Transaction transaction = getTransactionUseCase.getTransaction(transactionId);
        return ResponseEntity.ok(ApiResponse.ok(TransactionResponse.from(transaction)));
    }

    // Returns all transactions where this account is either the sender or receiver, newest first
    @GetMapping("/account/{accountId}")
    public ResponseEntity<ApiResponse<List<TransactionResponse>>> getTransactionsByAccount(
            @PathVariable UUID accountId) {

        List<TransactionResponse> transactions = getTransactionUseCase
            .getTransactionsByAccount(accountId)
            .stream()
            .map(TransactionResponse::from)
            .toList();

        return ResponseEntity.ok(ApiResponse.ok(transactions));
    }
}
