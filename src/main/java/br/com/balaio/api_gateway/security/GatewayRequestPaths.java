package br.com.balaio.api_gateway.security;

import jakarta.servlet.http.HttpServletRequest;

final class GatewayRequestPaths {
    private GatewayRequestPaths() {
    }

    static String apiPath(HttpServletRequest request) {
        String raw = request.getRequestURI().substring(request.getContextPath().length());
        String servletPath = request.getServletPath();
        if (!inApi(raw) && !inApi(servletPath)) {
            return null;
        }
        // The security policy and Gateway must see the same path. Refuse ambiguous encodings,
        // matrix parameters and traversal rather than relying on differing normalization rules.
        if (!inApi(raw) || raw.indexOf('%') >= 0 || raw.indexOf(';') >= 0 || raw.indexOf('\\') >= 0
                || raw.contains("//") || raw.contains("/./") || raw.contains("/../")
                || raw.endsWith("/.") || raw.endsWith("/..")) {
            throw new GatewayRejection(400);
        }
        return raw;
    }

    private static boolean inApi(String path) {
        return path != null && (path.equals("/api/v1") || path.startsWith("/api/v1/"));
    }
}
