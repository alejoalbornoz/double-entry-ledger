package com.alejodev.ledger.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "idempotency_keys")
@Getter
@EqualsAndHashCode(of = "key")
@NoArgsConstructor(force = true)
@AllArgsConstructor
@Builder
public class IdempotencyKey {

    @Id
    private String key;

    @Column(nullable = false)
    private String requestHash;

    @Lob
    @Column(nullable = false)
    private String responseBody;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = Instant.now();
    }
}