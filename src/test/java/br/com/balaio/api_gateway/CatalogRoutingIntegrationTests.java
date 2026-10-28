package br.com.balaio.api_gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogRoutingIntegrationTests {

    private static final AtomicInteger catalogRequests = new AtomicInteger();
    private static final HttpClient client = HttpClient.newHttpClient();
    private static HttpServer catalog;

    @Value("${local.server.port}")
    private int gatewayPort;

    @DynamicPropertySource
    static void catalogDestination(DynamicPropertyRegistry registry) {
        ensureCatalogStarted();
        registry.add("CATALOG_SERVICE_URL", () -> "http://localhost:" + catalog.getAddress().getPort());
    }

    private static synchronized void ensureCatalogStarted() {
        if (catalog != null) return;
        try {
            catalog = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Could not start mock catalog", e);
        }
        catalog.createContext("/", exchange -> {
            catalogRequests.incrementAndGet();
            String path = exchange.getRequestURI().getRawPath();
            String query = exchange.getRequestURI().getRawQuery();
            if (!"/api/v1/pecas".equals(path)) {
                respond(exchange, 404, "{\"dados\":null,\"erro\":{\"codigo\":\"RECURSO_NAO_ENCONTRADO\"}}");
            } else if (query != null && query.contains("erro=400")) {
                respond(exchange, 400, "{\"dados\":null,\"erro\":{\"codigo\":\"REQUISICAO_INVALIDA\"}}");
            } else if (query != null && query.contains("erro=500")) {
                respond(exchange, 500, "{\"dados\":null,\"erro\":{\"codigo\":\"ERRO_INTERNO\"}}");
            } else {
                respond(exchange, 200, "{\"dados\":[],\"erro\":null,\"paginacao\":{\"pagina\":1,\"tamanho\":9,\"total\":0,\"totalPaginas\":0},\"queryRecebida\":\"" + (query == null ? "" : query) + "\"}");
            }
        });
        catalog.start();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    @AfterAll
    static void shutdown() {
        if (catalog != null) catalog.stop(0);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + gatewayPort + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void forwardsSuccessfulCatalogResponseWithoutChangingBody() throws Exception {
        var response = get("/api/v1/pecas");
        assertEquals(200, response.statusCode());
        assertEquals("{\"dados\":[],\"erro\":null,\"paginacao\":{\"pagina\":1,\"tamanho\":9,\"total\":0,\"totalPaginas\":0},\"queryRecebida\":\"\"}", response.body());
    }

    @Test
    void forwardsQueryParametersWithoutLoss() throws Exception {
        var response = get("/api/v1/pecas?q=barro&pagina=2&tamanho=9");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"queryRecebida\":\"q=barro&pagina=2&tamanho=9\""));
    }

    @Test
    void propagatesClientAndServerErrors() throws Exception {
        var badRequest = get("/api/v1/pecas?erro=400");
        assertEquals(400, badRequest.statusCode());
        assertEquals("{\"dados\":null,\"erro\":{\"codigo\":\"REQUISICAO_INVALIDA\"}}", badRequest.body());
        var serverError = get("/api/v1/pecas?erro=500");
        assertEquals(500, serverError.statusCode());
        assertEquals("{\"dados\":null,\"erro\":{\"codigo\":\"ERRO_INTERNO\"}}", serverError.body());
    }

    @Test
    void unrelatedRouteIsNotSentToCatalog() throws Exception {
        int before = catalogRequests.get();
        get("/api/v1/rota-que-nao-existe");
        assertEquals(before, catalogRequests.get());
    }
}
