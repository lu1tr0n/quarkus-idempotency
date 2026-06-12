package io.quarkiverse.idempotency.it;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Runs {@link IdempotencyTest} against the built artifact — the JVM jar with {@code mvn verify},
 * or the native binary with {@code mvn verify -Pnative}. This is the native smoke test.
 */
@QuarkusIntegrationTest
class IdempotencyIT extends IdempotencyTest {
}
