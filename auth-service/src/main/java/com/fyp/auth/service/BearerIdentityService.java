package com.fyp.auth.service;

import com.fyp.auth.entity.Role;
import com.fyp.auth.entity.User;
import com.fyp.auth.exception.AuthenticationException;
import com.fyp.auth.repository.RoleRepository;
import com.fyp.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves {@link User} from either legacy HS256 auth-service tokens or Keycloak RS256 access tokens,
 * provisioning a local {@link User} row on first Keycloak login so MFA and RBAC can use the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BearerIdentityService {

    private final TokenService tokenService;
    private final JwtDecoder keycloakJwtDecoder;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User getUserForBearerToken(String authHeader) {
        String token = stripBearer(authHeader);
        if (tokenService.validateToken(token)) {
            String username = tokenService.extractUsername(token);
            return userRepository.findByUsername(username)
                    .orElseThrow(() -> new AuthenticationException("User not found", "USER_NOT_FOUND"));
        }
        try {
            Jwt jwt = keycloakJwtDecoder.decode(token);
            String username = preferredUsername(jwt);
            return userRepository.findByUsername(username)
                    .orElseGet(() -> provisionFromKeycloakJwt(jwt, username));
        } catch (JwtException e) {
            throw new AuthenticationException("Invalid bearer token", "INVALID_TOKEN");
        }
    }

    private User provisionFromKeycloakJwt(Jwt jwt, String username) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            email = username + "@finnera.local";
        }
        if (userRepository.existsByEmail(email)) {
            email = username + "@keycloak.finnera.local";
        }

        String given = jwt.getClaimAsString("given_name");
        String family = jwt.getClaimAsString("family_name");

        Set<Role> roles = resolveRealmRoles(jwt);
        if (roles.isEmpty()) {
            roleRepository.findByName("USER").ifPresent(roles::add);
        }

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode("KEYCLOAK_OIDC_MANAGED_" + UUID.randomUUID()))
                .firstName(given != null ? given : username)
                .lastName(family != null ? family : "")
                .enabled(true)
                .roles(roles)
                .build();

        User saved = userRepository.save(user);
        log.info("Provisioned local user '{}' from Keycloak access token", username);
        return saved;
    }

    @SuppressWarnings("unchecked")
    private Set<Role> resolveRealmRoles(Jwt jwt) {
        Set<Role> out = new HashSet<>();
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> raw)) {
            return out;
        }
        for (Object r : raw) {
            if (r == null) {
                continue;
            }
            String name = mapKeycloakRoleName(String.valueOf(r));
            roleRepository.findByName(name).ifPresent(out::add);
        }
        return out;
    }

    /**
     * Keycloak realm role names may differ slightly from DB role names (e.g. COMPLIANCE_OFFICER vs COMPLIANCE).
     */
    private String mapKeycloakRoleName(String kc) {
        if ("COMPLIANCE_OFFICER".equalsIgnoreCase(kc)) {
            return "COMPLIANCE";
        }
        return kc;
    }

    private static String preferredUsername(Jwt jwt) {
        String u = jwt.getClaimAsString("preferred_username");
        if (u != null && !u.isBlank()) {
            return u;
        }
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new AuthenticationException("Token has no subject", "INVALID_TOKEN");
        }
        return sub;
    }

    private static String stripBearer(String authHeader) {
        if (authHeader == null) {
            throw new AuthenticationException("Missing Authorization header", "UNAUTHORIZED");
        }
        return authHeader.startsWith("Bearer ") ? authHeader.substring(7) : authHeader;
    }
}
