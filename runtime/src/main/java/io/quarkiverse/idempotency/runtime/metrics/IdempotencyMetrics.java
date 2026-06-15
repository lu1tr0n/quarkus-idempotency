package io.quarkiverse.idempotency.runtime.metrics;

/**
 * Observability hooks for idempotency outcomes. Implemented by a no-op bean by default and by a
 * Micrometer-backed bean when {@code quarkus-micrometer} is on the classpath, so the filters can
 * record metrics without a hard dependency on (or null checks for) a {@code MeterRegistry}.
 */
public interface IdempotencyMetrics {

    /** A new key was reserved and the request proceeded (a fresh operation). */
    void onFresh();

    /** A completed response was replayed for a repeated key. */
    void onReplay();

    /** A request arrived while another with the same key was still in flight (409). */
    void onConflict();

    /** A key was reused with a different payload fingerprint (422). */
    void onMismatch();

    /** A response was captured and persisted for future replay. */
    void onStored();

    /** A store operation failed. */
    void onStoreError();
}
