package br.com.balaio.api_gateway.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ConnectException;

import jakarta.servlet.ServletException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.ResourceAccessException;

class GatewayUpstreamFailureTests {
    private final JwtDecoder decoder = token -> {
        throw new IllegalStateException("não deve ser chamado em rota pública sem credencial");
    };

    @Test
    void falhaDeConexaoDoUpstreamRetornaEnvelopeDe503() throws Exception {
        var filter = new GatewayAuthenticationFilter(decoder);
        var request = new MockHttpServletRequest("GET", "/api/v1/pecas");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) throws IOException, ServletException {
                throw new ResourceAccessException("I/O error", new ConnectException("Connection refused"));
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString())
                .isEqualTo("{\"dados\":null,\"erro\":{\"codigo\":\"SERVICO_INDISPONIVEL\","
                        + "\"mensagem\":\"Serviço temporariamente indisponível. Tente novamente em instantes.\"}}");
    }

    @Test
    void causaDeConexaoEmServletExceptionTambemRetornaEnvelopeDe503() throws Exception {
        var filter = new GatewayAuthenticationFilter(decoder);
        var request = new MockHttpServletRequest("GET", "/api/v1/eventos");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) throws IOException, ServletException {
                throw new ServletException(new RuntimeException("wrapper", new java.net.SocketTimeoutException("read timed out")));
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("\"codigo\":\"SERVICO_INDISPONIVEL\"");
    }

    /**
     * TC23 (TS10): a falha de conexão não gera reenvio automático pelo gateway. A chave de idempotência
     * do cliente chega ao serviço de destino para que a repetição manual não duplique efeitos.
     */
    @Test
    void postComFalhaDeConexaoNaoEhReenviadoENaoPerdeAChaveDeIdempotencia() throws Exception {
        var filter = new GatewayAuthenticationFilter(decoder);
        var request = new MockHttpServletRequest("POST", "/api/v1/pagamentos/mercado-pago/notificacoes");
        request.addHeader("Idempotency-Key", "notificacao-42");
        request.addHeader("x-request-id", "req-42");
        var response = new MockHttpServletResponse();
        int[] tentativas = {0};
        var chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) throws IOException, ServletException {
                tentativas[0]++;
                assertThat(((jakarta.servlet.http.HttpServletRequest) req).getHeader("Idempotency-Key")).isEqualTo("notificacao-42");
                throw new ResourceAccessException("I/O error", new ConnectException("Connection refused"));
            }
        };

        filter.doFilter(request, response, chain);

        assertThat(tentativas[0]).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getContentAsString()).contains("\"codigo\":\"SERVICO_INDISPONIVEL\"")
                .doesNotContain("notificacao-42", "Connection refused");
    }

    @Test
    void erroQueNaoEhFalhaDeUpstreamPropagaSemSerMascarado() {
        var filter = new GatewayAuthenticationFilter(decoder);
        var request = new MockHttpServletRequest("GET", "/api/v1/pecas");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                throw new IllegalArgumentException("bug interno");
            }
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
