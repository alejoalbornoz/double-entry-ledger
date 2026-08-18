package com.alejodev.ledger.controller;

import com.alejodev.ledger.model.Account;
import com.alejodev.ledger.model.Transaction;
import com.alejodev.ledger.dto.request.CreateAccountRequest;
import com.alejodev.ledger.dto.request.DepositRequest;
import com.alejodev.ledger.dto.request.WithdrawRequest;
import com.alejodev.ledger.dto.response.AccountResponse;
import com.alejodev.ledger.dto.response.BalanceResponse;
import com.alejodev.ledger.dto.response.TransactionResponse;
import com.alejodev.ledger.exception.AccountNotFoundException;
import com.alejodev.ledger.repository.IAccountRepository;
import com.alejodev.ledger.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;
    private final IAccountRepository accountRepository;

    public AccountController(AccountService accountService, IAccountRepository accountRepository) {
        this.accountService = accountService;
        this.accountRepository = accountRepository;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.createAccount(request.ownerName());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(account));
    }

    @GetMapping("/{id}")
    public AccountResponse getAccount(@PathVariable UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        return toResponse(account);
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse getBalance(@PathVariable UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
        return new BalanceResponse(account.getId(), account.getBalance());
    }

    @PostMapping("/{id}/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @PathVariable UUID id,
            @Valid @RequestBody DepositRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey) {

        Transaction transaction = accountService.deposit(id, request.amount(), idempotencyKey, request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(transaction));
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @PathVariable UUID id,
            @Valid @RequestBody WithdrawRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey) {

        Transaction transaction = accountService.withdraw(id, request.amount(), idempotencyKey, request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(transaction));
    }

    private AccountResponse toResponse(Account a) {
        return new AccountResponse(a.getId(), a.getOwnerName(), a.getBalance(), a.getStatus(), a.getCreatedAt());
    }

    private TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(
                t.getId(), t.getType(), t.getStatus(), t.getAmount(),
                t.getDescription(), t.getCreatedAt(), t.getCompletedAt()
        );
    }
}