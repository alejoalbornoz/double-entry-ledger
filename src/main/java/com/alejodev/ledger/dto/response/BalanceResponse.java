package com.alejodev.ledger.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceResponse(
        UUID accountId,
        BigDecimal balance
) {
}