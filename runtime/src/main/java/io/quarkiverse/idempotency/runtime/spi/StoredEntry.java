package io.quarkiverse.idempotency.runtime.spi;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * The state held for a known idempotency key: the payload fingerprint plus, once the operation
 * has finished, the response to replay. A {@code null} response means the original request is
 * still in flight.
 *
 * @param fingerprint the request payload fingerprint
 * @param response the completed response, or {@code null} while in flight
 */
@RegisterForReflection
public record StoredEntry(String fingerprint, StoredResponse response) {

    /**
     * @return {@code true} when the original request has not yet completed
     */
    public boolean inFlight() {
        return response == null;
    }
}
