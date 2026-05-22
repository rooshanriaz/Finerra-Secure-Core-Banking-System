package com.fyp.auth.service;

import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Provides a runtime RSA keypair for OIDC ID token signing and JWKS publishing.
 */
@Service
public class OidcKeyService {

    private final KeyPair keyPair;
    private final String keyId;

    public OidcKeyService() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            this.keyPair = generator.generateKeyPair();
            this.keyId = "oidc-" + UUID.randomUUID().toString().substring(0, 12);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize OIDC keypair", e);
        }
    }

    public RSAPrivateKey getPrivateKey() {
        return (RSAPrivateKey) keyPair.getPrivate();
    }

    public String getKeyId() {
        return keyId;
    }

    public Map<String, Object> getJwks() {
        try {
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            RSAPublicKeySpec spec = KeyFactory.getInstance("RSA").getKeySpec(publicKey, RSAPublicKeySpec.class);

            Map<String, Object> jwk = new LinkedHashMap<>();
            jwk.put("kty", "RSA");
            jwk.put("use", "sig");
            jwk.put("alg", "RS256");
            jwk.put("kid", keyId);
            jwk.put("n", toBase64Url(spec.getModulus()));
            jwk.put("e", toBase64Url(spec.getPublicExponent()));

            Map<String, Object> jwks = new LinkedHashMap<>();
            jwks.put("keys", java.util.List.of(jwk));
            return jwks;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build JWKS", e);
        }
    }

    private String toBase64Url(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
