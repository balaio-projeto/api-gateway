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
        destino.createContext("/api/v1/estado/sacola", requisicao -> {
            var corpo = new String(requisicao.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            recebida.set(requisicao.getRequestMethod() + " " + requisicao.getRequestURI() + " "
                    + requisicao.getRequestHeaders().getFirst("Authorization") + " " + corpo);
            var resposta = "{\"dados\":[{\"slug\":\"peca-teste\",\"quantidade\":1}],\"erro\":null}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            requisicao.getResponseHeaders().set("Content-Type", "application/json");
            requisicao.sendResponseHeaders(200, resposta.length);
            requisicao.getResponseBody().write(resposta);
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

    @Test
    void encaminhaLeituraEEscritaDaSacolaSemCapturarEstadoAgregado() throws Exception {
        var cliente = HttpClient.newHttpClient();
        var uri = URI.create("http://127.0.0.1:" + porta + "/api/v1/estado/sacola");
        var get = cliente.send(HttpRequest.newBuilder(uri).header("Authorization", "Bearer comprador-a")
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(get.statusCode()).isEqualTo(200);
        assertThat(get.body()).contains("peca-teste", "\"erro\":null");
        assertThat(recebida.get()).isEqualTo("GET /api/v1/estado/sacola Bearer comprador-a ");
        var put = cliente.send(HttpRequest.newBuilder(uri).header("Authorization", "Bearer comprador-b")
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{\"sacola\":[{\"slug\":\"peca-teste\",\"quantidade\":1}]}"))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(put.statusCode()).isEqualTo(200);
        assertThat(put.body()).contains("peca-teste", "\"erro\":null");
        assertThat(recebida.get()).contains("PUT /api/v1/estado/sacola Bearer comprador-b {\"sacola\":");
        var agregado = cliente.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + "/api/v1/estado"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(agregado.statusCode()).isEqualTo(404);
        assertThat(recebida.get()).contains("PUT /api/v1/estado/sacola Bearer comprador-b");
    }

}
