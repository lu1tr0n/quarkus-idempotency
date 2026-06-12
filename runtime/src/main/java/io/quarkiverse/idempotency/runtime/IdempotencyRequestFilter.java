package io.quarkiverse.idempotency.runtime;

import java.security.Principal;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;

import io.quarkiverse.idempotency.runtime.spi.IdempotencyStore;
import io.quarkiverse.idempotency.runtime.spi.Reservation;
import io.quarkiverse.idempotency.runtime.spi.StoredEntry;
import io.quarkiverse.idempotency.runtime.spi.StoredResponse;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.smallrye.common.annotation.Blocking;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;

/**
 * Resolves idempotency for guarded requests carrying the configured key header. Runs after
 * authentication so the request's identity is known and the stored key is scoped to it.
 *
 * <ul>
 * <li>New key — reserve it and let the request proceed (the response filter stores the result).</li>
 * <li>Same key, same payload, completed — replay the stored response.</li>
 * <li>Same key, still in flight — 409 Conflict.</li>
 * <li>Same key, different payload — 422 Unprocessable Entity.</li>
 * <li>Required key missing/invalid — 400 Bad Request.</li>
 * <li>Identity required but request is anonymous — 401 Unauthorized.</li>
 * </ul>
 *
 * <p>
 * The store key is never the raw client header: it is {@code sha256(principal ⌷ scope ⌷ rawKey)}
 * (see {@link StorageKey}), so one caller can never be served another caller's response, even when
 * idempotency keys are predictable/meaningful.
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
@ApplicationScoped
public class IdempotencyRequestFilter implements ContainerRequestFilter {

    static final int UNPROCESSABLE_ENTITY = 422;

    @Inject
    IdempotencyConfig config;

    @Inject
    Instance<IdempotencyStore> store;

    @Inject
    IdempotencyRequestState state;

    @Inject
    CurrentVertxRequest currentRequest;

    @Override
    @Blocking
    public void filter(ContainerRequestContext requestContext) {
        if (!config.enabled() || !config.methods().contains(requestContext.getMethod())) {
            return;
        }

        String rawKey = requestContext.getHeaderString(config.headerName());
        if (rawKey == null || rawKey.isBlank()) {
            if (config.requireKey()) {
                problem(requestContext, 400, "idempotency-key-required", "Idempotency-Key required",
                        "This endpoint requires a " + config.headerName() + " header.");
            }
            return;
        }

        String key = unquote(rawKey.trim());
        if (key.isEmpty() || key.length() > config.maxKeyLength() || hasControlChars(key)) {
            problem(requestContext, 400, "idempotency-key-invalid", "Invalid Idempotency-Key",
                    "The " + config.headerName() + " header is empty, too long, or contains control characters.");
            return;
        }

        String principal = principalName(requestContext.getSecurityContext());
        if (config.requireIdentity() && principal.isEmpty()) {
            problem(requestContext, 401, "authentication-required", "Authentication required",
                    "An authenticated identity is required to use " + config.headerName() + ".");
            return;
        }
        String scope = scopeValue(requestContext);
        String storageKey = StorageKey.derive(principal, scope, key);

        String fingerprint = config.fingerprintEnabled()
                ? Fingerprint.compute(requestContext.getMethod(), requestContext.getUriInfo().getPath(),
                        rawQuery(requestContext), readBody(), config.maxFingerprintBody().asLongValue())
                : "";

        Reservation reservation = store.get().acquire(storageKey, fingerprint, config.lockTtl());
        if (reservation instanceof Reservation.Acquired) {
            state.setActiveKey(storageKey);
            state.setFingerprint(fingerprint);
            return;
        }

        StoredEntry entry = ((Reservation.Existing) reservation).entry();
        if (config.fingerprintEnabled() && !entry.fingerprint().equals(fingerprint)) {
            problem(requestContext, UNPROCESSABLE_ENTITY, "idempotency-key-mismatch",
                    "Idempotency-Key reused with a different payload",
                    "The " + config.headerName() + " was already used for a request with a different "
                            + "method, path, query, or body.");
        } else if (entry.inFlight()) {
            problem(requestContext, 409, "idempotency-key-conflict", "Request already in progress",
                    "A request with this " + config.headerName() + " is still being processed.");
        } else {
            requestContext.abortWith(buildReplay(entry.response()));
        }
    }

    private String scopeValue(ContainerRequestContext ctx) {
        if (config.scopeHeader().isEmpty()) {
            return "";
        }
        String value = ctx.getHeaderString(config.scopeHeader().get());
        return value == null ? "" : value;
    }

    private static String principalName(SecurityContext securityContext) {
        if (securityContext == null) {
            return "";
        }
        Principal principal = securityContext.getUserPrincipal();
        return principal == null ? "" : principal.getName();
    }

    private static String rawQuery(ContainerRequestContext ctx) {
        return ctx.getUriInfo().getRequestUri().getRawQuery();
    }

    private Buffer readBody() {
        RoutingContext rc = currentRequest.getCurrent();
        if (rc == null) {
            return null;
        }
        RequestBody body = rc.body();
        return body != null ? body.buffer() : null;
    }

    private Response buildReplay(StoredResponse stored) {
        Response.ResponseBuilder rb = Response.status(stored.status());
        if (stored.entity() != null) {
            rb.entity(stored.entity());
        }
        if (stored.mediaType() != null) {
            rb.type(stored.mediaType());
        }
        stored.headers().forEach(rb::header);
        String marker = config.replayedHeader();
        if (marker != null && !marker.isBlank()) {
            rb.header(marker, "true");
        }
        return rb.build();
    }

    private void problem(ContainerRequestContext ctx, int status, String slug, String title, String detail) {
        ctx.abortWith(ProblemResponse.of(status, slug, title, detail, config.problemBaseUri()));
    }

    private static boolean hasControlChars(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return true;
            }
        }
        return false;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
