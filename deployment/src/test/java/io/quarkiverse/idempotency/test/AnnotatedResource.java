package io.quarkiverse.idempotency.test;

import java.util.concurrent.atomic.AtomicInteger;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.quarkiverse.idempotency.runtime.Idempotent;

/** Endpoints exercising {@link Idempotent}: opt-in a GET, opt-out a POST, and require the key. */
@Path("/annotated")
@Produces(MediaType.TEXT_PLAIN)
public class AnnotatedResource {

    static final AtomicInteger GET_RUNS = new AtomicInteger();
    static final AtomicInteger OPTOUT_RUNS = new AtomicInteger();

    /** GET is not globally guarded; the annotation opts it in. */
    @GET
    @Path("/report")
    @Idempotent
    public String report() {
        return "report#" + GET_RUNS.incrementAndGet();
    }

    /** POST is globally guarded; the annotation opts it out. */
    @POST
    @Path("/fire")
    @Idempotent(enabled = false)
    public String fire() {
        return "fire#" + OPTOUT_RUNS.incrementAndGet();
    }

    /** Require the key on this endpoint regardless of the global default. */
    @POST
    @Path("/strict")
    @Idempotent(requireKey = Idempotent.Require.REQUIRED)
    public String strict() {
        return "strict";
    }
}
