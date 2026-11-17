package br.com.balaio.api_gateway.security;

import java.util.Arrays;
import java.util.Locale;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

/** The edge owns CORS; upstream responses cannot widen the browser's permissions. */
final class GatewayCorsResponseWrapper extends HttpServletResponseWrapper {
    private final String origin;

    GatewayCorsResponseWrapper(HttpServletResponse response, String origin) {
        super(response);
        this.origin = origin;
    }

    @Override
    public void setHeader(String name, String value) {
        if (!corsHeader(name)) {
            super.setHeader(name, "Vary".equalsIgnoreCase(name) ? varyWithOrigin(value) : value);
        }
    }

    @Override
    public void addHeader(String name, String value) {
        if (!corsHeader(name) && value != null) {
            if ("Vary".equalsIgnoreCase(name)) {
                // Preserve the edge's Origin variation while retaining upstream cache dimensions.
                for (String field : value.split(",")) {
                    if (!"Origin".equalsIgnoreCase(field.strip()) && !field.isBlank()) {
                        super.addHeader(name, field.strip());
                    }
                }
            } else {
                super.addHeader(name, value);
            }
        }
    }

    @Override
    public void setIntHeader(String name, int value) {
        setHeader(name, Integer.toString(value));
    }

    @Override
    public void addIntHeader(String name, int value) {
        addHeader(name, Integer.toString(value));
    }

    @Override
    public void setDateHeader(String name, long value) {
        if (!corsHeader(name) && !"Vary".equalsIgnoreCase(name)) {
            super.setDateHeader(name, value);
        }
    }

    @Override
    public void addDateHeader(String name, long value) {
        if (!corsHeader(name) && !"Vary".equalsIgnoreCase(name)) {
            super.addDateHeader(name, value);
        }
    }

    @Override
    public void reset() {
        super.reset();
        super.setHeader("Vary", "Origin");
        if (origin != null) {
            super.setHeader("Access-Control-Allow-Origin", origin);
            super.setHeader("Access-Control-Allow-Credentials", "true");
        }
    }

    private static boolean corsHeader(String name) {
        return name.toLowerCase(Locale.ROOT).startsWith("access-control-");
    }

    private static String varyWithOrigin(String value) {
        if (value == null || value.isBlank()) {
            return "Origin";
        }
        return Arrays.stream(value.split(",")).anyMatch(field -> "Origin".equalsIgnoreCase(field.strip()))
                ? value : value + ", Origin";
    }
}
