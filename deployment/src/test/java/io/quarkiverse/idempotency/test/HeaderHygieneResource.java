package io.quarkiverse.idempotency.test;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** Returns sensitive and benign headers so a test can assert the capture deny-list holds on replay. */
@Path("/secure")
public class HeaderHygieneResource {

    static final AtomicInteger EXECUTIONS = new AtomicInteger();

    @POST
    @Path("/charge")
    @Produces(MediaType.TEXT_PLAIN)
    public Response charge() {
        int n = EXECUTIONS.incrementAndGet();
        return Response.ok("ok#" + n)
                .header("Set-Cookie", "session=secret-" + n)
                .header("Authorization", "Bearer token-" + n)
                .header("X-Auth-Token", "tok-" + n)
                .header("X-Custom", "custom-" + n)
                .build();
    }
}
