package io.quarkiverse.idempotency.runtime;

import jakarta.ws.rs.core.Response;

/**
 * Builds RFC 9457 / RFC 7807 {@code application/problem+json} error responses for the idempotency
 * rejection cases (missing/invalid key, conflict, payload mismatch, authentication required).
 *
 * <p>
 * The Idempotency-Key draft asks for an error body "containing a link pointing to relevant
 * documentation". Each problem therefore carries a {@code type} URI (the configured documentation
 * base plus a per-problem fragment) and an equivalent {@code Link} header, so both
 * machine-readable and link-following clients reach the docs. The JSON is built by hand to avoid
 * requiring a JSON provider on the classpath.
 */
final class ProblemResponse {

    private ProblemResponse() {
    }

    static Response of(int status, String slug, String title, String detail, String baseUri) {
        String typeUri = typeUri(baseUri, slug);
        String json = "{"
                + "\"type\":\"" + escape(typeUri) + "\","
                + "\"title\":\"" + escape(title) + "\","
                + "\"status\":" + status + ","
                + "\"detail\":\"" + escape(detail) + "\""
                + "}";
        return Response.status(status)
                .entity(json)
                .type("application/problem+json")
                .header("Link", "<" + typeUri + ">; rel=\"help\"")
                .build();
    }

    private static String typeUri(String baseUri, String slug) {
        if (baseUri == null || baseUri.isBlank()) {
            return "urn:quarkus-idempotency:" + slug;
        }
        String sep = baseUri.contains("#") ? "-" : "#";
        return baseUri + sep + slug;
    }

    private static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
