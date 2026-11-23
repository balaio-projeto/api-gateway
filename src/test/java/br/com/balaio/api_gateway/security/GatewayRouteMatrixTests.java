package br.com.balaio.api_gateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.AntPathMatcher;
import org.yaml.snakeyaml.Yaml;

/**
 * Garante que a matriz versionada em docs/matriz-rotas.csv, as rotas de application.yaml e a
 * GatewayAccessPolicy continuem coerentes. Uma rota nova sem linha na matriz, ou uma linha cujo
 * acesso mudou sem atualizar a matriz, falha aqui.
 */
class GatewayRouteMatrixTests {
    private static final Path ROUTES = Path.of("src", "main", "resources", "application.yaml");
    private static final Path MATRIX = Path.of("docs", "matriz-rotas.csv");
    private static final AntPathMatcher PATHS = new AntPathMatcher();
    private static final String CABECALHO = "metodo,caminho,servico,acesso,tela,card";
    private static final Pattern TELA = Pattern.compile("-|menu da conta|/[\\w\\[\\]/-]*(;/[\\w\\[\\]/-]*)*");
    private static final Pattern CARD = Pattern.compile("PI-\\d+(;PI-\\d+)*");

    @Test
    void cadaLinhaDaMatrizTemOAcessoDecididoPelaPolitica() throws IOException {
        var policy = new GatewayAccessPolicy();
        for (Row row : rows()) {
            var request = new MockHttpServletRequest(row.method(), row.path());
            var decided = policy.access(request, row.path());
            assertThat(decided.name())
                    .as("acesso de %s %s (servico %s)", row.method(), row.path(), row.servico())
                    .isEqualTo(row.acesso());
        }
    }

    @Test
    void cadaLinhaDaMatrizPertenceAUmaRotaDoServicoIndicado() throws IOException {
        var routes = routes();
        for (Row row : rows()) {
            var matching = routes.stream()
                    .filter(route -> route.matches(row.method(), row.path()))
                    .map(Route::id)
                    .toList();
            assertThat(matching)
                    .as("rota de %s %s", row.method(), row.path())
                    .contains(row.servico());
        }
    }

    @Test
    void cadaLinhaDaMatrizTemUmUnicoDonoDeRota() throws IOException {
        // Conflito de prefixo: duas rotas casando a mesma linha deixariam o dono ambíguo.
        var routes = routes();
        for (Row row : rows()) {
            var matching = routes.stream()
                    .filter(route -> route.matches(row.method(), row.path()))
                    .map(Route::id)
                    .toList();
            assertThat(matching)
                    .as("donos de %s %s", row.method(), row.path())
                    .hasSize(1);
        }
    }

    @Test
    void matrizTemCabecalhoComTelaECard() throws IOException {
        assertThat(Files.readAllLines(MATRIX, StandardCharsets.UTF_8).getFirst()).isEqualTo(CABECALHO);
    }

    @Test
    void cadaLinhaDaMatrizIndicaTelaDoFrontECardDoJira() throws IOException {
        for (Row row : rows()) {
            assertThat(row.tela())
                    .as("tela de %s %s", row.method(), row.path())
                    .matches(TELA);
            assertThat(row.card())
                    .as("card de %s %s", row.method(), row.path())
                    .matches(CARD);
        }
    }

    @Test
    void cadaRotaDoApplicationYamlTemPeloMenosUmaLinhaNaMatriz() throws IOException {
        var rows = rows();
        for (Route route : routes()) {
            boolean covered = route.patterns().stream().anyMatch(pattern -> rows.stream()
                    .anyMatch(row -> route.allowsMethod(row.method()) && PATHS.match(pattern, row.path())));
            assertThat(covered)
                    .as("rota %s (%s) sem linha na matriz", route.id(), route.patterns())
                    .isTrue();
        }
    }

    private static List<Row> rows() throws IOException {
        List<Row> rows = new ArrayList<>();
        for (String line : Files.readAllLines(MATRIX, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("metodo,")) {
                continue;
            }
            String[] columns = line.split(",", -1);
            assertThat(columns).as("linha da matriz: %s", line).hasSize(6);
            rows.add(new Row(columns[0].trim(), columns[1].trim(), columns[2].trim(), columns[3].trim(),
                    columns[4].trim(), columns[5].trim()));
        }
        assertThat(rows).isNotEmpty();
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static List<Route> routes() throws IOException {
        Map<String, Object> root;
        try (InputStream input = Files.newInputStream(ROUTES)) {
            root = new Yaml().load(input);
        }
        var spring = (Map<String, Object>) root.get("spring");
        var cloud = (Map<String, Object>) spring.get("cloud");
        var gateway = (Map<String, Object>) cloud.get("gateway");
        var server = (Map<String, Object>) gateway.get("server");
        var webmvc = (Map<String, Object>) server.get("webmvc");
        List<Route> routes = new ArrayList<>();
        for (Object item : (List<Object>) webmvc.get("routes")) {
            var definition = (Map<String, Object>) item;
            List<String> patterns = new ArrayList<>();
            Set<String> methods = null;
            for (String predicate : (List<String>) definition.get("predicates")) {
                if (predicate.startsWith("Path=")) {
                    for (String pattern : predicate.substring("Path=".length()).split(",")) {
                        patterns.add(pattern.trim());
                    }
                } else if (predicate.startsWith("Method=")) {
                    methods = Set.of(predicate.substring("Method=".length()).split(","));
                }
            }
            routes.add(new Route((String) definition.get("id"), patterns, methods));
        }
        return routes;
    }

    private record Row(String method, String path, String servico, String acesso, String tela, String card) {
    }

    private record Route(String id, List<String> patterns, Set<String> methods) {
        boolean allowsMethod(String method) {
            return methods == null || methods.contains(method);
        }

        boolean matches(String method, String path) {
            return allowsMethod(method) && patterns.stream().anyMatch(pattern -> PATHS.match(pattern, path));
        }
    }
}
