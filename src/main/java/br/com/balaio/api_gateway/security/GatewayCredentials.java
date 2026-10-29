package br.com.balaio.api_gateway.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

record GatewayCredentials(String bearer, String session, boolean sessionPresent) {
    static final String SESSION_COOKIE = "balaio_sessao";

    static GatewayCredentials read(HttpServletRequest request, boolean ignoreExisting) {
        if (ignoreExisting) {
            return new GatewayCredentials(null, null, hasSessionCookie(request));
        }
        List<String> authorizations = Collections.list(request.getHeaders("Authorization"));
        List<String> sessions = sessions(request);
        if (authorizations.size() > 1 || sessions.size() > 1
                || (!authorizations.isEmpty() && authorizations.getFirst().contains(","))) {
            throw new GatewayRejection(400);
        }
        String bearer = null;
        if (!authorizations.isEmpty()) {
            String value = authorizations.getFirst();
            if (!value.matches("(?i)Bearer [A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) {
                throw new GatewayRejection(401);
            }
            bearer = value.substring(7);
        }
        String session = sessions.isEmpty() ? null : sessions.getFirst();
        if (session != null && session.isEmpty()) {
            throw new GatewayRejection(401);
        }
        return new GatewayCredentials(bearer, session, !sessions.isEmpty());
    }

    static boolean hasSessionCookie(HttpServletRequest request) {
        return !sessions(request).isEmpty();
    }

    private static List<String> sessions(HttpServletRequest request) {
        List<String> values = new ArrayList<>();
        List<String> cookieHeaders = Collections.list(request.getHeaders("Cookie"));
        if (!cookieHeaders.isEmpty()) {
            for (String header : cookieHeaders) {
                for (String field : header.split(";", -1)) {
                    String cookie = field.strip();
                    int equals = cookie.indexOf('=');
                    String name = equals < 0 ? cookie : cookie.substring(0, equals).strip();
                    if (SESSION_COOKIE.equals(name)) {
                        values.add(equals < 0 ? "" : cookie.substring(equals + 1).strip());
                    }
                }
            }
        } else if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (SESSION_COOKIE.equals(cookie.getName())) {
                    values.add(cookie.getValue() == null ? "" : cookie.getValue());
                }
            }
        }
        return values;
    }
}
