package com.alejodev.ledger.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateAccountRequest(
        @NotBlank String ownerName
) {
}