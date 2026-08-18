package com.alejodev.ledger.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class ReconciliationException extends RuntimeException {
    public ReconciliationException(UUID accountId, BigDecimal cachedBalance, BigDecimal computedBalance) {
        super(String.format(
                "Balance mismatch for account %s: cached=%s, computed from ledger=%s",
                accountId, cachedBalance, computedBalance
        ));
    }
}