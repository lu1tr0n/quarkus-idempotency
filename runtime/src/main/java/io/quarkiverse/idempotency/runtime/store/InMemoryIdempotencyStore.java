package io.quarkiverse.idempotency.runtime.store;

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import io.quarkiverse.idempotency.runtime.IdempotencyConfig;
import io.quarkiverse.idempotency.runtime.spi.IdempotencyStore;
import io.quarkiverse.idempotency.runtime.spi.Reservation;
import io.quarkiverse.idempotency.runtime.spi.StoredEntry;
import io.quarkiverse.idempotency.runtime.spi.StoredResponse;
import io.quarkus.arc.lookup.LookupIfProperty;

/**
 * Single-node, in-memory {@link IdempotencyStore} backed by a bounded Caffeine cache. Entries are
 * evicted by both size ({@code max-entries}, a hard memory ceiling) and per-entry expire-after-write
 * TTL, so an unauthenticated flood of unique keys cannot exhaust the heap. Suitable for a single
 * instance or development; use the {@code redis} store for clustered deployments.
 *
 * <p>
 * Atomicity of {@link #acquire} is provided by {@code asMap().compute}, which holds the per-bin lock
 * for the duration of the remapping function (Caffeine's map view does not surface expired entries).
 */
@ApplicationScoped
@LookupIfProperty(name = "quarkus.idempotency.store", stringValue = "in-memory", lookupIfMissing = true)
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private record Holder(StoredEntry entry, long ttlNanos) {
    }

    private final Cache<String, Holder> cache;

    @Inject
    public InMemoryIdempotencyStore(IdempotencyConfig config) {
        this(config.maxEntries());
    }

    public InMemoryIdempotencyStore(long maxEntries) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(Math.max(1, maxEntries))
                .expireAfter(new Expiry<String, Holder>() {
                    @Override
                    public long expireAfterCreate(String key, Holder value, long currentTime) {
                        return value.ttlNanos();
                    }

                    @Override
                    public long expireAfterUpdate(String key, Holder value, long currentTime,
                            long currentDuration) {
                        return value.ttlNanos();
                    }

                    @Override
                    public long expireAfterRead(String key, Holder value, long currentTime,
                            long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();
    }

    @Override
    public Reservation acquire(String key, String fingerprint, Duration lockTtl) {
        boolean[] acquired = { false };
        Holder result = cache.asMap().compute(key, (k, existing) -> {
            if (existing != null) {
                return existing;
            }
            acquired[0] = true;
            return new Holder(new StoredEntry(fingerprint, null), lockTtl.toNanos());
        });
        return acquired[0]
                ? new Reservation.Acquired()
                : new Reservation.Existing(result.entry());
    }

    @Override
    public void complete(String key, String fingerprint, StoredResponse response, Duration ttl) {
        cache.put(key, new Holder(new StoredEntry(fingerprint, response), ttl.toNanos()));
    }

    @Override
    public void release(String key) {
        cache.asMap().computeIfPresent(key, (k, existing) -> existing.entry().inFlight() ? null : existing);
    }

    /** Visible for testing: run pending maintenance and report the bounded entry count. */
    long estimatedSize() {
        cache.cleanUp();
        return cache.estimatedSize();
    }
}
