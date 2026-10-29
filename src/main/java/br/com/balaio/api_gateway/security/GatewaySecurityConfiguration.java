package br.com.balaio.api_gateway.security;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewaySecurityProperties.class)
public class GatewaySecurityConfiguration {
    private static final Pattern PUBLIC_PEM = Pattern.compile(
            "\\A\\s*-----BEGIN PUBLIC KEY-----\\R([A-Za-z0-9+/=\\r\\n]+)-----END PUBLIC KEY-----\\s*\\z");

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock gatewayClock() {
        return Clock.systemUTC();
    }

    @Bean
    NimbusJwtDecoder gatewayJwtDecoder(GatewaySecurityProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(readPublicKey(properties.publicKeyLocation()))
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(new GatewayJwtValidator(properties, clock));
        return decoder;
    }

    @Bean
    FilterRegistrationBean<GatewayCorsFilter> gatewayCorsFilter(GatewaySecurityProperties properties) {
        var registration = new FilterRegistrationBean<>(new GatewayCorsFilter(properties));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    FilterRegistrationBean<GatewayAuthenticationFilter> gatewayAuthenticationFilter(NimbusJwtDecoder decoder) {
        var registration = new FilterRegistrationBean<>(new GatewayAuthenticationFilter(decoder));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    private RSAPublicKey readPublicKey(String location) {
        try {
            // Only a filesystem path or a local file URI is accepted; never resolve resources or URLs.
            if (location.contains("-----") || location.contains("\n") || location.contains("\r")
                    || location.startsWith("\\\\") || location.startsWith("//")
                    || (location.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*")
                            && !location.startsWith("file:") && !location.matches("^[A-Za-z]:[\\\\/].*"))) {
                throw GatewaySecurityProperties.invalidConfiguration();
            }
            Path path;
            if (location.startsWith("file:")) {
                URI uri = URI.create(location);
                if (uri.getRawAuthority() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                        || uri.isOpaque()) {
                    throw GatewaySecurityProperties.invalidConfiguration();
                }
                path = Path.of(uri);
            } else {
                path = Path.of(location);
            }
            if (path.toString().startsWith("\\\\") || path.toString().startsWith("//")) {
                throw GatewaySecurityProperties.invalidConfiguration();
            }
            if (!Files.isRegularFile(path) || Files.size(path) > 32768) {
                throw GatewaySecurityProperties.invalidConfiguration();
            }
            var matcher = PUBLIC_PEM.matcher(Files.readString(path, StandardCharsets.UTF_8));
            if (!matcher.matches()) {
                throw GatewaySecurityProperties.invalidConfiguration();
            }
            byte[] encoded = Base64.getDecoder().decode(matcher.group(1).replaceAll("\\s", ""));
            var key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(encoded));
            if (!(key instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength() < 3072) {
                throw GatewaySecurityProperties.invalidConfiguration();
            }
            return rsa;
        } catch (Exception exception) {
            // Do not attach the cause: parsers can echo paths or key contents in their messages.
            throw GatewaySecurityProperties.invalidConfiguration();
        }
    }
}
