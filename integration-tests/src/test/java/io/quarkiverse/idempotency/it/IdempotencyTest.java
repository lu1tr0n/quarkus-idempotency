package io.quarkiverse.idempotency.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.Header;

@QuarkusTest
class IdempotencyTest {

    private int count() {
        return Integer.parseInt(given().when().get("/payments/count")
                .then().statusCode(200).extract().asString().trim());
    }

    @Test
    void retrySameKeyReplaysAndRunsOnce() {
        int before = count();
        String body = "{\"amount\":500}";

        String first = given()
                .header(new Header("Idempotency-Key", "it-1"))
                .contentType("application/json").body(body)
                .when().post("/payments/charge")
                .then().statusCode(201)
                .header("Idempotent-Replayed", nullValue())
                .extract().asString();

        given()
                .header(new Header("Idempotency-Key", "it-1"))
                .contentType("application/json").body(body)
                .when().post("/payments/charge")
                .then().statusCode(201)
                .header("Idempotent-Replayed", equalTo("true"))
                .body(equalTo(first));

        assertEquals(1, count() - before, "the charge must run exactly once across the retry");
    }

    @Test
    void differentPayloadSameKeyRejected() {
        given().header(new Header("Idempotency-Key", "it-2"))
                .contentType("application/json").body("{\"amount\":1}")
                .when().post("/payments/charge").then().statusCode(201);

        given().header(new Header("Idempotency-Key", "it-2"))
                .contentType("application/json").body("{\"amount\":2}")
                .when().post("/payments/charge").then().statusCode(422);
    }
}
