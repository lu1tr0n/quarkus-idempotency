package io.quarkiverse.idempotency.runtime.metrics;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.arc.DefaultBean;

/**
 * Default {@link IdempotencyMetrics} used when no metrics backend is present. Every hook is a no-op,
 * so the filters carry no conditional logic. Overridden by {@code MicrometerIdempotencyMetrics} when
 * {@code quarkus-micrometer} is on the classpath.
 */
@DefaultBean
@ApplicationScoped
public class NoopIdempotencyMetrics implements IdempotencyMetrics {

    @Override
    public void onFresh() {
    }

    @Override
    public void onReplay() {
    }

    @Override
    public void onConflict() {
    }

    @Override
    public void onMismatch() {
    }

    @Override
    public void onStored() {
    }

    @Override
    public void onStoreError() {
    }
}
