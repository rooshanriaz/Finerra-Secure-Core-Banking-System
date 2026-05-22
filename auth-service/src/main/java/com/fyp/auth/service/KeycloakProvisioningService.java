package com.fyp.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Provisions users in Keycloak whenever they are created or deleted in the
 * auth-service, keeping both identity stores in sync so users can authenticate
 * via the Keycloak OIDC password grant used by the frontend.
 *
 * When {@code keycloak.provisioning.enabled=true} (default), user creation in
 * {@code UserService} requires a successful Keycloak update so OIDC login always works.
 */
@Slf4j
@Service
public class KeycloakProvisioningService {

    @Value("${keycloak.admin.url:http://localhost:8090}")
    private String keycloakAdminUrl;

    @Value("${keycloak.admin.username:admin}")
    private String adminUsername;

    @Value("${keycloak.admin.password:admin}")
    private String adminPassword;

    @Value("${keycloak.realm:finnera}")
    private String realm;

    @Value("${keycloak.provisioning.enabled:true}")
    private boolean provisioningEnabled;

    private final RestTemplate restTemplate;

    public KeycloakProvisioningService(RestTemplateBuilder builder) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean isProvisioningEnabled() {
        return provisioningEnabled;
    }

    /**
     * Creates a user in Keycloak with the given credentials and realm roles.
     * Role names are auth-service role names (e.g. "MANAGER"); they are mapped
     * to Keycloak realm role names before assignment.
     *
     * @return true if the user was created (or already existed) in Keycloak
     */
    public boolean provisionUser(String username, String email, String firstName,
                                  String lastName, String plainPassword, Set<String> roleNames) {
        if (!provisioningEnabled) {
            log.debug("Keycloak provisioning disabled – skipping for user '{}'", username);
            return false;
        }
        try {
            String adminToken = obtainAdminToken();
            if (adminToken == null) {
                log.warn("Could not obtain Keycloak admin token – user '{}' not provisioned in Keycloak", username);
                return false;
            }

            String existingId = findUserIdByUsername(username, adminToken);
            if (existingId != null) {
                // User already in Keycloak — keep password and roles aligned with auth-service
                // (fixes users created in auth before sync, or passwords changed only in the DB).
                if (plainPassword != null && !plainPassword.isBlank()) {
                    setUserPassword(existingId, plainPassword, adminToken);
                }
                if (roleNames != null && !roleNames.isEmpty()) {
                    if (!replaceRealmRoles(existingId, roleNames, adminToken)) {
                        return false;
                    }
                }
                log.info("Updated existing Keycloak user '{}'", username);
                return true;
            }

            String usersUrl = keycloakAdminUrl + "/admin/realms/" + realm + "/users";

            // Build user representation
            Map<String, Object> userRep = new HashMap<>();
            userRep.put("username", username);
            userRep.put("email", email != null && !email.isBlank() ? email : username + "@finnera.local");
            userRep.put("firstName", firstName != null ? firstName : username);
            userRep.put("lastName", lastName != null ? lastName : "");
            userRep.put("enabled", true);
            userRep.put("emailVerified", true);

            Map<String, Object> credential = new HashMap<>();
            credential.put("type", "password");
            credential.put("value", plainPassword);
            credential.put("temporary", false);
            userRep.put("credentials", List.of(credential));

            HttpHeaders createHeaders = bearerHeaders(adminToken);
            createHeaders.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Void> createResp = restTemplate.exchange(
                    usersUrl, HttpMethod.POST,
                    new HttpEntity<>(userRep, createHeaders), Void.class);

            if (!createResp.getStatusCode().is2xxSuccessful()) {
                log.warn("Keycloak returned {} when creating user '{}'", createResp.getStatusCode(), username);
                return false;
            }

            String keycloakUserId = null;
            String location = createResp.getHeaders().getFirst(HttpHeaders.LOCATION);
            if (location != null) {
                keycloakUserId = location.substring(location.lastIndexOf('/') + 1);
            }
            if (keycloakUserId == null || keycloakUserId.isBlank()) {
                keycloakUserId = findUserIdByUsername(username, adminToken);
            }
            if (keycloakUserId == null) {
                log.warn("Keycloak created user '{}' but no user id could be resolved", username);
                return false;
            }

            if (roleNames != null && !roleNames.isEmpty()) {
                if (!replaceRealmRoles(keycloakUserId, roleNames, adminToken)) {
                    return false;
                }
            }

            log.info("Provisioned Keycloak user '{}' (id={})", username, keycloakUserId);
            return true;

        } catch (HttpClientErrorException.Conflict e) {
            log.info("Keycloak user '{}' already exists (409 Conflict) — aligning password and roles", username);
            return alignExistingUser(username, plainPassword, roleNames);
        } catch (Exception e) {
            log.warn("Failed to provision Keycloak user '{}': {}", username, e.getMessage());
            return false;
        }
    }

