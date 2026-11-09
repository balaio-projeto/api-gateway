package br.com.balaio.api_gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTests {
    private static final AtomicReference<String> recebida = new AtomicReference<>();
    private static HttpServer destino;

    @DynamicPropertySource
    static void destinoCommerce(DynamicPropertyRegistry propriedades) throws Exception {
        destino = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        destino.createContext("/api/v1/pagamentos/mercado-pago/notificacoes", requisicao -> {
            recebida.set(requisicao.getRequestMethod() + " " + requisicao.getRequestURI() + " "
                    + requisicao.getRequestHeaders().getFirst("x-signature") + " "
                    + requisicao.getRequestHeaders().getFirst("x-request-id"));
            requisicao.getRequestBody().readAllBytes();
            requisicao.sendResponseHeaders(200, -1);
            requisicao.close();
        });
        destino.start();
        propriedades.add("COMMERCE_SERVICE_URL", () -> "http://127.0.0.1:" + destino.getAddress().getPort());
    }

    @AfterAll
    static void parar() { if (destino != null) destino.stop(0); }

    @Value("${local.server.port}") int porta;

	@Test
	void encaminhaWebhookComAssinaturaEQuery() throws Exception {
		var requisicao = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta
				+ "/api/v1/pagamentos/mercado-pago/notificacoes?data.id=123"))
				.header("x-signature", "ts=123,v1=abc")
				.header("x-request-id", "request-teste")
				.POST(HttpRequest.BodyPublishers.ofString("{}"))
				.build();
		var resposta = HttpClient.newHttpClient().send(requisicao, HttpResponse.BodyHandlers.ofString());
		assertThat(resposta.statusCode()).isEqualTo(200);
		assertThat(recebida.get()).isEqualTo("POST /api/v1/pagamentos/mercado-pago/notificacoes?data.id=123 ts=123,v1=abc request-teste");
	}

}
