package io.quarkiverse.idempotency.it;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.quarkiverse.idempotency.runtime.Idempotent;

/**
 * A non-idempotent "charge" operation whose side effect is counted, so the tests can prove the
 * handler runs exactly once across retries with the same idempotency key.
 */
@Path("/payments")
public class PaymentApiResource {

    private static final AtomicInteger CHARGES = new AtomicInteger();
    private static final AtomicInteger REPORTS = new AtomicInteger();

    @POST
    @Path("/charge")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response charge(String body) {
        int n = CHARGES.incrementAndGet();
        return Response.status(201)
                .header("Location", "/payments/" + n)
                .entity("{\"charge\":" + n + "}")
                .build();
    }

    @GET
    @Path("/count")
    @Produces(MediaType.TEXT_PLAIN)
    public int count() {
        return CHARGES.get();
    }

    /** GET is not globally guarded; {@code @Idempotent} opts it in — verifies the annotation in native. */
    @GET
    @Path("/report")
    @Idempotent
    @Produces(MediaType.TEXT_PLAIN)
    public String report() {
        return "report#" + REPORTS.incrementAndGet();
    }
}
