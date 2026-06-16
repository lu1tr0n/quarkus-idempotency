package io.quarkiverse.idempotency.runtime;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Build-time-resolved {@link Idempotent} policies, keyed by resource method and class. Populated once
 * at static init by the recorder (so it is fixed in native images) and read-only thereafter, so the
 * request filter can resolve an endpoint's policy with a plain map lookup — no runtime reflection on
 * annotations.
 */
public final class IdempotencyMethodRegistry {

    /** Resolved per-endpoint policy. {@code ttlMillis <= 0} means "inherit the global response TTL". */
    public record MethodPolicy(boolean enabled, Idempotent.Require requireKey, long ttlMillis) {
    }

    private static final Map<String, MethodPolicy> METHODS = new ConcurrentHashMap<>();
    private static final Map<String, MethodPolicy> CLASSES = new ConcurrentHashMap<>();

    private IdempotencyMethodRegistry() {
    }

    /** Method identity stable across build-time (Jandex) and runtime (reflection): class#name/arity. */
    static String methodId(String declaringClass, String methodName, int parameterCount) {
        return declaringClass + "#" + methodName + "/" + parameterCount;
    }

    public static void registerMethod(String declaringClass, String methodName, int parameterCount,
            MethodPolicy policy) {
        METHODS.put(methodId(declaringClass, methodName, parameterCount), policy);
    }

    public static void registerClass(String className, MethodPolicy policy) {
        CLASSES.put(className, policy);
    }

    /** True when at least one {@link Idempotent} annotation was found (lets the filter skip lookups). */
    public static boolean isEmpty() {
        return METHODS.isEmpty() && CLASSES.isEmpty();
    }

    /**
     * Resolve the effective policy for a matched resource method: the method-level annotation wins,
     * else the resource class's annotation, else {@code null} (no annotation → global behaviour).
     */
    public static MethodPolicy resolve(String declaringClass, String methodName, int parameterCount,
            String resourceClass) {
        MethodPolicy method = METHODS.get(methodId(declaringClass, methodName, parameterCount));
        if (method != null) {
            return method;
        }
        MethodPolicy onClass = CLASSES.get(resourceClass);
        if (onClass != null) {
            return onClass;
        }
        return CLASSES.get(declaringClass);
    }
}
