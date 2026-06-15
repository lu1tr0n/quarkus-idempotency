package io.quarkiverse.idempotency.deployment;

import io.quarkiverse.idempotency.runtime.IdempotencyConfig;
import io.quarkiverse.idempotency.runtime.IdempotencyRequestFilter;
import io.quarkiverse.idempotency.runtime.IdempotencyResponseFilter;
import io.quarkiverse.idempotency.runtime.IdempotencyStartup;
import io.quarkiverse.idempotency.runtime.store.InMemoryIdempotencyStore;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ConfigMappingBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.resteasy.reactive.spi.ContainerRequestFilterBuildItem;
import io.quarkus.resteasy.reactive.spi.ContainerResponseFilterBuildItem;
import io.quarkus.vertx.http.deployment.RequireBodyHandlerBuildItem;

class IdempotencyProcessor {

    private static final String FEATURE = "quarkus-idempotency";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    ConfigMappingBuildItem config() {
        return new ConfigMappingBuildItem(IdempotencyConfig.class, "quarkus.idempotency");
    }

    /**
     * Force a Vert.x body handler on all routes so the request body is fully buffered before the
     * request filter reads it — this is what lets the filter fingerprint the body without a
     * blocking read on reactive endpoints. Gated behind {@code buffer-request-body} so the app-wide
     * buffering can be turned off when body fingerprinting is not needed.
     */
    @BuildStep
    void requireBodyHandler(IdempotencyBuildTimeConfig buildTimeConfig,
            BuildProducer<RequireBodyHandlerBuildItem> producer) {
        if (buildTimeConfig.bufferRequestBody()) {
            producer.produce(new RequireBodyHandlerBuildItem());
        }
    }

    @BuildStep
    void registerFilters(BuildProducer<ContainerRequestFilterBuildItem> requestFilters,
            BuildProducer<ContainerResponseFilterBuildItem> responseFilters) {
        requestFilters.produce(new ContainerRequestFilterBuildItem.Builder(
                IdempotencyRequestFilter.class.getName()).setRegisterAsBean(true).build());
        responseFilters.produce(new ContainerResponseFilterBuildItem.Builder(
                IdempotencyResponseFilter.class.getName()).setRegisterAsBean(true).build());
    }

    @BuildStep
    void beans(BuildProducer<AdditionalBeanBuildItem> beans) {
        AdditionalBeanBuildItem.Builder builder = AdditionalBeanBuildItem.builder()
                .addBeanClass(IdempotencyStartup.class)
                .addBeanClass(InMemoryIdempotencyStore.class)
                // Default no-op metrics; replaced by the Micrometer-backed bean when present.
                .addBeanClass("io.quarkiverse.idempotency.runtime.metrics.NoopIdempotencyMetrics");

        // Register the Redis store only when the redis client is on the classpath, by class name so
        // this processor never loads the redis-dependent class when redis is absent.
        if (isPresent("io.quarkus.redis.datasource.RedisDataSource")) {
            builder.addBeanClass("io.quarkiverse.idempotency.runtime.store.RedisIdempotencyStore");
        }

        // Register the Micrometer-backed metrics only when Micrometer is on the classpath, by class
        // name so the micrometer-dependent class is never loaded when micrometer is absent.
        if (isPresent("io.micrometer.core.instrument.MeterRegistry")) {
            builder.addBeanClass("io.quarkiverse.idempotency.runtime.metrics.MicrometerIdempotencyMetrics");
        }

        beans.produce(builder.build());
    }

    private static boolean isPresent(String className) {
        try {
            Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
