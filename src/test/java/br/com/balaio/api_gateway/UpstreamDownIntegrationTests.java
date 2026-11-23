package br.com.balaio.api_gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integração real: o serviço de destino do catálogo está fora do ar (porta fechada). O gateway
 * precisa responder 503 com o envelope, sem vazar a falha interna do Spring.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UpstreamDownIntegrationTests {
    private static final int CLOSED_PORT = closedPort();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        GatewaySecurityTestFixtures.registrarSeguranca(properties);
        properties.add("CATALOG_SERVICE_URL", () -> "http://127.0.0.1:" + CLOSED_PORT);
    }

    @Test
    void catalogoIndisponivelRetorna503NoEnvelope() throws Exception {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/pecas")).GET().build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).isEqualTo("{\"dados\":null,\"erro\":{\"codigo\":\"SERVICO_INDISPONIVEL\","
                + "\"mensagem\":\"Serviço temporariamente indisponível. Tente novamente em instantes.\"}}");
    }

    @Test
    void rotaNaoRoteadaComCredencialRetornaEnvelopeDe404() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/inexistente"))
                .header("Authorization", "Bearer " + GatewaySecurityTestFixtures.token("comprador"))
                .GET().build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).isEqualTo("{\"dados\":null,\"erro\":{\"codigo\":\"RECURSO_NAO_ENCONTRADO\","
                + "\"mensagem\":\"Recurso não encontrado.\"}}");
    }

    private static int closedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
