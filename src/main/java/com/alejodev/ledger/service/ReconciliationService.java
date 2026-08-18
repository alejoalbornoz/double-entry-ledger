package com.alejodev.ledger.service;

import com.alejodev.ledger.model.Account;
import com.alejodev.ledger.dto.response.ReconciliationResult;
import com.alejodev.ledger.repository.IAccountRepository;
import com.alejodev.ledger.repository.ILedgerEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final IAccountRepository accountRepository;
    private final ILedgerEntryRepository ledgerEntryRepository;

    public ReconciliationService(IAccountRepository accountRepository,
                                 ILedgerEntryRepository ledgerEntryRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    /**
     * Recalculates each account's balance from its ledger entries and compares
     * it against the cached balance field. Read-only — never corrects mismatches
     * automatically, because auto-"fixing" a financial discrepancy without human
     * review is exactly the kind of thing that turns a bug into a cover-up.
     */
    @Transactional(readOnly = true)
    public ReconciliationResult reconcileAll() {
        List<Account> accounts = accountRepository.findAll();
        List<ReconciliationResult.Discrepancy> discrepancies = new ArrayList<>();

        for (Account account : accounts) {
            BigDecimal computedBalance = ledgerEntryRepository.calculateBalanceForAccount(account.getId());
            BigDecimal cachedBalance = account.getBalance();

            if (cachedBalance.compareTo(computedBalance) != 0) {
                log.error("Balance mismatch detected for account {}: cached={}, computed={}",
                        account.getId(), cachedBalance, computedBalance);
                discrepancies.add(new ReconciliationResult.Discrepancy(
                        account.getId(), cachedBalance, computedBalance
                ));
            }
        }

        if (discrepancies.isEmpty()) {
            log.info("Reconciliation completed: {} accounts checked, no discrepancies", accounts.size());
        } else {
            log.error("Reconciliation completed: {} accounts checked, {} discrepancies found",
                    accounts.size(), discrepancies.size());
        }

        return new ReconciliationResult(accounts.size(), discrepancies.size(), discrepancies);
    }
}