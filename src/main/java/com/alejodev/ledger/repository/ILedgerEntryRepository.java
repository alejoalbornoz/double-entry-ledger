package com.alejodev.ledger.repository;

import com.alejodev.ledger.model.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface ILedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(UUID accountId);

    List<LedgerEntry> findByTransactionId(UUID transactionId);

    @Query("""
    SELECT COALESCE(SUM(CASE WHEN le.entryType = 'CREDIT' THEN le.amount ELSE -le.amount END), 0)
    FROM LedgerEntry le
    WHERE le.account.id = :accountId
    """)
    BigDecimal calculateBalanceForAccount(@Param("accountId") UUID accountId);
}
