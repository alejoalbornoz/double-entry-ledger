package com.alejodev.ledger.dto.response;

import com.alejodev.ledger.model.enums.AccountStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String ownerName,
        BigDecimal balance,
        AccountStatus status,
        Instant createdAt
) {
}