package com.alejodev.ledger.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ReconciliationResult(
        int accountsChecked,
        int discrepanciesFound,
        List<Discrepancy> discrepancies
) {
    public record Discrepancy(
            UUID accountId,
            BigDecimal cachedBalance,
            BigDecimal computedBalance
    ) {}
}