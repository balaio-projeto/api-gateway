package br.com.balaio.api_gateway.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

final class GatewayAuthenticationFilter extends OncePerRequestFilter {
    private final JwtDecoder decoder;
    private final GatewayAccessPolicy policy = new GatewayAccessPolicy();

    GatewayAuthenticationFilter(JwtDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpServletRequest forwarded = request;
        try {
            String path = GatewayRequestPaths.apiPath(request);
            if (path != null) {
                boolean authAction = policy.authAction(request.getMethod(), path);
                GatewayCredentials credentials = GatewayCredentials.read(request, authAction);
                Jwt bearer = decode(credentials.bearer());
                Jwt session = decode(credentials.session());
                if (bearer != null && session != null && !credentials.bearer().equals(credentials.session())) {
                    throw new GatewayRejection(400);
                }
                Jwt identity = bearer != null ? bearer : session;
                var access = policy.access(request, path);
                if (identity == null && access != GatewayAccessPolicy.Access.PUBLIC) {
                    throw new GatewayRejection(401);
                }
                if (identity != null && !access.accepts(identity.getClaimAsString("papel"))) {
                    throw new GatewayRejection(403);
                }
                forwarded = new GatewayRequestWrapper(request, identity == null ? null : identity.getTokenValue());
            }
        } catch (GatewayRejection rejection) {
            GatewayErrors.write(response, rejection.status());
            return;
        }
        chain.doFilter(forwarded, response);
    }

    private Jwt decode(String token) {
        if (token == null) {
            return null;
        }
        try {
            return decoder.decode(token);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new GatewayRejection(401);
        }
    }
}
