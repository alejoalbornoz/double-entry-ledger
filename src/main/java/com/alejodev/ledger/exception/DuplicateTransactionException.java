package com.alejodev.ledger.exception;

public class DuplicateTransactionException extends RuntimeException {
    public DuplicateTransactionException(String idempotencyKey) {
        super("Transaction already processed with idempotency key: " + idempotencyKey);
    }
}