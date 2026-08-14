package com.alejodev.ledger.service;

import com.alejodev.ledger.model.Transaction;
import com.alejodev.ledger.dto.request.TransferRequest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Service
public class TransferRetryService {

    private final TransferService transferService;

    public TransferRetryService(TransferService transferService) {
        this.transferService = transferService;
    }
    @Retryable(
            retryFor = OptimisticLockingFailureException.class,
            maxAttempts = 10,
            backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 2000)
    )
    public Transaction transferWithRetry(TransferRequest request, String idempotencyKey) {
        return transferService.transfer(request, idempotencyKey);
    }
}