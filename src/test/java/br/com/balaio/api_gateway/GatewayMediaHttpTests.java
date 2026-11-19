package br.com.balaio.api_gateway;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static br.com.balaio.api_gateway.GatewaySecurityTestFixtures.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayMediaHttpTests {
    private static final String UPLOAD = "/api/v1/artesao/me/imagem";
    private static final String PUBLICO = "/api/v1/arquivos/perfis/00000000-0000-0000-0000-000000000001";
    private static final String BOUNDARY = "gateway-media-test";
    private static final byte[] IMAGEM = new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, (byte) 0xff, 1};
    private static final BlockingQueue<Recebida> RECEBIDAS = new LinkedBlockingQueue<>();
    private static final HttpServer BACKEND = iniciarBackend();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    @LocalServerPort int port;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("balaio.gateway.security.public-key-location", GatewaySecurityTestFixtures::publicKeyLocation);
        properties.add("balaio.gateway.security.issuer", () -> ISSUER);
        properties.add("balaio.gateway.security.audience", () -> AUDIENCE);
        properties.add("balaio.gateway.security.allowed-origins", () -> ORIGIN);
        properties.add("PROFILE_SERVICE_URL", () -> "http://localhost:" + BACKEND.getAddress().getPort());
    }
    @BeforeEach void preparar() { RECEBIDAS.clear(); }
    @AfterAll static void terminar() { BACKEND.stop(0); }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void encaminhaMultipartBinarioComBearerOuCookie(boolean cookie) throws Exception {
        String jwt = token("artesao");
        String[] headers = cookie ? new String[]{"Cookie", "balaio_sessao=" + jwt, "Origin", ORIGIN}
                : new String[]{"Authorization", "Bearer " + jwt};
        assertThat(enviar("PUT", UPLOAD, multipart(IMAGEM), false, headers).statusCode()).isEqualTo(200);
        var recebida = receber();
        assertThat(recebida.metodo()).isEqualTo("PUT");
        assertThat(recebida.autorizacao()).isEqualTo("Bearer " + jwt);
        String boundary = recebida.tipo().substring(recebida.tipo().indexOf("boundary=") + 9).split(";")[0].replace("\"", "");
        String bruto = new String(recebida.corpo(), StandardCharsets.ISO_8859_1);
        assertThat(bruto).contains("name=\"arquivo\"", "--" + boundary);
        int inicio = bruto.indexOf("\r\n\r\n") + 4;
        int fim = bruto.indexOf("\r\n--" + boundary, inicio);
        assertThat(Arrays.copyOfRange(recebida.corpo(), inicio, fim)).isEqualTo(IMAGEM);
    }

    @ParameterizedTest @ValueSource(strings = {"PUT", "DELETE"})
    void mutacoesExigemArtesaoAutenticado(String metodo) throws Exception {
        byte[] corpo = metodo.equals("PUT") ? multipart(IMAGEM) : null;
        assertThat(enviar(metodo, UPLOAD, corpo, false).statusCode()).isEqualTo(401);
        assertThat(enviar(metodo, UPLOAD, corpo, false, "Authorization", "Bearer " + token("comprador")).statusCode()).isEqualTo(403);
        assertThat(RECEBIDAS).isEmpty();
        assertThat(enviar(metodo, UPLOAD, corpo, false, "Authorization", "Bearer " + token("artesao")).statusCode()).isEqualTo(200);
        assertThat(receber().metodo()).isEqualTo(metodo);
    }

    @ParameterizedTest @ValueSource(strings = {"GET", "HEAD"})
    void encaminhaLeituraPublicaECabecalhosSemAlterarBytes(String metodo) throws Exception {
        var response = enviar(metodo, PUBLICO, null, false);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("image/png");
        assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
        assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
        assertThat(response.headers().firstValue("Content-Length")).hasValue(String.valueOf(IMAGEM.length));
        assertThat(response.body()).isEqualTo(metodo.equals("HEAD") ? new byte[0] : IMAGEM);
        assertThat(receber().autorizacao()).isNull();
    }

    @Test void recusaTamanhoDeclaradoAcimaDe3MiBAntesDoBackend() throws Exception {
        var response = enviar("PUT", UPLOAD, multipart(new byte[3 * 1024 * 1024 + 1]), false,
                "Authorization", "Bearer " + token("artesao"));
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(new String(response.body(), StandardCharsets.UTF_8)).contains("ARQUIVO_MUITO_GRANDE", "\"dados\":null");
        assertThat(RECEBIDAS).isEmpty();
    }

    @Test void encaminhaChunkedSemBufferizarParaValidacaoMultipartDoProfile() throws Exception {
        byte[] corpo = multipart(IMAGEM);
        assertThat(enviar("PUT", UPLOAD, corpo, true, "Authorization", "Bearer " + token("artesao")).statusCode()).isEqualTo(200);
        assertThat(receber().corpo()).isEqualTo(corpo);
    }

    private HttpResponse<byte[]> enviar(String metodo, String path, byte[] corpo, boolean chunked, String... headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (headers.length > 0) request.headers(headers);
        if (corpo != null) request.header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
        var publisher = corpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(corpo);
        if (chunked) publisher = HttpRequest.BodyPublishers.fromPublisher(publisher);
        return HTTP.send(request.method(metodo, publisher).build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private byte[] multipart(byte[] bytes) throws IOException {
        var out = new ByteArrayOutputStream();
        out.write(("--" + BOUNDARY + "\r\nContent-Disposition: form-data; name=\"arquivo\"; filename=\"teste.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.write(bytes); out.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }
    private Recebida receber() throws InterruptedException {
        var recebida = RECEBIDAS.poll(2, TimeUnit.SECONDS);
        assertThat(recebida).isNotNull();
        assertThat(RECEBIDAS).isEmpty();
        return recebida;
    }
    private static HttpServer iniciarBackend() {
        try {
            var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                RECEBIDAS.add(new Recebida(exchange.getRequestMethod(), exchange.getRequestHeaders().getFirst("Content-Type"),
                        exchange.getRequestHeaders().getFirst("Authorization"), exchange.getRequestBody().readAllBytes()));
                boolean leitura = exchange.getRequestURI().getPath().startsWith("/api/v1/arquivos/perfis/");
                byte[] body = leitura ? IMAGEM : "{\"dados\":{\"imagem\":\"url\"},\"erro\":null}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", leitura ? "image/png" : "application/json");
                exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.getResponseHeaders().set("Content-Length", String.valueOf(body.length));
                boolean head = exchange.getRequestMethod().equals("HEAD");
                exchange.sendResponseHeaders(200, head ? -1 : body.length);
                if (!head) exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start(); return server;
        } catch (IOException e) { throw new IllegalStateException("Backend de teste indisponivel.", e); }
    }
    private record Recebida(String metodo, String tipo, String autorizacao, byte[] corpo) { }
}
