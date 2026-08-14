package com.alejodev.ledger.service;

import com.alejodev.ledger.model.Account;
import com.alejodev.ledger.dto.request.TransferRequest;
import com.alejodev.ledger.repository.IAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TransferConcurrencyTest {

    @Autowired
    private TransferRetryService transferRetryService;

    @Autowired
    private IAccountRepository accountRepository;

    @Test
    void concurrentTransfersFromSameAccount_shouldNotCorruptBalance() throws InterruptedException {
        // Cuenta origen con fondos, y 10 cuentas destino distintas
        Account source = accountRepository.save(new Account("Source"));
        source.setBalance(new BigDecimal("1000.00"));
        source = accountRepository.save(source);
        final UUID sourceId = source.getId();

        int threadCount = 10;
        BigDecimal amountPerTransfer = new BigDecimal("50.00");

        List<Account> destinations = accountRepository.saveAll(
                java.util.stream.IntStream.range(0, threadCount)
                        .mapToObj(i -> new Account("Destination " + i))
                        .toList()
        );

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();

        for (int i = 0; i < threadCount; i++) {
            UUID destId = destinations.get(i).getId();
            executor.submit(() -> {
                try {
                    TransferRequest request = new TransferRequest(
                            sourceId, destId, amountPerTransfer, "Concurrent test"
                    );
                    transferRetryService.transferWithRetry(request, UUID.randomUUID().toString());  // <-- cambio acá
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.out.println("Transfer failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        System.out.println("Successful transfers: " + successCount.get());
        System.out.println("Failed transfers: " + failureCount.get());

        // Lo importante: el balance final tiene que ser EXACTO,
        // sin importar cuántos threads fallaron por conflicto de versión.
        Account finalSource = accountRepository.findById(sourceId).orElseThrow();
        BigDecimal expectedBalance = new BigDecimal("1000.00")
                .subtract(amountPerTransfer.multiply(BigDecimal.valueOf(successCount.get())));

        assertThat(finalSource.getBalance()).isEqualByComparingTo(expectedBalance);
    }
}