package br.com.balaio.api_gateway.security;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

final class GatewayCorsFilter extends OncePerRequestFilter {
    private static final List<String> METHODS = List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final Set<String> HEADERS = Set.of("authorization", "content-type", "accept");
    private final Set<String> origins;

    GatewayCorsFilter(GatewaySecurityProperties properties) {
        origins = properties.origins();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpServletResponse downstreamResponse = response;
        try {
            if (GatewayRequestPaths.apiPath(request) == null) {
                chain.doFilter(request, response);
                return;
            }
            response.addHeader("Vary", "Origin");
            var originHeaders = Collections.list(request.getHeaders("Origin"));
            if (originHeaders.size() > 1
                    || (!originHeaders.isEmpty() && !origins.contains(originHeaders.getFirst()))) {
                throw new GatewayRejection(403);
            }
            String origin = originHeaders.isEmpty() ? null : originHeaders.getFirst();
            if (origin == null && !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod())
                    && GatewayCredentials.hasSessionCookie(request)) {
                throw new GatewayRejection(403);
            }
            if (origin != null) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                response.setHeader("Access-Control-Allow-Credentials", "true");
            }
            if ("OPTIONS".equals(request.getMethod()) && request.getHeader("Access-Control-Request-Method") != null) {
                var methods = Collections.list(request.getHeaders("Access-Control-Request-Method"));
                if (origin == null || methods.size() != 1 || !METHODS.contains(methods.getFirst())
                        || !allowedHeaders(request)) {
                    throw new GatewayRejection(403);
                }
                response.setHeader("Access-Control-Allow-Methods", String.join(", ", METHODS));
                response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept");
                response.addHeader("Vary", "Access-Control-Request-Method");
                response.addHeader("Vary", "Access-Control-Request-Headers");
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }
            downstreamResponse = new GatewayCorsResponseWrapper(response, origin);
        } catch (GatewayRejection rejection) {
            if (!response.getHeaders("Vary").contains("Origin")) {
                response.addHeader("Vary", "Origin");
            }
            GatewayErrors.write(response, rejection.status());
            return;
        }
        chain.doFilter(request, downstreamResponse);
    }

    private boolean allowedHeaders(HttpServletRequest request) {
        for (String value : Collections.list(request.getHeaders("Access-Control-Request-Headers"))) {
            for (String header : value.split(",", -1)) {
                if (!HEADERS.contains(header.strip().toLowerCase(Locale.ROOT))) {
                    return false;
                }
            }
        }
        return true;
    }
}