    /**
     * Updates email / name fields in Keycloak for an existing user (best-effort profile sync).
     */
    public boolean syncUserProfile(String username, String email, String firstName, String lastName) {
        if (!provisioningEnabled) {
            return true;
        }
        try {
            String adminToken = obtainAdminToken();
            if (adminToken == null) {
                return false;
            }
            String userId = findUserIdByUsername(username, adminToken);
            if (userId == null) {
                log.warn("syncUserProfile: user '{}' not found in Keycloak — skipping profile sync", username);
                return true;
            }
            Map<String, Object> rep = new HashMap<>();
            rep.put("email", email != null && !email.isBlank() ? email : username + "@finnera.local");
            rep.put("firstName", firstName != null ? firstName : username);
            rep.put("lastName", lastName != null ? lastName : "");
            rep.put("emailVerified", true);
            String url = keycloakAdminUrl + "/admin/realms/" + realm + "/users/" + userId;
            HttpHeaders headers = bearerHeaders(adminToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(rep, headers), Void.class);
            return true;
        } catch (Exception e) {
            log.warn("Failed to sync Keycloak profile for '{}': {}", username, e.getMessage());
            return false;
        }
    }

    private boolean alignExistingUser(String username, String plainPassword, Set<String> roleNames) {
        try {
            String adminToken = obtainAdminToken();
            if (adminToken == null) {
                return false;
            }
            String userId = findUserIdByUsername(username, adminToken);
            if (userId == null) {
                return false;
            }
            if (plainPassword != null && !plainPassword.isBlank()) {
                setUserPassword(userId, plainPassword, adminToken);
            }
            if (roleNames != null && !roleNames.isEmpty()) {
                return replaceRealmRoles(userId, roleNames, adminToken);
            }
            return true;
        } catch (Exception e) {
            log.warn("alignExistingUser failed for '{}': {}", username, e.getMessage());
            return false;
        }
    }

    /**
     * Deletes a user from Keycloak by username.
     */
    public void deprovisionUser(String username) {
        if (!provisioningEnabled) return;
        try {
            String adminToken = obtainAdminToken();
            if (adminToken == null) return;

            String userId = findUserIdByUsername(username, adminToken);
            if (userId == null) return;

            restTemplate.exchange(
                    keycloakAdminUrl + "/admin/realms/" + realm + "/users/" + userId,
                    HttpMethod.DELETE, new HttpEntity<>(bearerHeaders(adminToken)), Void.class);

            log.info("Deprovisioned Keycloak user '{}'", username);
        } catch (Exception e) {
            log.warn("Failed to deprovision Keycloak user '{}': {}", username, e.getMessage());
        }
    }

    /**
     * Updates the Keycloak password for an existing user (e.g. after password change in auth-service).
     *
     * @return {@code true} if skipped (provisioning off / blank password), succeeded, or not applicable
     */
    public boolean syncUserPassword(String username, String plainPassword) {
        if (!provisioningEnabled || plainPassword == null || plainPassword.isBlank()) {
            return true;
        }
        try {
            String adminToken = obtainAdminToken();
            if (adminToken == null) {
                return false;
            }
            String userId = findUserIdByUsername(username, adminToken);
            if (userId == null) {
                log.warn("syncUserPassword: user '{}' not found in Keycloak", username);
                return false;
            }
            setUserPassword(userId, plainPassword, adminToken);
            log.info("Synced Keycloak password for user '{}'", username);
            return true;
        } catch (Exception e) {
            log.warn("Failed to sync Keycloak password for '{}': {}", username, e.getMessage());
            return false;
        }
    }

