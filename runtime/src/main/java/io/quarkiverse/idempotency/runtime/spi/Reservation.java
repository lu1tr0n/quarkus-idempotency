package io.quarkiverse.idempotency.runtime.spi;

/**
 * Outcome of {@link IdempotencyStore#acquire(String, String, java.time.Duration)}.
 */
public sealed interface Reservation permits Reservation.Acquired, Reservation.Existing {

    /** The caller reserved the key and owns the in-flight request; it must process and complete. */
    record Acquired() implements Reservation {
    }

    /**
     * The key already existed; the caller must inspect the entry (replay / in-flight / mismatch).
     *
     * @param entry the existing entry
     */
    record Existing(StoredEntry entry) implements Reservation {
    }
}
