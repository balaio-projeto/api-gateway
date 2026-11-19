package br.com.balaio.api_gateway.security;

import java.util.Arrays;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

final class GatewayAccessPolicy {
    private static final Set<String> AUTH_ACTIONS = Set.of("/api/v1/auth/cadastro", "/api/v1/auth/login",
            "/api/v1/auth/entrar", "/api/v1/auth/recuperacao", "/api/v1/auth/redefinicao", "/api/v1/auth/logout");

    enum Access {
        PUBLIC, AUTHENTICATED, COMPRADOR, COMPRADOR_OR_ADMIN, ARTESAO, ARTESAO_OR_ADMIN, ADMIN;

        boolean accepts(String role) {
            return switch (this) {
                case PUBLIC, AUTHENTICATED -> true;
                case COMPRADOR -> "comprador".equals(role);
                case COMPRADOR_OR_ADMIN -> "comprador".equals(role) || "admin".equals(role);
                case ARTESAO -> "artesao".equals(role);
                case ARTESAO_OR_ADMIN -> "artesao".equals(role) || "admin".equals(role);
                case ADMIN -> "admin".equals(role);
            };
        }
    }

    boolean authAction(String method, String path) {
        return "POST".equals(method) && AUTH_ACTIONS.contains(path);
    }

    Access access(HttpServletRequest request, String path) {
        String method = request.getMethod();
        boolean read = "GET".equals(method) || "HEAD".equals(method);
        if (authAction(method, path)) {
            return Access.PUBLIC;
        }
        // Only the collection GET is shared; ownership remains in Community.
        if (path.equals("/api/v1/admin/mediacoes") && "GET".equals(method)) {
            return Access.COMPRADOR_OR_ADMIN;
        }
        if (path.equals("/api/v1/admin/mediacoes") && "POST".equals(method)) {
            return Access.AUTHENTICATED;
        }
        if (path.equals("/api/v1/admin") || path.startsWith("/api/v1/admin/")) {
            return Access.ADMIN;
        }
        if (path.equals("/api/v1/artesao") || path.startsWith("/api/v1/artesao/")
                || path.matches("/api/v1/artesaos/[^/]+/configuracoes")) {
            return Access.ARTESAO;
        }
        if ("PATCH".equals(method) && path.matches("/api/v1/artesaos/[^/]+")) {
            return Access.ARTESAO_OR_ADMIN;
        }
        if ("PUT".equals(method) && path.equals("/api/v1/estado/sacola")) {
            return Access.COMPRADOR;
        }
        if (("POST".equals(method) && (path.equals("/api/v1/pecas") || path.equals("/api/v1/videos")))
                || (Set.of("PATCH", "DELETE").contains(method) && path.matches("/api/v1/pecas/[^/]+"))
                || ("PATCH".equals(method) && (path.matches("/api/v1/pedidos/[^/]+/(estado|recusar)")
                        || path.matches("/api/v1/pecas/[^/]+/perguntas/[^/]+")))) {
            return Access.ARTESAO;
        }
        if (read && path.equals("/api/v1/videos")) {
            String[] panel = request.getParameterValues("painel");
            // The panel includes drafts. Every query value participates in this decision.
            if (panel != null && Arrays.asList(panel).contains("true")) {
                return Access.ARTESAO;
            }
        }
        if (read && (path.matches("/api/v1/pecas(?:/[^/]+(?:/(?:historico|relacionadas|perguntas))?)?")
                || path.matches("/api/v1/artesaos(?:/[^/]+(?:/(?:pecas|reputacao))?)?")
                || path.matches("/api/v1/coletivos(?:/[^/]+)?")
                || path.equals("/api/v1/referencias") || path.matches("/api/v1/arquivos/pecas/[^/]+")
                || path.matches("/api/v1/eventos(?:/[^/]+)?")
                || path.matches("/api/v1/videos(?:/[^/]+(?:/(?:comentarios|arquivo|capa))?)?")
                || path.equals("/api/v1/fretes") || path.equals("/api/v1/estado"))) {
            return Access.PUBLIC;
        }
        if ("POST".equals(method) && (path.equals("/api/v1/eventos")
                || path.matches("/api/v1/videos/[^/]+/visualizacoes")
                || path.equals("/api/v1/pagamentos/mercado-pago/notificacoes"))) {
            return Access.PUBLIC;
        }
        return Access.AUTHENTICATED;
    }
}
