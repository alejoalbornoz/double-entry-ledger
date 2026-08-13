package com.alejodev.ledger.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(UUID accountId, BigDecimal requested, BigDecimal available) {
        super(String.format("Account %s has insufficient funds. Requested: %s, Available: %s",
                accountId, requested, available));
    }
}