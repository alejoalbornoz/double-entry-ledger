package com.alejodev.ledger.service;

import com.alejodev.ledger.dto.request.TransferRequest;
import com.alejodev.ledger.exception.AccountNotFoundException;
import com.alejodev.ledger.exception.DuplicateTransactionException;
import com.alejodev.ledger.exception.InsufficientFundsException;
import com.alejodev.ledger.exception.InvalidTransferException;
import com.alejodev.ledger.model.Account;
import com.alejodev.ledger.model.LedgerEntry;
import com.alejodev.ledger.model.Transaction;
import com.alejodev.ledger.model.enums.AccountStatus;
import com.alejodev.ledger.model.enums.EntryType;
import com.alejodev.ledger.model.enums.TransactionStatus;
import com.alejodev.ledger.repository.IAccountRepository;
import com.alejodev.ledger.repository.ILedgerEntryRepository;
import com.alejodev.ledger.repository.ITransactionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TransferService {

    private final IAccountRepository accountRepository;
    private final ITransactionRepository transactionRepository;
    private final ILedgerEntryRepository ledgerEntryRepository;

    public TransferService(IAccountRepository accountRepository,
                           ITransactionRepository transactionRepository,
                           ILedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    /**
     * Ejecuta una transferencia entre dos cuentas de forma atómica.
     * Genera exactamente 2 LedgerEntry: un DEBIT en la cuenta origen
     * y un CREDIT en la cuenta destino, por el mismo monto.
     *
     * Invariante de doble entrada: sum(DEBIT) == sum(CREDIT) para esta Transaction.
     */
    @Transactional
    public Transaction transfer(TransferRequest request, String idempotencyKey) {

        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new InvalidTransferException("Cannot transfer to the same account");
        }

        // Orden determinístico de locking para evitar deadlocks:
        // si dos transfers cruzados (A->B y B->A) piden locks en orden distinto,
        // se pueden deadlockear entre sí. Bloqueando siempre por UUID ascendente,
        // los dos threads piden los locks en el mismo orden.
        UUID firstId = request.fromAccountId().compareTo(request.toAccountId()) < 0
                ? request.fromAccountId() : request.toAccountId();
        UUID secondId = firstId.equals(request.fromAccountId())
                ? request.toAccountId() : request.fromAccountId();

        Account first = accountRepository.findById(firstId)
                .orElseThrow(() -> new AccountNotFoundException(firstId));
        Account second = accountRepository.findById(secondId)
                .orElseThrow(() -> new AccountNotFoundException(secondId));

        Account fromAccount = first.getId().equals(request.fromAccountId()) ? first : second;
        Account toAccount = first.getId().equals(request.toAccountId()) ? first : second;

        if (fromAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTransferException("Source account is not active: " + fromAccount.getId());
        }
        if (toAccount.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTransferException("Destination account is not active: " + toAccount.getId());
        }

        BigDecimal amount = request.amount();

        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(fromAccount.getId(), amount, fromAccount.getBalance());
        }


        // 1. Crear la Transaction en estado PENDING
        Transaction transaction = new Transaction(idempotencyKey, "TRANSFER", amount, request.description());
        try {
            transaction = transactionRepository.save(transaction);
        } catch (DataIntegrityViolationException e) {
            // Otro request concurrente con la misma idempotency key ya ganó la carrera
            // e insertó su Transaction primero. No es un error real — es un retry legítimo.
            throw new DuplicateTransactionException(idempotencyKey);
        }
        transaction = transactionRepository.save(transaction);

        // 2. Debitar cuenta origen
        BigDecimal newFromBalance = fromAccount.getBalance().subtract(amount);
        fromAccount.setBalance(newFromBalance);
        accountRepository.save(fromAccount);

        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(fromAccount)
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .balanceAfter(newFromBalance)
                .build();
        ledgerEntryRepository.save(debitEntry);

        // 3. Acreditar cuenta destino
        BigDecimal newToBalance = toAccount.getBalance().add(amount);
        toAccount.setBalance(newToBalance);
        accountRepository.save(toAccount);

        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(toAccount)
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .balanceAfter(newToBalance)
                .build();
        ledgerEntryRepository.save(creditEntry);

        // 4. Marcar transacción como completada
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction = transactionRepository.save(transaction);

        return transaction;
    }
}