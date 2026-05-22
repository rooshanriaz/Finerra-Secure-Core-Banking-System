package com.fyp.auth.controller;

import com.fyp.auth.dto.response.ApiResponse;
import com.fyp.auth.service.OidcKeyService;
import com.fyp.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight OIDC discovery endpoints for integration compatibility.
 */
@RestController
@RequiredArgsConstructor
public class OidcController {

    private final OidcKeyService oidcKeyService;
    private final TokenService tokenService;
    @Value("${oidc.issuer-url:http://localhost:8082}")
    private String oidcIssuerUrl;

    @GetMapping("/.well-known/openid-configuration")
    public ResponseEntity<Map<String, Object>> openIdConfiguration() {
        String issuer = oidcIssuerUrl;
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("issuer", issuer);
        cfg.put("jwks_uri", issuer + "/oauth2/jwks");
        cfg.put("userinfo_endpoint", issuer + "/oauth2/userinfo");
        cfg.put("token_endpoint", issuer + "/api/v1/auth/login");
        cfg.put("grant_types_supported", java.util.List.of("password", "refresh_token"));
        cfg.put("response_types_supported", java.util.List.of("token", "id_token"));
        cfg.put("subject_types_supported", java.util.List.of("public"));
        cfg.put("id_token_signing_alg_values_supported", java.util.List.of("RS256"));
        cfg.put("scopes_supported", java.util.List.of("openid", "profile", "email"));
        return ResponseEntity.ok(cfg);
    }

    @GetMapping("/oauth2/jwks")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok(oidcKeyService.getJwks());
    }

    @GetMapping("/oauth2/userinfo")
    public ResponseEntity<ApiResponse<Map<String, Object>>> userInfo(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ")
                ? authHeader.substring(7)
                : authHeader;

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", tokenService.extractUsername(token));
        claims.put("roles", tokenService.extractRoles(token));
        claims.put("permissions", tokenService.extractPermissions(token));
        return ResponseEntity.ok(ApiResponse.success("OIDC userinfo", claims));
    }
}
