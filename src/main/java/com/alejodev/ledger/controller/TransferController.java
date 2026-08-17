package com.alejodev.ledger.controller;



import com.alejodev.ledger.exception.DuplicateTransactionException;
import com.alejodev.ledger.model.Transaction;
import com.alejodev.ledger.dto.request.TransferRequest;
import com.alejodev.ledger.dto.response.TransactionResponse;
import com.alejodev.ledger.repository.ITransactionRepository;
import com.alejodev.ledger.service.IdempotencyService;
import com.alejodev.ledger.service.TransferRetryService;
import com.alejodev.ledger.service.TransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferRetryService transferRetryService;
    private final ITransactionRepository transactionRepository;
    private final IdempotencyService idempotencyService;

    public TransferController(TransferRetryService transferRetryService,
                              ITransactionRepository transactionRepository,
                              IdempotencyService idempotencyService) {
        this.transferRetryService = transferRetryService;
        this.transactionRepository = transactionRepository;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey) {

        String requestHash = idempotencyService.hash(request);

        // Fast path: ya procesamos esta key con este payload exacto antes
        Optional<TransactionResponse> cached = idempotencyService.findCached(idempotencyKey, requestHash);
        if (cached.isPresent()) {
            return ResponseEntity.ok(cached.get());
        }

        TransactionResponse response;
        try {
            Transaction transaction = transferRetryService.transferWithRetry(request, idempotencyKey);
            response = toResponse(transaction);
        } catch (DuplicateTransactionException e) {
            // Perdimos la carrera al insertar el Transaction — otro request
            // concurrente con la misma key ya la completó. Recuperamos su resultado.
            Transaction existing = transactionRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> e);
            response = toResponse(existing);
        }

        idempotencyService.cache(idempotencyKey, requestHash, response);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private TransactionResponse toResponse(Transaction t) {
        return new TransactionResponse(
                t.getId(), t.getType(), t.getStatus(), t.getAmount(),
                t.getDescription(), t.getCreatedAt(), t.getCompletedAt()
        );
    }
}