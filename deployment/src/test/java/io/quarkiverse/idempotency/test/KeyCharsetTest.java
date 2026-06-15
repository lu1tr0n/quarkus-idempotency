package io.quarkiverse.idempotency.test;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.Header;

/**
 * The Idempotency-Key draft defines the value as an RFC 8941 sf-string: printable US-ASCII
 * ({@code 0x20}-{@code 0x7E}). A key carrying a non-ASCII byte must be rejected with HTTP 400, while
 * a plain-ASCII key is accepted.
 */
public class KeyCharsetTest {

    @RegisterExtension
    static final QuarkusUnitTest TEST = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClass(PaymentResource.class));

    private static final String BODY = "{\"amount\":1000}";

    @Test
    void nonAsciiKeyIsRejected() {
        given().header(new Header("Idempotency-Key", "café-key")) // é (0xE9) is not sf-string
                .contentType("application/json").body(BODY)
                .when().post("/payments/charge")
                .then().statusCode(400)
                .contentType("application/problem+json");
    }

    @Test
    void plainAsciiKeyIsAccepted() {
        given().header(new Header("Idempotency-Key", "ascii-key-123"))
                .contentType("application/json").body(BODY)
                .when().post("/payments/charge")
                .then().statusCode(200);
    }
}
