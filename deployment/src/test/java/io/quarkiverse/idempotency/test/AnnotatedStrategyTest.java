package io.quarkiverse.idempotency.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.Header;

/**
 * With {@code quarkus.idempotency.strategy=annotated}, only endpoints carrying {@link
 * io.quarkiverse.idempotency.runtime.Idempotent} are guarded — a plain guarded HTTP method is not.
 */
public class AnnotatedStrategyTest {

    @RegisterExtension
    static final QuarkusUnitTest TEST = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClass(AnnotatedResource.class).addClass(PaymentResource.class))
            .overrideConfigKey("quarkus.idempotency.strategy", "annotated");

    @Test
    void unannotatedPostIsNotGuarded() {
        // PaymentResource.charge has no @Idempotent → not guarded in annotated mode → never replays.
        given().header(new Header("Idempotency-Key", "p1")).contentType("application/json").body("{}")
                .when().post("/payments/charge")
                .then().statusCode(200).header("Idempotent-Replayed", nullValue());
        given().header(new Header("Idempotency-Key", "p1")).contentType("application/json").body("{}")
                .when().post("/payments/charge")
                .then().statusCode(200).header("Idempotent-Replayed", nullValue());
    }

    @Test
    void annotatedEndpointIsStillGuarded() {
        given().header(new Header("Idempotency-Key", "r1")).when().get("/annotated/report")
                .then().statusCode(200).header("Idempotent-Replayed", nullValue());
        given().header(new Header("Idempotency-Key", "r1")).when().get("/annotated/report")
                .then().statusCode(200).header("Idempotent-Replayed", equalTo("true"));
    }
}