    /**
     * Replaces realm role assignments in Keycloak for the given username.
     *
     * @return {@code true} if skipped (provisioning off / empty roles) or replacement succeeded
     */
    public boolean syncUserRealmRoles(String username, Set<String> roleNames) {
        if (!provisioningEnabled || roleNames == null || roleNames.isEmpty()) {
            return true;
        }
        try {
            String adminToken = obtainAdminToken();
            if (adminToken == null) {
                return false;
            }
            String userId = findUserIdByUsername(username, adminToken);
            if (userId == null) {
                log.warn("syncUserRealmRoles: user '{}' not found in Keycloak", username);
                return false;
            }
            boolean ok = replaceRealmRoles(userId, roleNames, adminToken);
            if (ok) {
                log.info("Synced Keycloak realm roles for user '{}': {}", username, roleNames);
            }
            return ok;
        } catch (Exception e) {
            log.warn("Failed to sync Keycloak roles for '{}': {}", username, e.getMessage());
            return false;
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private String obtainAdminToken() {
        try {
            String tokenUrl = keycloakAdminUrl + "/realms/master/protocol/openid-connect/token";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "password");
            body.add("client_id", "admin-cli");
            body.add("username", adminUsername);
            body.add("password", adminPassword);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    tokenUrl, new HttpEntity<>(body, headers), Map.class);
            return response != null ? (String) response.get("access_token") : null;
        } catch (Exception e) {
            log.warn("Could not obtain Keycloak admin token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Looks up a Keycloak user id by username (exact match). Username is URL-encoded for the query.
     */
    @SuppressWarnings("unchecked")
    private String findUserIdByUsername(String username, String adminToken) {
        try {
            String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
            String url = keycloakAdminUrl + "/admin/realms/" + realm + "/users?username=" + encoded + "&exact=true";
            ResponseEntity<List> resp = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(adminToken)), List.class);
            if (resp.getBody() == null || resp.getBody().isEmpty()) {
                return null;
            }
            Map<String, Object> user = (Map<String, Object>) resp.getBody().get(0);
            return (String) user.get("id");
        } catch (Exception e) {
            log.warn("findUserIdByUsername failed for '{}': {}", username, e.getMessage());
            return null;
        }
    }

    private void setUserPassword(String keycloakUserId, String plainPassword, String adminToken) {
        String url = keycloakAdminUrl + "/admin/realms/" + realm + "/users/" + keycloakUserId + "/reset-password";
        Map<String, Object> body = new HashMap<>();
        body.put("type", "password");
        body.put("value", plainPassword);
        body.put("temporary", false);
        HttpHeaders headers = bearerHeaders(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, headers), Void.class);
    }

    /**
     * Auth-service DB uses {@code COMPLIANCE}; Keycloak realm export uses {@code COMPLIANCE_OFFICER}.
     */
    private static String toKeycloakRealmRoleName(String authRoleName) {
        if (authRoleName == null) return null;
        if ("COMPLIANCE".equalsIgnoreCase(authRoleName)) {
            return "COMPLIANCE_OFFICER";
        }
        return authRoleName;
    }

    private static Set<String> expandToKeycloakRoleNames(Set<String> roleNames) {
        Set<String> out = new HashSet<>();
        for (String r : roleNames) {
            if (r == null) continue;
            out.add(toKeycloakRealmRoleName(r));
        }
        return out;
    }

    /**
     * Resolves desired roles first; only clears existing realm mappings after a non-empty
     * replacement set is known, so we never leave the user with zero roles on a bad role name.
     */
    @SuppressWarnings("unchecked")
    private boolean replaceRealmRoles(String keycloakUserId, Set<String> roleNames, String adminToken) {
        try {
            Set<String> kcRoleNames = expandToKeycloakRoleNames(roleNames);

            String rolesUrl = keycloakAdminUrl + "/admin/realms/" + realm + "/roles";
            ResponseEntity<List> allRolesResp = restTemplate.exchange(
                    rolesUrl, HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders(adminToken)), List.class);

            if (allRolesResp.getBody() == null) {
                return false;
            }

            List<Map<String, Object>> rolesToAssign = new ArrayList<>();
            for (Object roleObj : allRolesResp.getBody()) {
                Map<String, Object> role = (Map<String, Object>) roleObj;
                String kcRoleName = (String) role.get("name");
                if (kcRoleName != null && kcRoleNames.contains(kcRoleName)) {
                    rolesToAssign.add(role);
                }
            }

            if (rolesToAssign.isEmpty()) {
                log.error("No Keycloak realm roles match auth roles {} for user {} — check realm role names",
                        roleNames, keycloakUserId);
                return false;
            }

            String mappingsUrl = keycloakAdminUrl + "/admin/realms/" + realm
                    + "/users/" + keycloakUserId + "/role-mappings/realm";
            HttpHeaders readHeaders = bearerHeaders(adminToken);
            ResponseEntity<List> current = restTemplate.exchange(
                    mappingsUrl, HttpMethod.GET, new HttpEntity<>(readHeaders), List.class);

            if (current.getBody() != null && !current.getBody().isEmpty()) {
                HttpHeaders delHeaders = bearerHeaders(adminToken);
                delHeaders.setContentType(MediaType.APPLICATION_JSON);
                restTemplate.exchange(mappingsUrl, HttpMethod.DELETE,
                        new HttpEntity<>(current.getBody(), delHeaders), Void.class);
            }

            HttpHeaders postHeaders = bearerHeaders(adminToken);
            postHeaders.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.exchange(
                    mappingsUrl, HttpMethod.POST,
                    new HttpEntity<>(rolesToAssign, postHeaders), Void.class);

            log.debug("Assigned Keycloak roles {} to user {}", roleNames, keycloakUserId);
            return true;
        } catch (Exception e) {
            log.warn("Failed to replace Keycloak roles for user {}: {}", keycloakUserId, e.getMessage());
            return false;
        }
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }
}
