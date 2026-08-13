package com.alejodev.ledger.dto.response;

import com.alejodev.ledger.model.enums.TransactionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        String type,
        TransactionStatus status,
        BigDecimal amount,
        String description,
        Instant createdAt,
        Instant completedAt
) {
}