package br.com.balaio.api_gateway.security;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("balaio.gateway.security")
public record GatewaySecurityProperties(String publicKeyLocation, String issuer, String audience,
        List<String> allowedOrigins) {

    public GatewaySecurityProperties {
        if (blank(publicKeyLocation) || blank(issuer) || blank(audience) || allowedOrigins == null
                || allowedOrigins.isEmpty() || allowedOrigins.stream().anyMatch(origin -> !validOrigin(origin))) {
            throw invalidConfiguration();
        }
        allowedOrigins = List.copyOf(allowedOrigins);
    }

    public Set<String> origins() {
        return Set.copyOf(allowedOrigins);
    }

    static IllegalStateException invalidConfiguration() {
        return new IllegalStateException("Configuração de segurança do gateway inválida.");
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean validOrigin(String origin) {
        if (blank(origin) || !origin.equals(origin.strip()) || origin.contains("*")) {
            return false;
        }
        try {
            URI uri = URI.create(origin);
            return ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null
                    && uri.getRawPath().isEmpty() && uri.getRawQuery() == null && uri.getRawFragment() == null
                    && uri.getPort() >= -1 && uri.getPort() != 0 && uri.getPort() <= 65535
                    && !uri.getRawAuthority().endsWith(":");
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
