package com.alejodev.ledger.service;

import com.alejodev.ledger.model.*;
import com.alejodev.ledger.exception.AccountNotFoundException;
import com.alejodev.ledger.exception.InsufficientFundsException;
import com.alejodev.ledger.exception.InvalidTransferException;
import com.alejodev.ledger.model.enums.AccountStatus;
import com.alejodev.ledger.model.enums.EntryType;
import com.alejodev.ledger.model.enums.TransactionStatus;
import com.alejodev.ledger.repository.IAccountRepository;
import com.alejodev.ledger.repository.ILedgerEntryRepository;
import com.alejodev.ledger.repository.ITransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AccountService {

    private final IAccountRepository accountRepository;
    private final ITransactionRepository transactionRepository;
    private final ILedgerEntryRepository ledgerEntryRepository;

    public AccountService(IAccountRepository accountRepository,
                          ITransactionRepository transactionRepository,
                          ILedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public Account createAccount(String ownerName) {
        return accountRepository.save(new Account(ownerName));
    }

    /**
     * Funds an account with money entering the system from outside (e.g. an
     * external bank transfer, initial funding, cash deposit). Generates a
     * single CREDIT LedgerEntry — there's no internal DEBIT counterpart
     * because the money isn't coming from another account in this ledger.
     *
     * This is the ONLY sanctioned way to increase a balance without a transfer.
     * Account.balance must never be set directly (account.setBalance(x)) outside
     * of this kind of ledger-backed operation — doing so breaks the invariant
     * that every balance is fully explained by its ledger entries.
     */
    @Transactional
    public Transaction deposit(UUID accountId, BigDecimal amount, String idempotencyKey, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Deposit amount must be positive");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTransferException("Account is not active: " + accountId);
        }

        Transaction transaction = new Transaction(idempotencyKey, "DEPOSIT", amount, description);
        transaction = transactionRepository.save(transaction);

        BigDecimal newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);
        accountRepository.save(account);

        LedgerEntry creditEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(account)
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .balanceAfter(newBalance)
                .build();
        ledgerEntryRepository.save(creditEntry);

        transaction.setStatus(TransactionStatus.COMPLETED);
        return transactionRepository.save(transaction);
    }

    /**
     * Removes money from the system (e.g. cash withdrawal, external payout).
     * Mirror of deposit(): a single DEBIT LedgerEntry, no internal CREDIT
     * counterpart.
     */
    @Transactional
    public Transaction withdraw(UUID accountId, BigDecimal amount, String idempotencyKey, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Withdrawal amount must be positive");
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTransferException("Account is not active: " + accountId);
        }

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(accountId, amount, account.getBalance());
        }

        Transaction transaction = new Transaction(idempotencyKey, "WITHDRAWAL", amount, description);
        transaction = transactionRepository.save(transaction);

        BigDecimal newBalance = account.getBalance().subtract(amount);
        account.setBalance(newBalance);
        accountRepository.save(account);

        LedgerEntry debitEntry = LedgerEntry.builder()
                .transaction(transaction)
                .account(account)
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .balanceAfter(newBalance)
                .build();
        ledgerEntryRepository.save(debitEntry);

        transaction.setStatus(TransactionStatus.COMPLETED);
        return transactionRepository.save(transaction);
    }
}