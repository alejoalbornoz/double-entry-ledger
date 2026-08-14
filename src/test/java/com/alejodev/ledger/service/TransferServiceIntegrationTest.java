package com.alejodev.ledger.service;

import com.alejodev.ledger.model.Account;
import com.alejodev.ledger.model.enums.EntryType;
import com.alejodev.ledger.model.LedgerEntry;
import com.alejodev.ledger.model.Transaction;
import com.alejodev.ledger.dto.request.TransferRequest;
import com.alejodev.ledger.repository.IAccountRepository;
import com.alejodev.ledger.repository.ILedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TransferServiceIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private IAccountRepository accountRepository;

    @Autowired
    private ILedgerEntryRepository ledgerEntryRepository;

    @Test
    void transfer_shouldMoveFundsAndCreateBalancedLedgerEntries() {
        // 1. Crear cuentas con balance inicial
        Account accountA = accountRepository.save(new Account("Test Account A"));
        accountA.setBalance(new BigDecimal("1000.00"));
        accountA = accountRepository.save(accountA);

        Account accountB = accountRepository.save(new Account("Test Account B"));
        accountB.setBalance(new BigDecimal("500.00"));
        accountB = accountRepository.save(accountB);

        BigDecimal transferAmount = new BigDecimal("300.00");
        TransferRequest request = new TransferRequest(
                accountA.getId(), accountB.getId(), transferAmount, "Integration test transfer"
        );

        // 2. Ejecutar la transferencia
        Transaction transaction = transferService.transfer(request, UUID.randomUUID().toString());

        // 3. Assert: balances finales correctos
        Account updatedA = accountRepository.findById(accountA.getId()).orElseThrow();
        Account updatedB = accountRepository.findById(accountB.getId()).orElseThrow();

        assertThat(updatedA.getBalance()).isEqualByComparingTo("700.00");
        assertThat(updatedB.getBalance()).isEqualByComparingTo("800.00");

        // 4. Assert: exactamente 2 LedgerEntry para esta transacción
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(transaction.getId());
        assertThat(entries).hasSize(2);

        // 5. Assert: invariante de doble entrada -> sum(DEBIT) == sum(CREDIT)
        BigDecimal totalDebit = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredit = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalDebit).isEqualByComparingTo(totalCredit);
        assertThat(totalDebit).isEqualByComparingTo(transferAmount);
    }
}