package com.alejodev.ledger.service;

import com.alejodev.ledger.model.IdempotencyKey;
import com.alejodev.ledger.dto.response.TransactionResponse;
import com.alejodev.ledger.exception.IdempotencyConflictException;
import com.alejodev.ledger.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class IdempotencyService {

    private static final Duration TTL = Duration.ofHours(24);

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyKeyRepository idempotencyKeyRepository, ObjectMapper objectMapper) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns a cached response if this key was already used with the exact same
     * request payload. Throws if the key was reused with a DIFFERENT payload —
     * that's not a legitimate retry, it's a client bug (or a key collision).
     */
    public Optional<TransactionResponse> findCached(String key, String requestHash) {
        return idempotencyKeyRepository.findByKey(key)
                .map(existing -> {
                    if (!existing.getRequestHash().equals(requestHash)) {
                        throw new IdempotencyConflictException(key);
                    }
                    return deserialize(existing.getResponseBody());
                });
    }

    /**
     * Caches the response for a given key. If another concurrent request already
     * cached a response for the same key first, this silently no-ops — the
     * response already stored is equally valid (same key = same payload was
     * already validated in findCached).
     */
    public void cache(String key, String requestHash, TransactionResponse response) {
        try {
            IdempotencyKey record = IdempotencyKey.builder()
                    .key(key)
                    .requestHash(requestHash)
                    .responseBody(serialize(response))
                    .expiresAt(Instant.now().plus(TTL))
                    .build();
            idempotencyKeyRepository.save(record);
        } catch (DataIntegrityViolationException e) {
            // Lost the race to cache this key — fine, someone else's cached
            // response for the same key/payload is just as valid as ours.
        }
    }

    public String hash(Object request) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(request);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(json);
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("Failed to hash idempotency request", e);
        }
    }

    private String serialize(TransactionResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize idempotent response", e);
        }
    }

    private TransactionResponse deserialize(String json) {
        try {
            return objectMapper.readValue(json, TransactionResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize cached response", e);
        }
    }
}