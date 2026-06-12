package io.quarkiverse.idempotency.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.idempotency.runtime.spi.Reservation;
import io.quarkiverse.idempotency.runtime.spi.StoredEntry;
import io.quarkiverse.idempotency.runtime.spi.StoredResponse;
import io.quarkiverse.idempotency.runtime.store.InMemoryIdempotencyStore;

/** Deterministic coverage of the store contract, including the in-flight (409) and TTL paths. */
class InMemoryStoreTest {

    private static final Duration LOCK = Duration.ofSeconds(60);
    private static final Duration TTL = Duration.ofHours(24);

    @Test
    void firstAcquireWinsSecondSeesInFlight() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(1000L);
        assertInstanceOf(Reservation.Acquired.class, store.acquire("k", "fp", LOCK));

        Reservation second = store.acquire("k", "fp", LOCK);
        StoredEntry entry = assertInstanceOf(Reservation.Existing.class, second).entry();
        assertTrue(entry.inFlight(), "second concurrent caller must observe the in-flight reservation (→ 409)");
    }

    @Test
    void completeMakesEntryReplayable() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(1000L);
        store.acquire("k", "fp", LOCK);
        store.complete("k", "fp", new StoredResponse(201, Map.of("Location", "/r/1"), "ok", "text/plain"), TTL);

        StoredEntry entry = assertInstanceOf(Reservation.Existing.class, store.acquire("k", "fp", LOCK)).entry();
        assertFalse(entry.inFlight());
        assertEquals(201, entry.response().status());
        assertEquals("ok", entry.response().entity());
        assertEquals("fp", entry.fingerprint());
    }

    @Test
    void releaseAllowsReacquire() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(1000L);
        store.acquire("k", "fp", LOCK);
        store.release("k");
        assertInstanceOf(Reservation.Acquired.class, store.acquire("k", "fp", LOCK));
    }

    @Test
    void expiredReservationCanBeReacquired() throws InterruptedException {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(1000L);
        store.acquire("k", "fp", Duration.ofMillis(1));
        Thread.sleep(15);
        assertInstanceOf(Reservation.Acquired.class, store.acquire("k", "fp", LOCK));
    }
}
