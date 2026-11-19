package br.com.balaio.api_gateway.security;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/** Early check for declared sizes. Profile enforces actual multipart limits, including chunked bodies. */
final class GatewayMediaSizeFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("PUT".equals(request.getMethod())
                && "/api/v1/artesao/me/imagem".equals(GatewayRequestPaths.apiPath(request))
                && request.getContentLengthLong() > 3L * 1024 * 1024) {
            response.setHeader("Cache-Control", "no-store");
            GatewayErrors.write(response, 413);
            return;
        }
        chain.doFilter(request, response);
    }
}
