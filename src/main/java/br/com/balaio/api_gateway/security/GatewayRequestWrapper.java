package br.com.balaio.api_gateway.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

/** One consistent header view for both Servlet and Gateway's ServerRequest adapter. */
final class GatewayRequestWrapper extends HttpServletRequestWrapper {
    private final Map<String, List<String>> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Cookie[] cookies;

    GatewayRequestWrapper(HttpServletRequest request, String validatedToken) {
        super(request);
        for (String name : Collections.list(request.getHeaderNames())) {
            if (!untrusted(name) && !"Authorization".equalsIgnoreCase(name) && !"Cookie".equalsIgnoreCase(name)) {
                headers.put(name, List.copyOf(Collections.list(request.getHeaders(name))));
            }
        }
        List<String> remainingCookies = new ArrayList<>();
        for (String header : Collections.list(request.getHeaders("Cookie"))) {
            List<String> fields = new ArrayList<>();
            for (String field : header.split(";", -1)) {
                String trimmed = field.strip();
                int equals = trimmed.indexOf('=');
                String name = equals < 0 ? trimmed : trimmed.substring(0, equals).strip();
                if (!GatewayCredentials.SESSION_COOKIE.equals(name) && !trimmed.isEmpty()) {
                    fields.add(trimmed);
                }
            }
            if (!fields.isEmpty()) {
                remainingCookies.add(String.join("; ", fields));
            }
        }
        if (!remainingCookies.isEmpty()) {
            headers.put("Cookie", List.copyOf(remainingCookies));
        }
        if (validatedToken != null) {
            headers.put("Authorization", List.of("Bearer " + validatedToken));
        }
        cookies = request.getCookies() == null ? null : Arrays.stream(request.getCookies())
                .filter(cookie -> !GatewayCredentials.SESSION_COOKIE.equals(cookie.getName()))
                .toArray(Cookie[]::new);
    }

    @Override
    public String getHeader(String name) {
        List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        return Collections.enumeration(headers.getOrDefault(name, List.of()));
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return Collections.enumeration(headers.keySet());
    }

    @Override
    public Cookie[] getCookies() {
        return cookies == null || cookies.length == 0 ? null : cookies.clone();
    }

    private boolean untrusted(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("forwarded") || lower.startsWith("x-forwarded-")
                || lower.startsWith("x-user") || lower.startsWith("x-account") || lower.startsWith("x-role")
                || lower.startsWith("x-papel") || lower.startsWith("x-auth") || lower.startsWith("x-identity")
                || lower.startsWith("x-principal") || lower.startsWith("x-remote-user") || lower.equals("remote-user")
                || lower.startsWith("x-usuario") || lower.startsWith("x-conta") || lower.startsWith("x-perfil")
                || lower.startsWith("x-subject") || lower.startsWith("x-original-user");
    }
}
