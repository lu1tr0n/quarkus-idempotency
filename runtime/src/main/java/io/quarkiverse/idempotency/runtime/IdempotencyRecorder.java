package io.quarkiverse.idempotency.runtime;

import io.quarkiverse.idempotency.runtime.IdempotencyMethodRegistry.MethodPolicy;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Records the {@link Idempotent} policies discovered at build time into the
 * {@link IdempotencyMethodRegistry}. The recorded calls replay at static init (in JVM and native),
 * so no annotation is reflected on at runtime.
 */
@Recorder
public class IdempotencyRecorder {

    public void registerMethod(String declaringClass, String methodName, int parameterCount,
            boolean enabled, Idempotent.Require requireKey, long ttlMillis) {
        IdempotencyMethodRegistry.registerMethod(declaringClass, methodName, parameterCount,
                new MethodPolicy(enabled, requireKey, ttlMillis));
    }

    public void registerClass(String className, boolean enabled, Idempotent.Require requireKey, long ttlMillis) {
        IdempotencyMethodRegistry.registerClass(className,
                new MethodPolicy(enabled, requireKey, ttlMillis));
    }
}
