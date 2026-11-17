package br.com.balaio.api_gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import br.com.balaio.api_gateway.security.GatewaySecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class GatewaySecurityConfigurationTests {
    private static final String PREFIX = "balaio.gateway.security.";

    @TempDir
    Path temporary;

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void iniciaSomenteComPublicaRsaExternaPorPathOuFileUri(boolean fileUri) {
        Map<String, String> values = properties();
        values.put("public-key-location", fileUri ? GatewaySecurityTestFixtures.publicKeyLocation()
                : GatewaySecurityTestFixtures.publicKeyPath());
        context(values).run(application -> assertThat(application).hasNotFailed().hasSingleBean(NimbusJwtDecoder.class));
    }

    @ParameterizedTest
    @ValueSource(strings = { "public-key-location", "issuer", "audience", "allowed-origins" })
    void falhaAoIniciarSemCadaConfiguracaoObrigatoria(String missing) {
        Map<String, String> values = properties();
        values.remove(missing);
        context(values).run(application -> assertThat(application).hasFailed());
    }

    @ParameterizedTest
    @MethodSource("invalidOrigins")
    void falhaAoIniciarComOrigensNaoConcretas(String origin) {
        Map<String, String> values = properties();
        values.put("allowed-origins", origin);
        context(values).run(application -> assertThat(application).hasFailed());
    }

    static Stream<String> invalidOrigins() {
        return Stream.of("", "*", "https://*.example.test", "null", "ftp://frontend.example.test",
                "https://frontend.example.test/", "https://frontend.example.test/path",
                "https://frontend.example.test?query=1", "https://frontend.example.test#fragment",
                "https://pessoa@frontend.example.test", "https://frontend.example.test:0",
                "https://frontend.example.test:65536", GatewaySecurityTestFixtures.ORIGIN + ",*");
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://example.test/public.pem", "classpath:public.pem", "file://remotehost/share/public.pem",
            "\\\\remotehost\\share\\public.pem", "-----BEGIN PUBLIC KEY-----inline" })
    void naoBuscaChaveEmUrlClasspathUncOuPemInline(String location) {
        assertInvalidKeyLocation(location);
    }

    @Test
    void rejeitaArquivoAusenteDiretorioEUriComQuery() {
        assertInvalidKeyLocation(temporary.resolve("missing.pem").toString());
        assertInvalidKeyLocation(temporary.toString());
        assertInvalidKeyLocation(GatewaySecurityTestFixtures.publicKeyLocation() + "?versao=1");
    }

    @Test
    void rejeitaPemDoTipoPrivadoSemNuncaGravarChavePrivada() throws Exception {
        // O conteudo continua sendo publico: somente o rotulo PEM esta deliberadamente incorreto.
        Path mislabeled = temporary.resolve("public-material-with-wrong-label.pem");
        String content = GatewaySecurityTestFixtures.publicPem(GatewaySecurityTestFixtures.publicKeyBytes())
                .replace("PUBLIC KEY", "PRIVATE KEY");
        Files.writeString(mislabeled, content, StandardCharsets.US_ASCII);
        assertInvalidKeyLocation(mislabeled.toString());
    }

    @Test
    void rejeitaRsa2048PemInvalidoEVazio() throws Exception {
        Path weak = temporary.resolve("weak-public.pem");
        Files.writeString(weak, GatewaySecurityTestFixtures.publicPem(
                GatewaySecurityTestFixtures.rsa(2048).getPublic().getEncoded()), StandardCharsets.US_ASCII);
        assertInvalidKeyLocation(weak.toString());
        Path malformed = temporary.resolve("malformed-public.pem");
        Files.writeString(malformed, "-----BEGIN PUBLIC KEY-----\nnot-a-key\n-----END PUBLIC KEY-----\n");
        assertInvalidKeyLocation(malformed.toString());
        Path empty = temporary.resolve("empty-public.pem");
        Files.writeString(empty, "");
        assertInvalidKeyLocation(empty.toString());
    }

    private void assertInvalidKeyLocation(String location) {
        Map<String, String> values = properties();
        values.put("public-key-location", location);
        context(values).run(application -> assertThat(application).hasFailed());
    }

    private static ApplicationContextRunner context(Map<String, String> values) {
        return new ApplicationContextRunner().withUserConfiguration(GatewaySecurityConfiguration.class)
                .withPropertyValues(values.entrySet().stream()
                        .map(entry -> PREFIX + entry.getKey() + "=" + entry.getValue()).toArray(String[]::new));
    }

    private static Map<String, String> properties() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("public-key-location", GatewaySecurityTestFixtures.publicKeyLocation());
        values.put("issuer", GatewaySecurityTestFixtures.ISSUER);
        values.put("audience", GatewaySecurityTestFixtures.AUDIENCE);
        values.put("allowed-origins", GatewaySecurityTestFixtures.ORIGIN);
        return values;
    }
}
