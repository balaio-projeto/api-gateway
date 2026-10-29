package br.com.balaio.api_gateway;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;

/** Emissor de teste isolado: a chave privada existe apenas em memoria. */
final class GatewaySecurityTestFixtures {
    static final String ISSUER = "https://profile.example.test";
    static final String AUDIENCE = "balaio-api-test";
    static final String ORIGIN = "https://frontend.example.test";
    static final String SECOND_ORIGIN = "http://localhost:5173";
    static final String SUBJECT = "af58b270-9491-4321-9b12-e6becf88a465";
    private static final KeyPair KEYS = rsa(3072);
    private static final Path PUBLIC_KEY = writePublicKey(KEYS);

    private GatewaySecurityTestFixtures() { }

    static String publicKeyLocation() { return PUBLIC_KEY.toUri().toString(); }
    static String publicKeyPath() { return PUBLIC_KEY.toString(); }
    static byte[] publicKeyBytes() { return KEYS.getPublic().getEncoded(); }

    static Map<String, Object> claims(String role) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", SUBJECT);
        claims.put("papel", role);
        claims.put("iss", ISSUER);
        claims.put("aud", java.util.List.of(AUDIENCE));
        claims.put("iat", now - 5);
        claims.put("exp", now + 3500);
        claims.put("jti", UUID.randomUUID().toString());
        return claims;
    }

    static String token(String role) { return token(claims(role)); }
    static String token(Map<String, Object> claims) { return sign(new Payload(claims), JWSAlgorithm.RS256, KEYS); }
    static String rawToken(String payload) { return sign(new Payload(payload), JWSAlgorithm.RS256, KEYS); }
    static String otherKeyToken(Map<String, Object> claims) { return sign(new Payload(claims), JWSAlgorithm.RS256, rsa(3072)); }
    static String rs512Token(Map<String, Object> claims) { return sign(new Payload(claims), JWSAlgorithm.RS512, KEYS); }

    static String hs256Token(Map<String, Object> claims) {
        try {
            var jwt = new JWSObject(new JWSHeader(JWSAlgorithm.HS256), new Payload(claims));
            jwt.sign(new MACSigner(new byte[64]));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Nao foi possivel emitir JWT HS256 de teste.", exception);
        }
    }

    static String unsignedToken(Map<String, Object> claims) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8))
                + "." + new Payload(claims).toBase64URL() + ".";
    }

    static KeyPair rsa(int bits) {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("Nao foi possivel gerar RSA de teste.", exception);
        }
    }

    static String publicPem(byte[] encoded) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(encoded)
                + "\n-----END PUBLIC KEY-----\n";
    }

    private static String sign(Payload payload, JWSAlgorithm algorithm, KeyPair keys) {
        try {
            var jwt = new JWSObject(new JWSHeader(algorithm), payload);
            jwt.sign(new RSASSASigner((RSAPrivateKey) keys.getPrivate()));
            return jwt.serialize();
        } catch (Exception exception) {
            throw new IllegalStateException("Nao foi possivel emitir JWT RSA de teste.", exception);
        }
    }

    private static Path writePublicKey(KeyPair keys) {
        try {
            Path directory = Files.createTempDirectory("balaio-gateway-public-test-");
            directory.toFile().deleteOnExit();
            Path file = directory.resolve("public.pem");
            Files.writeString(file, publicPem(keys.getPublic().getEncoded()), StandardCharsets.US_ASCII);
            file.toFile().deleteOnExit();
            return file;
        } catch (Exception exception) {
            throw new IllegalStateException("Nao foi possivel preparar a chave publica de teste.", exception);
        }
    }
}
