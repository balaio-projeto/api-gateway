package br.com.balaio.api_gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.sun.net.httpserver.HttpServer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CommunityRoutingIntegrationTests {
    private static final HttpClient CLIENTE = HttpClient.newHttpClient();
    private static HttpServer community;
    private static HttpServer profile;

    @Value("${local.server.port}")
    private int portaGateway;

    @DynamicPropertySource
    static void destinos(DynamicPropertyRegistry registro) {
        iniciarServicosFalsos();
        registro.add("COMMUNITY_SERVICE_URL", () -> "http://localhost:" + community.getAddress().getPort());
        registro.add("PROFILE_SERVICE_URL", () -> "http://localhost:" + profile.getAddress().getPort());
        GatewaySecurityTestFixtures.registrarSeguranca(registro);
    }

    private static synchronized void iniciarServicosFalsos() {
        if (community != null) {
            return;
        }
        community = servicoQueSeIdentifica("community");
        profile = servicoQueSeIdentifica("profile");
    }

    private static HttpServer servicoQueSeIdentifica(String nome) {
        try {
            var servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            servidor.createContext("/", troca -> {
                byte[] corpo = (nome + ":" + troca.getRequestURI().getRawPath()).getBytes(StandardCharsets.UTF_8);
                troca.sendResponseHeaders(200, corpo.length);
                try (var saida = troca.getResponseBody()) {
                    saida.write(corpo);
                }
            });
            servidor.start();
            return servidor;
        } catch (IOException erro) {
            throw new IllegalStateException("Não foi possível iniciar o serviço falso " + nome, erro);
        }
    }

    @AfterAll
    static void encerrar() {
        if (community != null) {
            community.stop(0);
            profile.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/api/v1/estado/historicoBusca",
        "/api/v1/estado/favoritos",
        "/api/v1/artesaos/artesao-123/reputacao",
        "/api/v1/pecas/vaso/perguntas",
        "/api/v1/pedidos/abc/conversa",
        "/api/v1/videos/abc/arquivo",
        "/api/v1/admin/mediacoes/abc/decisao"
    })
    void rotasDaComunidadeChegamAoCommunity(String caminho) throws Exception {
        assertEquals("community:" + caminho, get(caminho).body());
    }

    @Test
    void perfilDoArtesaoContinuaNoProfile() throws Exception {
        assertEquals("profile:/api/v1/artesaos/artesao-123", get("/api/v1/artesaos/artesao-123").body());
    }

    private HttpResponse<String> get(String caminho) throws Exception {
        return CLIENTE.send(HttpRequest.newBuilder(URI.create("http://localhost:" + portaGateway + caminho))
                .header("Authorization", "Bearer " + GatewaySecurityTestFixtures.token("admin")).GET()
                .build(), HttpResponse.BodyHandlers.ofString());
    }
}
