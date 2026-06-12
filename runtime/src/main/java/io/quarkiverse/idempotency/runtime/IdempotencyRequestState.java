package io.quarkiverse.idempotency.runtime;

import jakarta.enterprise.context.RequestScoped;

/**
 * Request-scoped carrier that hands the active idempotency key from the request filter (which
 * reserved it) to the response filter (which captures and stores the response). A non-null key
 * means this request owns an in-flight reservation that must be completed.
 */
@RequestScoped
public class IdempotencyRequestState {

    private String activeKey;
    private String fingerprint;

    public String getActiveKey() {
        return activeKey;
    }

    public void setActiveKey(String activeKey) {
        this.activeKey = activeKey;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }
}
