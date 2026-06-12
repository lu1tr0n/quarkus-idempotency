package io.quarkiverse.idempotency.runtime.spi;

import java.util.Map;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * A captured HTTP response, persisted so a later retry with the same idempotency key can be
 * answered without re-running the operation.
 *
 * @param status the HTTP status code
 * @param headers response headers to replay (first value per name)
 * @param entity the response entity object (serialized by the framework on replay)
 * @param mediaType the response media type, or {@code null}
 */
@RegisterForReflection
public record StoredResponse(int status, Map<String, String> headers, Object entity, String mediaType) {
}
