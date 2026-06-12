package io.quarkiverse.idempotency.test;

import static io.restassured.RestAssured.given;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.Header;

public class RequireKeyTest {

    @RegisterExtension
    static final QuarkusUnitTest TEST = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClass(PaymentResource.class))
            .overrideConfigKey("quarkus.idempotency.require-key", "true");

    @Test
    void missingKeyOnGuardedMethodIsRejected() {
        given().contentType("application/json").body("{\"amount\":5}")
                .when().post("/payments/charge")
                .then().statusCode(400);
    }

    @Test
    void presentKeyIsAccepted() {
        given().header(new Header("Idempotency-Key", "key-required"))
                .contentType("application/json").body("{\"amount\":5}")
                .when().post("/payments/charge")
                .then().statusCode(200);
    }
}
