package br.com.balaio.api_gateway;

import static br.com.balaio.api_gateway.GatewaySecurityTestFixtures.AUDIENCE;
import static br.com.balaio.api_gateway.GatewaySecurityTestFixtures.ORIGIN;
import static br.com.balaio.api_gateway.GatewaySecurityTestFixtures.SECOND_ORIGIN;
import static br.com.balaio.api_gateway.GatewaySecurityTestFixtures.claims;
import static br.com.balaio.api_gateway.GatewaySecurityTestFixtures.token;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTests {
    private static final BlockingQueue<ReceivedRequest> RECEIVED = new LinkedBlockingQueue<>();
    private static final String UPSTREAM_SESSION_COOKIE =
            "balaio_sessao=upstream-session; Path=/api/v1; HttpOnly; Secure; SameSite=Lax";
    private static final String UPSTREAM_VISITOR_COOKIE = "balaio_cliente=visitante:teste; Path=/; HttpOnly; SameSite=Lax";
    private static final Map<String, HttpServer> UPSTREAMS = startUpstreams();
    private static final HttpClient CLIENT = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("balaio.gateway.security.public-key-location", GatewaySecurityTestFixtures::publicKeyLocation);
        properties.add("balaio.gateway.security.issuer", () -> GatewaySecurityTestFixtures.ISSUER);
        properties.add("balaio.gateway.security.audience", () -> AUDIENCE);
        properties.add("balaio.gateway.security.allowed-origins", () -> ORIGIN + "," + SECOND_ORIGIN);
        for (String service : UPSTREAMS.keySet()) {
            properties.add(service.toUpperCase() + "_SERVICE_URL", () -> upstreamUrl(service));
        }
    }

    @BeforeEach
    void clearUpstreamRequests() { RECEIVED.clear(); }

    @AfterAll
    static void stopUpstreams() { UPSTREAMS.values().forEach(server -> server.stop(0)); }

    @Test
    void iniciaComApenasChavePublicaExternaEHealthPermanecePublico() throws Exception {
        var response = send("GET", "/actuator/health", null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("UP");
        assertThat(RECEIVED).isEmpty();
    }

    @ParameterizedTest(name = "{0} preserva {1}")
    @MethodSource("routes")
    void preservaOs45PadroesDeRotaEOsCincoDestinos(String service, String path, String role) throws Exception {
        String jwt = token(role);
        var response = send("GET", path + "?q=leao%20azul&ordem=asc&tag=a&tag=b", null,
                "Authorization", "Bearer " + jwt, "X-Request-Id", "rota-integracao");
        assertThat(response.statusCode()).isEqualTo(200);
        var request = received();
        assertThat(request.service()).isEqualTo(service);
        assertThat(request.rawPath()).isEqualTo(path);
        assertThat(request.rawQuery()).isEqualTo("q=leao%20azul&ordem=asc&tag=a&tag=b");
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.header("Authorization")).containsExactly("Bearer " + jwt);
        assertThat(request.header("X-Request-Id")).containsExactly("rota-integracao");
    }

    static Stream<Arguments> routes() {
        return Stream.of(
                route("catalog", "/api/v1/pecas", "comprador"),
                route("catalog", "/api/v1/pecas/jarra", "comprador"),
                route("catalog", "/api/v1/pecas/jarra/historico", "comprador"),
                route("catalog", "/api/v1/pecas/jarra/relacionadas", "comprador"),
                route("catalog", "/api/v1/artesaos/ana/pecas", "comprador"),
                route("catalog", "/api/v1/referencias", "comprador"),
                route("catalog", "/api/v1/arquivos/pecas/jarra.jpg", "comprador"),
                route("profile", "/api/v1/auth/contrato-futuro", "comprador"),
                route("profile", "/api/v1/usuario", "comprador"),
                route("profile", "/api/v1/usuario/preferencias", "comprador"),
                route("profile", "/api/v1/conta/senha", "comprador"),
                route("profile", "/api/v1/artesaos", "comprador"),
                route("profile", "/api/v1/artesaos/ana", "comprador"),
                route("profile", "/api/v1/artesaos/ana/configuracoes", "artesao"),
                route("profile", "/api/v1/artesao/me", "artesao"),
                route("profile", "/api/v1/artesao/selo", "artesao"),
                route("profile", "/api/v1/coletivos", "comprador"),
                route("profile", "/api/v1/coletivos/rio", "comprador"),
                route("profile", "/api/v1/admin/curadoria", "admin"),
                route("profile", "/api/v1/admin/curadoria/solicitacao", "admin"),
                route("profile", "/api/v1/estado/historicoBusca", "comprador"),
                route("commerce", "/api/v1/checkout", "comprador"),
                route("commerce", "/api/v1/fretes", "comprador"),
                route("commerce", "/api/v1/pedidos", "comprador"),
                route("commerce", "/api/v1/pedidos/123", "comprador"),
                route("commerce", "/api/v1/pedidos/123/estado", "comprador"),
                route("commerce", "/api/v1/pedidos/123/recusar", "comprador"),
                route("commerce", "/api/v1/pedidos/123/cancelar", "comprador"),
                route("commerce", "/api/v1/artesao/pedidos-pendentes", "artesao"),
                route("commerce", "/api/v1/artesao/vendas", "artesao"),
                route("commerce", "/api/v1/estado/sacola", "comprador"),
                route("community", "/api/v1/pecas/jarra/perguntas", "comprador"),
                route("community", "/api/v1/pecas/jarra/perguntas/123", "comprador"),
                route("community", "/api/v1/pedidos/123/conversa", "comprador"),
                route("community", "/api/v1/pedidos/123/avaliacao", "comprador"),
                route("community", "/api/v1/artesao/conversas", "artesao"),
                route("community", "/api/v1/videos", "comprador"),
                route("community", "/api/v1/videos/video/comentarios", "comprador"),
                route("community", "/api/v1/admin/mediacoes", "admin"),
                route("community", "/api/v1/admin/mediacoes/123", "admin"),
                route("community", "/api/v1/estado/favoritos", "comprador"),
                route("community", "/api/v1/estado/videosCurtidos", "comprador"),
                route("community", "/api/v1/estado/videosSalvos", "comprador"),
                route("events", "/api/v1/eventos", "comprador"),
                route("events", "/api/v1/eventos/feira", "comprador"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/v1/pecas", "/api/v1/pecas/jarra", "/api/v1/pecas/jarra/historico",
            "/api/v1/pecas/jarra/relacionadas", "/api/v1/pecas/jarra/perguntas",
            "/api/v1/artesaos", "/api/v1/artesaos/ana",
            "/api/v1/artesaos/ana/pecas", "/api/v1/coletivos", "/api/v1/coletivos/rio",
            "/api/v1/referencias", "/api/v1/arquivos/pecas/jarra.jpg", "/api/v1/fretes",
            "/api/v1/eventos", "/api/v1/eventos/feira", "/api/v1/videos", "/api/v1/videos/video",
            "/api/v1/videos/video/comentarios" })
    void permiteGetPublicoSemCredenciais(String path) throws Exception {
        assertThat(send("GET", path, null).statusCode()).isEqualTo(200);
        assertThat(received().header("Authorization")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({ "POST,/api/v1/eventos", "POST,/api/v1/videos", "POST,/api/v1/videos/video/comentarios",
            "PUT,/api/v1/estado/sacola", "PUT,/api/v1/estado/favoritos", "PUT,/api/v1/estado/historicoBusca",
            "PUT,/api/v1/estado/videosCurtidos", "PUT,/api/v1/estado/videosSalvos" })
    void preservaOperacoesPublicasEEstadoDeVisitante(String method, String path) throws Exception {
        var response = send(method, path, "{\"valor\":\"ação\"}", "Cookie", "balaio_cliente=visitante:123");
        assertThat(response.statusCode()).isEqualTo(200);
        var request = received();
        assertThat(request.method()).isEqualTo(method);
        assertThat(request.body()).isEqualTo("{\"valor\":\"ação\"}");
        assertThat(request.header("Cookie")).containsExactly("balaio_cliente=visitante:123");
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/v1/usuario", "/api/v1/conta/senha", "/api/v1/pedidos",
            "/api/v1/pedidos/123/conversa", "/api/v1/pedidos/123/avaliacao", "/api/v1/artesao/me",
            "/api/v1/artesaos/ana/configuracoes", "/api/v1/admin/curadoria", "/api/v1/admin/mediacoes",
            "/api/v1/auth/rota-nao-publica" })
    void exigeAutenticacaoNasRotasProtegidas(String path) throws Exception {
        assertError(send("GET", path, null), 401, "NAO_AUTENTICADO");
    }

    @ParameterizedTest
    @ValueSource(strings = { "comprador", "artesao", "organizador", "admin" })
    void aceitaTodosOsPapeisContratados(String role) throws Exception {
        assertThat(send("GET", "/api/v1/usuario", null, "Authorization", "Bearer " + token(role)).statusCode()).isEqualTo(200);
        received();
    }

    @Test
    void normalizaBearerDaSessaoRemoveIdentidadeForjadaEPreservaContratoHttp() throws Exception {
        String jwt = token("comprador");
        String body = "{\"mensagem\":\"Olá, artesão!\",\"itens\":[1,2]}";
        var response = send("POST", "/api/v1/pedidos/123/conversa?destino=ana%20silva&tag=a&tag=b", body,
                "Cookie", "balaio_cliente=visitante:abc; balaio_sessao=" + jwt + "; preferencia=azul",
                "Origin", ORIGIN, "X-User-Id", "forjado", "X-Account-Id", "forjado", "X-Role", "admin",
                "X-Papel", "admin", "X-Authenticated-User", "forjado", "X-User", "forjado",
                "Forwarded", "for=attacker;proto=https", "X-Forwarded-For", "203.0.113.99",
                "X-Forwarded-Host", "evil.example", "X-Forwarded-Proto", "https",
                "X-Correlation-Id", "correlacao-123");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().allValues("Set-Cookie")).contains(UPSTREAM_SESSION_COOKIE, UPSTREAM_VISITOR_COOKIE);
        assertThat(response.headers().firstValue("X-Upstream")).hasValue("community");
        var request = received();
        assertThat(request.service()).isEqualTo("community");
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.rawPath()).isEqualTo("/api/v1/pedidos/123/conversa");
        assertThat(request.rawQuery()).isEqualTo("destino=ana%20silva&tag=a&tag=b");
        assertThat(request.body()).isEqualTo(body);
        assertThat(request.header("Authorization")).containsExactly("Bearer " + jwt);
        assertThat(String.join("; ", request.header("Cookie")))
                .contains("balaio_cliente=visitante:abc", "preferencia=azul").doesNotContain("balaio_sessao");
        for (String name : List.of("X-User-Id", "X-Account-Id", "X-Role", "X-Papel", "X-Authenticated-User", "X-User")) {
            assertThat(request.header(name)).as(name).isEmpty();
        }
        assertThat(String.join(",", request.header("Forwarded"))).doesNotContain("attacker");
        assertThat(String.join(",", request.header("X-Forwarded-For"))).doesNotContain("203.0.113.99");
        assertThat(String.join(",", request.header("X-Forwarded-Host"))).doesNotContain("evil.example");
        assertThat(String.join(",", request.header("X-Forwarded-Proto"))).doesNotContain("https");
        assertThat(request.header("X-Correlation-Id")).containsExactly("correlacao-123");
    }

    @Test
    void aceitaBearerValidoSemOriginEmClienteApi() throws Exception {
        String jwt = token("comprador");
        assertThat(send("PATCH", "/api/v1/conta/senha", "{\"senha\":\"senha-nova\"}",
                "Authorization", "bearer " + jwt).statusCode()).isEqualTo(200);
        assertThat(received().header("Authorization")).containsExactly("Bearer " + jwt);
    }

    @Test
    void aceitaCookieEBearerIdenticosDepoisDeValidarEExigeOriginEmMutacao() throws Exception {
        String jwt = token("comprador");
        assertThat(send("PATCH", "/api/v1/conta/senha", "{}", "Authorization", "Bearer " + jwt,
                "Cookie", "balaio_sessao=" + jwt, "Origin", ORIGIN).statusCode()).isEqualTo(200);
        var request = received();
        assertThat(request.header("Authorization")).containsExactly("Bearer " + jwt);
        assertThat(request.header("Cookie")).isEmpty();
        assertError(send("PATCH", "/api/v1/conta/senha", "{}", "Authorization", "Bearer " + jwt,
                "Cookie", "balaio_sessao=" + jwt), 403, "SEM_PERMISSAO");
    }

    @Test
    void rejeitaDuasCredenciaisValidasDiferentesSemEscolherIdentidade() throws Exception {
        assertError(send("GET", "/api/v1/usuario", null, "Authorization", "Bearer " + token("comprador"),
                "Cookie", "balaio_sessao=" + token("admin")), 400, "REQUISICAO_INVALIDA");
    }

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void credencialInvalidaNaoFazFallbackParaOutraValida(boolean invalidBearer) throws Exception {
        String valid = token("comprador");
        assertError(send("GET", "/api/v1/usuario", null,
                "Authorization", "Bearer " + (invalidBearer ? "invalido" : valid),
                "Cookie", "balaio_sessao=" + (invalidBearer ? valid : "invalido")), 401, "NAO_AUTENTICADO");
    }

    @ParameterizedTest
    @MethodSource("duplicateCredentials")
    void rejeitaCredenciaisDuplicadasMesmoQuandoIguais(String[] headers) throws Exception {
        assertError(send("GET", "/api/v1/usuario", null, headers), 400, "REQUISICAO_INVALIDA");
    }

    static Stream<Arguments> duplicateCredentials() {
        String jwt = token("comprador");
        return Stream.of(
                Arguments.of((Object) new String[] { "Authorization", "Bearer " + jwt, "Authorization", "Bearer " + jwt }),
                Arguments.of((Object) new String[] { "Authorization", "Bearer " + jwt + ", Bearer " + jwt }),
                Arguments.of((Object) new String[] { "Cookie", "balaio_sessao=" + jwt + "; balaio_sessao=" + jwt }),
                Arguments.of((Object) new String[] { "Cookie", "balaio_sessao=" + jwt, "Cookie", "balaio_sessao=" + jwt }));
    }

    @ParameterizedTest(name = "JWT invalido: {0}")
    @MethodSource("invalidTokens")
    void rejeitaJwtInvalidoSemEncaminhar(String reason, String jwt) throws Exception {
        assertError(send("GET", "/api/v1/usuario", null, "Authorization", "Bearer " + jwt), 401, "NAO_AUTENTICADO");
    }

    static Stream<Arguments> invalidTokens() {
        List<Arguments> cases = new ArrayList<>();
        cases.add(Arguments.of("malformado", "abc.def.ghi"));
        cases.add(Arguments.of("sem assinatura", GatewaySecurityTestFixtures.unsignedToken(claims("comprador"))));
        cases.add(Arguments.of("HS256", GatewaySecurityTestFixtures.hs256Token(claims("comprador"))));
        cases.add(Arguments.of("RS512", GatewaySecurityTestFixtures.rs512Token(claims("comprador"))));
        cases.add(Arguments.of("outra chave", GatewaySecurityTestFixtures.otherKeyToken(claims("comprador"))));
        String valid = token("comprador");
        int signature = valid.lastIndexOf('.') + 1;
        cases.add(Arguments.of("assinatura adulterada", valid.substring(0, signature)
                + (valid.charAt(signature) == 'A' ? 'B' : 'A') + valid.substring(signature + 1)));
        for (String name : List.of("sub", "papel", "iss", "aud", "iat", "exp", "jti")) {
            Map<String, Object> missing = claims("comprador");
            missing.remove(name);
            cases.add(Arguments.of("ausente " + name, token(missing)));
        }
        addInvalidClaim(cases, "sub", "1-1-1-1-1");
        addInvalidClaim(cases, "sub", GatewaySecurityTestFixtures.SUBJECT.toUpperCase());
        addInvalidClaim(cases, "sub", 42);
        addInvalidClaim(cases, "papel", "superadmin");
        addInvalidClaim(cases, "papel", "ADMIN");
        addInvalidClaim(cases, "papel", List.of("admin"));
        addInvalidClaim(cases, "iss", "https://attacker.example");
        addInvalidClaim(cases, "iss", List.of(GatewaySecurityTestFixtures.ISSUER));
        addInvalidClaim(cases, "aud", List.of("outro-servico"));
        addInvalidClaim(cases, "aud", 123);
        addInvalidClaim(cases, "exp", Instant.now().getEpochSecond() - 1);
        addInvalidClaim(cases, "exp", "amanha");
        addInvalidClaim(cases, "exp", Instant.now().getEpochSecond() + 9000);
        addInvalidClaim(cases, "iat", Instant.now().getEpochSecond() + 120);
        addInvalidClaim(cases, "iat", "ontem");
        addInvalidClaim(cases, "iat", Instant.now().getEpochSecond() - 10000);
        addInvalidClaim(cases, "iat", Instant.now().getEpochSecond() - 5.5);
        addInvalidClaim(cases, "jti", "");
        addInvalidClaim(cases, "jti", "   ");
        addInvalidClaim(cases, "jti", 42);
        addInvalidClaim(cases, "nbf", Instant.now().getEpochSecond() + 120);
        addInvalidClaim(cases, "nbf", "ontem");
        String raw = new com.nimbusds.jose.Payload(claims("comprador")).toString();
        cases.add(Arguments.of("claim papel duplicada", GatewaySecurityTestFixtures.rawToken(
                raw.substring(0, raw.length() - 1) + ",\"papel\":\"admin\"}")));
        cases.add(Arguments.of("payload array", GatewaySecurityTestFixtures.rawToken("[]")));
        return cases.stream();
    }

    private static void addInvalidClaim(List<Arguments> cases, String name, Object value) {
        var changed = claims("comprador");
        changed.put(name, value);
        cases.add(Arguments.of(name + "=" + value, token(changed)));
    }

    @Test
    void aceitaJtiNaoVazioSemExigirUuid() throws Exception {
        var claims = claims("comprador");
        claims.put("jti", "identificador-opaco-do-emissor");
        assertThat(send("GET", "/api/v1/usuario", null, "Authorization", "Bearer " + token(claims)).statusCode()).isEqualTo(200);
        received();
    }

    @ParameterizedTest
    @ValueSource(strings = { "Basic dXNlcjpwYXNz", "Bearer", "Bearer ", "Bearer a b", "Digest abc" })
    void rejeitaEsquemaOuEstruturaAuthorizationInvalidos(String authorization) throws Exception {
        assertError(send("GET", "/api/v1/usuario", null, "Authorization", authorization), 401, "NAO_AUTENTICADO");
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/v1/pecas", "/api/v1/eventos", "/api/v1/videos" })
    void tokenInvalidoTambemERejeitadoEmRecursoPublico(String path) throws Exception {
        assertError(send("GET", path, null, "Authorization", "Bearer invalido"), 401, "NAO_AUTENTICADO");
    }

    @ParameterizedTest
    @ValueSource(strings = { "cadastro", "login", "entrar", "recuperacao", "logout" })
    void authPublicoIgnoraCredenciaisAntigasEPassaRespostaDoProfile(String operation) throws Exception {
        var response = send("POST", "/api/v1/auth/" + operation, "{\"email\":\"pessoa@example.test\"}",
                "Authorization", "Bearer expirado", "Cookie", "balaio_sessao=invalido; balaio_cliente=visitante:123", "Origin", ORIGIN);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().allValues("Set-Cookie")).contains(UPSTREAM_SESSION_COOKIE, UPSTREAM_VISITOR_COOKIE);
        var request = received();
        assertThat(request.service()).isEqualTo("profile");
        assertThat(request.header("Authorization")).isEmpty();
        assertThat(request.header("Cookie")).containsExactly("balaio_cliente=visitante:123");
    }

    @Test
    void logoutSemSessaoSemOriginContinuaIdempotente() throws Exception {
        assertThat(send("POST", "/api/v1/auth/logout", null).statusCode()).isEqualTo(200);
        assertThat(received().service()).isEqualTo("profile");
    }

    @ParameterizedTest
    @ValueSource(strings = { "login", "logout" })
    void authPermiteCookiesLegadosDuplicadosMasNaoOsEncaminha(String operation) throws Exception {
        var expired = claims("comprador");
        expired.put("iat", Instant.now().getEpochSecond() - 4000);
        expired.put("exp", Instant.now().getEpochSecond() - 400);
        var response = send("POST", "/api/v1/auth/" + operation, "{}", "Origin", ORIGIN,
                "Cookie", "balaio_sessao=" + token(expired) + "; balaio_sessao=adulterado; balaio_cliente=visitante:123",
                "Authorization", "Bearer adulterado", "Authorization", "Bearer outro-adulterado");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().allValues("Set-Cookie")).contains(UPSTREAM_SESSION_COOKIE, UPSTREAM_VISITOR_COOKIE);
        var request = received();
        assertThat(request.header("Authorization")).isEmpty();
        assertThat(request.header("Cookie")).containsExactly("balaio_cliente=visitante:123");
    }

    @ParameterizedTest
    @ValueSource(strings = { "cadastro", "login", "entrar", "recuperacao", "logout" })
    void authComCookieExigeOriginAntesDeRemoverCookie(String operation) throws Exception {
        assertError(send("POST", "/api/v1/auth/" + operation, "{}", "Cookie", "balaio_sessao=invalido"), 403, "SEM_PERMISSAO");
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://attacker.example", "null", "https://frontend.example.test.attacker.example", "https://frontend.example.test/" })
    void rejeitaOriginNaoAutorizadoMesmoEmGetPublico(String origin) throws Exception {
        assertError(send("GET", "/api/v1/pecas", null, "Origin", origin), 403, "SEM_PERMISSAO");
    }

    @Test
    void rejeitaOriginDuplicado() throws Exception {
        assertError(send("GET", "/api/v1/pecas", null, "Origin", ORIGIN, "Origin", ORIGIN), 403, "SEM_PERMISSAO");
    }

    @ParameterizedTest
    @ValueSource(strings = { ORIGIN, SECOND_ORIGIN })
    void corsPermiteSomenteOrigemConcretaComCredenciais(String origin) throws Exception {
        var response = send("GET", "/api/v1/pecas", null, "Origin", origin);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).hasValue(origin);
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).hasValue("true");
        assertThat(String.join(",", response.headers().allValues("Vary"))).containsIgnoringCase("Origin");
        received();
    }

    @Test
    void corsDaBordaPrevaleceSobreHeadersPermissivosDoUpstream() throws Exception {
        var browser = send("GET", "/api/v1/pecas/cors-upstream", null, "Origin", ORIGIN);
        assertThat(browser.statusCode()).isEqualTo(200);
        assertThat(browser.headers().allValues("Access-Control-Allow-Origin")).containsExactly(ORIGIN);
        assertThat(browser.headers().allValues("Access-Control-Allow-Credentials")).containsExactly("true");
        assertThat(String.join(",", browser.headers().allValues("Vary"))).containsIgnoringCase("Origin");
        received();

        var clientWithoutOrigin = send("GET", "/api/v1/pecas/cors-upstream", null);
        assertThat(clientWithoutOrigin.statusCode()).isEqualTo(200);
        assertThat(clientWithoutOrigin.headers().allValues("Access-Control-Allow-Origin")).isEmpty();
        assertThat(clientWithoutOrigin.headers().allValues("Access-Control-Allow-Credentials")).isEmpty();
        assertThat(String.join(",", clientWithoutOrigin.headers().allValues("Vary"))).containsIgnoringCase("Origin");
        received();
    }

    @Test
    void preflightPermitidoDispensaJwtENaoAlcancaUpstream() throws Exception {
        var response = send("OPTIONS", "/api/v1/conta/senha", null, "Origin", ORIGIN,
                "Access-Control-Request-Method", "PATCH", "Access-Control-Request-Headers", "authorization,content-type");
        assertThat(response.statusCode()).isBetween(200, 299);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).hasValue(ORIGIN);
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials")).hasValue("true");
        assertThat(String.join(",", response.headers().allValues("Access-Control-Allow-Methods"))).contains("PATCH");
        assertThat(String.join(",", response.headers().allValues("Access-Control-Allow-Headers")))
                .containsIgnoringCase("authorization").containsIgnoringCase("content-type");
        assertThat(RECEIVED).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({ "https://attacker.example,PATCH,authorization", "null,PATCH,authorization",
            "https://frontend.example.test,TRACE,authorization", "https://frontend.example.test,PATCH,x-evil-header" })
    void preflightRejeitadoUsaEnvelopeENaoAlcancaUpstream(String origin, String method, String headers) throws Exception {
        assertError(send("OPTIONS", "/api/v1/conta/senha", null, "Origin", origin,
                "Access-Control-Request-Method", method, "Access-Control-Request-Headers", headers), 403, "SEM_PERMISSAO");
    }

    @ParameterizedTest
    @ValueSource(strings = { "POST", "PUT", "PATCH", "DELETE" })
    void todaMutacaoComCookieDeSessaoExigeOriginPermitido(String method) throws Exception {
        assertError(send(method, "/api/v1/usuario", "{}", "Cookie", "balaio_sessao=" + token("comprador")), 403, "SEM_PERMISSAO");
    }

    @ParameterizedTest
    @CsvSource({ "GET,/api/v1/artesao/me", "GET,/api/v1/artesaos/ana/configuracoes", "POST,/api/v1/pecas",
            "PATCH,/api/v1/pecas/jarra", "DELETE,/api/v1/pecas/jarra", "PATCH,/api/v1/pecas/jarra/perguntas/123",
            "PATCH,/api/v1/pedidos/123/estado", "PATCH,/api/v1/pedidos/123/recusar" })
    void operacoesDoAtelieExigemArtesao(String method, String path) throws Exception {
        assertError(send(method, path, "{}", "Authorization", "Bearer " + token("comprador")), 403, "SEM_PERMISSAO");
        assertThat(send(method, path, "{}", "Authorization", "Bearer " + token("artesao")).statusCode()).isEqualTo(200);
        received();
    }

    @ParameterizedTest
    @ValueSource(strings = { "artesao", "admin" })
    void alteracaoDePerfilDoArtesaoPermiteArtesaoOuAdmin(String role) throws Exception {
        assertThat(send("PATCH", "/api/v1/artesaos/ana", "{}", "Authorization", "Bearer " + token(role)).statusCode()).isEqualTo(200);
        received();
    }

    @ParameterizedTest
    @CsvSource({ "GET,/api/v1/admin/curadoria", "GET,/api/v1/admin/curadoria/123", "GET,/api/v1/admin/mediacoes", "PATCH,/api/v1/admin/mediacoes/123" })
    void administracaoExigeAdmin(String method, String path) throws Exception {
        assertError(send(method, path, "{}", "Authorization", "Bearer " + token("comprador")), 403, "SEM_PERMISSAO");
        assertThat(send(method, path, "{}", "Authorization", "Bearer " + token("admin")).statusCode()).isEqualTo(200);
        received();
    }

    @Test
    void compradorPodeAbrirMediacaoSemSerAdministrador() throws Exception {
        assertError(send("POST", "/api/v1/admin/mediacoes", "{}"), 401, "NAO_AUTENTICADO");
        assertThat(send("POST", "/api/v1/admin/mediacoes", "{}", "Authorization", "Bearer " + token("comprador")).statusCode()).isEqualTo(200);
        assertThat(received().service()).isEqualTo("community");
    }

    @ParameterizedTest
    @ValueSource(strings = { "?painel=true", "?painel=false&painel=true", "?painel=true&painel=false" })
    void painelDeVideosExigeArtesaoMesmoComQueryDuplicada(String query) throws Exception {
        assertError(send("GET", "/api/v1/videos" + query, null), 401, "NAO_AUTENTICADO");
        assertError(send("GET", "/api/v1/videos" + query, null, "Authorization", "Bearer " + token("comprador")), 403, "SEM_PERMISSAO");
        assertThat(send("GET", "/api/v1/videos" + query, null, "Authorization", "Bearer " + token("artesao")).statusCode()).isEqualTo(200);
        received();
    }

    @ParameterizedTest
    @ValueSource(strings = { "/api/v1/%75suario", "/api/v1/usuario;publico=true", "/api/v1/pecas/../usuario",
            "/api/v1/pecas/%2e%2e/usuario", "/api/v1/artesaos/ana%2fconfiguracoes" })
    void caminhoNaoCanonicoNaoPodeEvitarPoliticaDeAcesso(String path) throws Exception {
        assertThat(send("GET", path, null).statusCode()).isIn(400, 401, 403, 404);
        assertThat(RECEIVED).isEmpty();
    }

    @Test
    void rotaEstadoCompostaPermaneceSemEncaminhamento() throws Exception {
        assertThat(send("GET", "/api/v1/estado", null, "Authorization", "Bearer " + token("comprador")).statusCode()).isEqualTo(404);
        assertThat(RECEIVED).isEmpty();
    }

    private HttpResponse<String> send(String method, String path, String body, String... headers) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).timeout(Duration.ofSeconds(10));
        if (headers.length > 0) { builder.headers(headers); }
        if (body != null) { builder.header("Content-Type", "application/json; charset=UTF-8"); }
        return CLIENT.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(response.body().replaceAll("\\s+", ""))
                .contains("\"dados\":null", "\"erro\":{", "\"codigo\":\"" + code + "\"", "\"mensagem\":");
        assertThat(response.body()).doesNotContain("eyJ", "PRIVATE KEY", "stackTrace", "java.lang.");
        assertThat(RECEIVED).isEmpty();
    }

    private static ReceivedRequest received() throws InterruptedException {
        ReceivedRequest request = RECEIVED.poll(2, TimeUnit.SECONDS);
        assertThat(request).as("requisicao recebida no servidor HTTP upstream real").isNotNull();
        assertThat(RECEIVED).isEmpty();
        return request;
    }

    private static Arguments route(String service, String path, String role) { return Arguments.of(service, path, role); }
    private static String upstreamUrl(String service) { return "http://localhost:" + UPSTREAMS.get(service).getAddress().getPort(); }

    private static Map<String, HttpServer> startUpstreams() {
        Map<String, HttpServer> servers = new LinkedHashMap<>();
        try {
            for (String service : List.of("catalog", "profile", "commerce", "community", "events")) {
                HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                server.createContext("/", exchange -> {
                    Map<String, List<String>> headers = new LinkedHashMap<>();
                    exchange.getRequestHeaders().forEach((key, values) -> headers.put(key, List.copyOf(values)));
                    RECEIVED.add(new ReceivedRequest(service, exchange.getRequestMethod(),
                            exchange.getRequestURI().getRawPath(), exchange.getRequestURI().getRawQuery(), headers,
                            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                    byte[] body = ("{\"dados\":{\"servico\":\"" + service + "\"},\"erro\":null}").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
                    exchange.getResponseHeaders().add("X-Upstream", service);
                    exchange.getResponseHeaders().add("Set-Cookie", UPSTREAM_SESSION_COOKIE);
                    exchange.getResponseHeaders().add("Set-Cookie", UPSTREAM_VISITOR_COOKIE);
                    if (exchange.getRequestURI().getRawPath().equals("/api/v1/pecas/cors-upstream")) {
                        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                        exchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
                    }
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
                server.start();
                servers.put(service, server);
            }
            return servers;
        } catch (IOException exception) {
            servers.values().forEach(server -> server.stop(0));
            throw new IllegalStateException("Nao foi possivel iniciar upstreams HTTP de teste.", exception);
        }
    }

    private record ReceivedRequest(String service, String method, String rawPath, String rawQuery,
            Map<String, List<String>> headers, String body) {
        List<String> header(String name) {
            return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
                    .flatMap(entry -> entry.getValue().stream()).toList();
        }
    }
}
