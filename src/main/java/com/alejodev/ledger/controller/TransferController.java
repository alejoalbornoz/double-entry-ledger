package com.alejodev.ledger.controller;



import com.alejodev.ledger.model.Transaction;
import com.alejodev.ledger.dto.request.TransferRequest;
import com.alejodev.ledger.dto.response.TransactionResponse;
import com.alejodev.ledger.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        // Por ahora, si no viene el header, generamos uno random.
        // En Fase 4 esto se vuelve obligatorio y se valida contra Redis/DB.
        String key = idempotencyKey != null ? idempotencyKey : UUID.randomUUID().toString();

        Transaction transaction = transferService.transfer(request, key);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(transaction));
    }

    private TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(
                t.getId(), t.getType(), t.getStatus(), t.getAmount(),
                t.getDescription(), t.getCreatedAt(), t.getCompletedAt()
        );
    }
}