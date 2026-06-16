package io.quarkiverse.idempotency.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusUnitTest;
import io.restassured.http.Header;

/** {@code @Idempotent} in the default {@code all-methods} strategy: per-endpoint opt-in/out + requireKey. */
public class IdempotentAnnotationTest {

    @RegisterExtension
    static final QuarkusUnitTest TEST = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar.addClass(AnnotatedResource.class));

    @Test
    void getIsOptedIn() {
        // GET is not a globally guarded method, but @Idempotent opts it in: the second call replays.
        String first = given().header(new Header("Idempotency-Key", "g1"))
                .when().get("/annotated/report")
                .then().statusCode(200).header("Idempotent-Replayed", nullValue())
                .extract().asString();

        given().header(new Header("Idempotency-Key", "g1"))
                .when().get("/annotated/report")
                .then().statusCode(200)
                .header("Idempotent-Replayed", equalTo("true"))
                .body(equalTo(first)); // replayed verbatim; the handler did not run again
    }

    @Test
    void postIsOptedOut() {
        // POST is globally guarded, but @Idempotent(enabled=false) excludes it: no replay, runs each time.
        given().header(new Header("Idempotency-Key", "f1")).when().post("/annotated/fire")
                .then().statusCode(200).header("Idempotent-Replayed", nullValue());
        given().header(new Header("Idempotency-Key", "f1")).when().post("/annotated/fire")
                .then().statusCode(200).header("Idempotent-Replayed", nullValue());
    }

    @Test
    void requireKeyOverride() {
        // @Idempotent(requireKey = REQUIRED) → a missing key is rejected even though the global default is false.
        given().when().post("/annotated/strict").then().statusCode(400);
        given().header(new Header("Idempotency-Key", "s1")).when().post("/annotated/strict")
                .then().statusCode(200);
    }
}
