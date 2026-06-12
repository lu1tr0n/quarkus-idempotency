package io.quarkiverse.idempotency.runtime.store;

import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.idempotency.runtime.spi.IdempotencyStore;
import io.quarkiverse.idempotency.runtime.spi.Reservation;
import io.quarkiverse.idempotency.runtime.spi.StoredEntry;
import io.quarkiverse.idempotency.runtime.spi.StoredResponse;
import io.quarkus.arc.lookup.LookupIfProperty;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.SetArgs;
import io.quarkus.redis.datasource.value.ValueCommands;

/**
 * Distributed {@link IdempotencyStore} backed by Redis. The in-flight reservation uses an atomic
 * {@code SET NX} so exactly one node wins a given key; the loser observes the existing entry. Only
 * active when {@code quarkus.idempotency.store=redis} and {@code quarkus-redis-client} is present.
 */
@ApplicationScoped
@LookupIfProperty(name = "quarkus.idempotency.store", stringValue = "redis")
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String PREFIX = "idempotency:";

    private final ValueCommands<String, StoredEntry> values;
    private final KeyCommands<String> keys;

    @Inject
    public RedisIdempotencyStore(RedisDataSource dataSource) {
        this.values = dataSource.value(StoredEntry.class);
        this.keys = dataSource.key();
    }

    @Override
    public Reservation acquire(String key, String fingerprint, Duration lockTtl) {
        // Single atomic round-trip: SET key val NX GET PX ttl. Returns the previous value (the
        // existing entry) or null when we won the reservation. Replaces the former
        // setnx + pexpire (+ get) sequence — fewer round-trips and no set/expire race.
        // Requires Redis 7.0+ (GET option combined with NX).
        StoredEntry previous = values.setGet(PREFIX + key, new StoredEntry(fingerprint, null),
                new SetArgs().nx().px(lockTtl.toMillis()));
        return previous == null ? new Reservation.Acquired() : new Reservation.Existing(previous);
    }

    @Override
    public void complete(String key, String fingerprint, StoredResponse response, Duration ttl) {
        values.set(PREFIX + key, new StoredEntry(fingerprint, response), new SetArgs().px(ttl.toMillis()));
    }

    @Override
    public void release(String key) {
        keys.del(PREFIX + key);
    }
}
