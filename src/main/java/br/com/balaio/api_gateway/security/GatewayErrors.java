package br.com.balaio.api_gateway.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

final class GatewayErrors {
    private GatewayErrors() {
    }

    static void write(HttpServletResponse response, int status) throws IOException {
        String code;
        String message;
        switch (status) {
            case 400 -> {
                code = "REQUISICAO_INVALIDA";
                message = "Requisição inválida.";
            }
            case 403 -> {
                code = "SEM_PERMISSAO";
                message = "Você não tem permissão para continuar.";
            }
            default -> {
                status = 401;
                code = "NAO_AUTENTICADO";
                message = "Entre na sua conta para continuar.";
            }
        }
        writeEnvelope(response, status, code, message);
    }

    /** Falha de conexão ou timeout com o serviço de destino: mesmo envelope, sem detalhes internos. */
    static void writeUnavailable(HttpServletResponse response) throws IOException {
        writeEnvelope(response, 503, "SERVICO_INDISPONIVEL",
                "Serviço temporariamente indisponível. Tente novamente em instantes.");
    }

    private static void writeEnvelope(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"dados\":null,\"erro\":{\"codigo\":\"" + code
                + "\",\"mensagem\":\"" + message + "\"}}");
    }
}
