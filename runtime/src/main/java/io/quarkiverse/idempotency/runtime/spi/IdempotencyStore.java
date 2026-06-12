package io.quarkiverse.idempotency.runtime.spi;

import java.time.Duration;

/**
 * Backing store for idempotency keys. Implementations must make {@link #acquire} atomic: exactly
 * one concurrent caller for a given key may receive {@link Reservation.Acquired}.
 *
 * <p>
 * The default implementation is in-memory (single node). A distributed implementation (e.g. Redis
 * with {@code SET key val NX PX}) can be provided as an alternative bean.
 */
public interface IdempotencyStore {

    /**
     * Atomically reserve a key for an in-flight request, or report the existing entry.
     *
     * @param key the idempotency key
     * @param fingerprint the request payload fingerprint
     * @param lockTtl how long the in-flight reservation is valid
     * @return {@link Reservation.Acquired} if reserved, otherwise {@link Reservation.Existing}
     */
    Reservation acquire(String key, String fingerprint, Duration lockTtl);

    /**
     * Persist the final response for a key, replacing the in-flight reservation.
     *
     * @param key the idempotency key
     * @param fingerprint the request payload fingerprint (preserved for mismatch detection on replay)
     * @param response the response to replay on future retries
     * @param ttl how long the response remains replayable
     */
    void complete(String key, String fingerprint, StoredResponse response, Duration ttl);

    /**
     * Release an in-flight reservation that produced no cacheable response, so the key can be
     * retried.
     *
     * @param key the idempotency key
     */
    void release(String key);
}
