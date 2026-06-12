package io.quarkiverse.idempotency.runtime;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logging.Logger;

import io.quarkiverse.idempotency.runtime.spi.IdempotencyStore;
import io.quarkiverse.idempotency.runtime.spi.StoredResponse;
import io.smallrye.common.annotation.Blocking;

/**
 * Captures the response of a request that reserved an idempotency key and stores it for replay.
 * Requests that only replayed, passed through, or were rejected carry no active key and are
 * ignored. A 5xx response releases the key (unless configured otherwise) so the client can retry.
 *
 * <p>
 * Only headers on the configured allow-list are captured, and a hard deny-list of credential-bearing
 * headers is enforced unconditionally so a stored response can never replay another caller's secrets.
 * Responses larger than {@code max-stored-body} are not stored (the key is released).
 */
@Provider
@ApplicationScoped
public class IdempotencyResponseFilter implements ContainerResponseFilter {

    private static final Logger LOG = Logger.getLogger(IdempotencyResponseFilter.class);

    /** Credential/identity-bearing headers that must never be captured, regardless of config. */
    private static final Set<String> DENIED_HEADERS = Set.of(
            "set-cookie", "set-cookie2", "cookie", "authorization", "proxy-authorization",
            "www-authenticate", "proxy-authenticate");

    /** Name fragments that mark a header as secret-bearing; denied even if not in the literal set. */
    private static final String[] DENIED_FRAGMENTS = { "token", "secret", "api-key", "apikey",
            "password", "passwd", "credential", "private-key" };

    @Inject
    IdempotencyConfig config;

    @Inject
    Instance<IdempotencyStore> store;

    @Inject
    IdempotencyRequestState state;

    @Override
    @Blocking
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        String key = state.getActiveKey();
        if (key == null) {
            return;
        }

        int status = responseContext.getStatus();
        if (status >= 500 && !config.cacheErrorResponses()) {
            store.get().release(key);
            return;
        }

        long limit = config.maxStoredBody().asLongValue();
        if (limit > 0) {
            Long size = measuredBodySize(responseContext);
            if (size != null && size > limit) {
                LOG.debugf("Response body (%s bytes) exceeds max-stored-body (%s); not caching",
                        size, Long.valueOf(limit));
                store.get().release(key);
                return;
            }
        }

        Map<String, String> headers = capturedHeaders(responseContext);

        String mediaType = responseContext.getMediaType() != null
                ? responseContext.getMediaType().toString()
                : null;

        store.get().complete(key, state.getFingerprint(),
                new StoredResponse(status, headers, responseContext.getEntity(), mediaType),
                config.responseTtl());
    }

    private Map<String, String> capturedHeaders(ContainerResponseContext responseContext) {
        Map<String, String> headers = new HashMap<>();
        for (String name : config.capturedHeaders()) {
            if (name == null || name.isBlank() || isDenied(name)) {
                continue;
            }
            String value = responseContext.getHeaderString(name);
            if (value != null) {
                headers.put(name, value);
            }
        }
        return headers;
    }

    /** A header is denied if it is a known credential header or its name embeds a secret fragment. */
    private static boolean isDenied(String name) {
        String normalized = name.trim().toLowerCase();
        if (DENIED_HEADERS.contains(normalized)) {
            return true;
        }
        for (String fragment : DENIED_FRAGMENTS) {
            if (normalized.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    /** Best-effort body size in bytes, or {@code null} when it cannot be determined cheaply. */
    private static Long measuredBodySize(ContainerResponseContext responseContext) {
        if (responseContext.getLength() >= 0) {
            return (long) responseContext.getLength();
        }
        Object entity = responseContext.getEntity();
        if (entity instanceof byte[] bytes) {
            return (long) bytes.length;
        }
        if (entity instanceof CharSequence text) {
            return (long) text.toString().getBytes(StandardCharsets.UTF_8).length;
        }
        return null;
    }
}
