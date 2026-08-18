package com.alejodev.ledger.scheduler;

import com.alejodev.ledger.service.ReconciliationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationScheduler {

    private final ReconciliationService reconciliationService;

    public ReconciliationScheduler(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    // Corre cada 1 hora. En producción, el intervalo dependería del volumen
    // de transacciones y de qué tan rápido necesitás detectar un problema.
    @Scheduled(fixedRate = 3_600_000)
    public void runScheduledReconciliation() {
        reconciliationService.reconcileAll();
    }
}