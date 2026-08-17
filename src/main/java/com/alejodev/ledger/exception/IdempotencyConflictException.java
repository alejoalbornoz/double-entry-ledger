package com.alejodev.ledger.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String key) {
        super("Idempotency key already used with a different request payload: " + key);
    }
}