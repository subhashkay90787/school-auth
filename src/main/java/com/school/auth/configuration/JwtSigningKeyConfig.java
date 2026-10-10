package com.school.auth.configuration;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;

/**
 * Signing key for all tokens issued by this server. Other services validate tokens against the public half,
 * published at /oauth2/jwks, so the key must be stable across restarts and identical on every instance.
 *
 * Generate one with:
 *   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/jwt-private.pem
 */
@Slf4j
@Configuration
public class JwtSigningKeyConfig {

    @Bean
    public JWKSource<SecurityContext> jwkSource(
            @Value("${app.jwt.private-key:}") String privateKeyLocation,
            ResourceLoader resourceLoader) throws IOException, GeneralSecurityException, JOSEException {
        RSAKey rsaKey;
        if (StringUtils.hasText(privateKeyLocation)) {
            Resource privateKeyPem = resourceLoader.getResource(privateKeyLocation);
            if (!privateKeyPem.exists()) {
                throw new IllegalStateException("JWT signing key not found: " + privateKeyLocation);
            }
            rsaKey = loadKey(privateKeyPem);
            log.info("Loaded JWT signing key {} (kid={})", privateKeyPem.getDescription(), rsaKey.getKeyID());
        } else {
            log.warn("app.jwt.private-key is not set; using a temporary signing key. "
                    + "Tokens will stop validating when this server restarts. Do not use this in production.");
            rsaKey = new RSAKeyGenerator(2048).keyIDFromThumbprint(true).generate();
        }
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    private static RSAKey loadKey(Resource privateKeyPem) throws IOException, GeneralSecurityException, JOSEException {
        RSAPrivateCrtKey privateKey;
        try (InputStream in = privateKeyPem.getInputStream()) {
            privateKey = (RSAPrivateCrtKey) RsaKeyConverters.pkcs8().convert(in);
        }
        // The public key is derived from the private key, so only one file needs to be managed
        BigInteger modulus = privateKey.getModulus();
        RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new RSAPublicKeySpec(modulus, privateKey.getPublicExponent()));

        RSAKey withoutKid = new RSAKey.Builder(publicKey).privateKey(privateKey).build();
        // kid derived from the key itself: stable across restarts and instances, changes only on key rotation
        return new RSAKey.Builder(withoutKid).keyID(withoutKid.computeThumbprint().toString()).build();
    }
}
