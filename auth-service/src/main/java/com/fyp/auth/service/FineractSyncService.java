package com.fyp.auth.service;

import com.fyp.auth.config.CbcProperties;
import com.fyp.auth.entity.AuditLog;
import com.fyp.auth.entity.Permission;
import com.fyp.auth.entity.Role;
import com.fyp.auth.repository.PermissionRepository;
import com.fyp.auth.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Service for synchronizing roles and permissions with Apache Fineract
 * through the Core Banking Connector.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cbc.sync.enabled", havingValue = "true")
public class FineractSyncService {

    private final CbcProperties cbcProperties;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditService auditService;
    private final WebClient.Builder webClientBuilder;

    private WebClient webClient;

    private WebClient getWebClient() {
        if (webClient == null) {
            // Build Basic Auth header for service-to-service authentication
            String credentials = cbcProperties.getUsername() + ":" + cbcProperties.getPassword();
            String encodedCredentials = Base64.getEncoder()
                    .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

            webClient = webClientBuilder
                    .baseUrl(cbcProperties.getUrl())
                    .defaultHeader("Authorization", "Basic " + encodedCredentials)
                    .build();
            
            log.info("CBC WebClient configured with base URL: {} and Basic Auth user: {}", 
                    cbcProperties.getUrl(), cbcProperties.getUsername());
        }
        return webClient;
    }

    /**
     * Scheduled sync of roles from Fineract.
     */
    @Scheduled(fixedRateString = "${cbc.sync.interval:300000}")
    @Transactional
    public void syncRolesFromFineract() {
        log.info("Starting Fineract role sync...");
        
        auditService.log(AuditLog.AuditAction.FINERACT_SYNC_STARTED, null, "SYSTEM",
                "SYNC", null, "Started Fineract role synchronization",
                null, null, null, true, null, null);

        try {
            // Fetch roles from CBC (which fetches from Fineract)
            List<Map<String, Object>> fineractRoles = fetchRolesFromCbc();
            
            if (fineractRoles == null || fineractRoles.isEmpty()) {
                log.warn("No roles received from Fineract");
                return;
            }

            int created = 0;
            int updated = 0;

            for (Map<String, Object> fineractRole : fineractRoles) {
                Long fineractRoleId = ((Number) fineractRole.get("id")).longValue();
                String roleName = (String) fineractRole.get("name");
                String description = (String) fineractRole.get("description");

                Role existingRole = roleRepository.findByFineractRoleId(fineractRoleId)
                        .orElse(null);

                if (existingRole != null) {
                    // Update existing role
                    if (!existingRole.getName().equals(roleName)) {
                        existingRole.setName(roleName);
                        existingRole.setDescription(description);
                        roleRepository.save(existingRole);
                        updated++;
                        log.debug("Updated role: {}", roleName);
                    }
                } else {
                    // Check if role with same name exists
                    if (!roleRepository.existsByName(roleName)) {
                        Role newRole = Role.builder()
                                .name(roleName)
                                .description(description)
                                .fineractRoleId(fineractRoleId)
                                .syncedFromFineract(true)
                                .enabled(true)
                                .build();
                        roleRepository.save(newRole);
                        created++;
                        log.debug("Created role from Fineract: {}", roleName);
                    }
                }
            }

            log.info("Fineract sync completed: {} created, {} updated", created, updated);
            
            auditService.log(AuditLog.AuditAction.FINERACT_SYNC_COMPLETED, null, "SYSTEM",
                    "SYNC", null, "Fineract sync completed: " + created + " created, " + updated + " updated",
                    null, null, null, true, null, null);

        } catch (Exception e) {
            log.error("Fineract sync failed: {}", e.getMessage(), e);
            
            auditService.log(AuditLog.AuditAction.FINERACT_SYNC_FAILED, null, "SYSTEM",
                    "SYNC", null, "Fineract sync failed",
                    null, null, null, false, e.getMessage(), null);
        }
    }

    /**
     * Fetch roles from Core Banking Connector.
     * CBC returns responses wrapped in ApiResponse: { success, message, data: [...roles] }
     * CBC context path is /api, controller is /v1/roles → full path: /api/v1/roles
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchRolesFromCbc() {
        try {
            log.info("Fetching roles from CBC at: {}/api/v1/roles", cbcProperties.getUrl());
            
            // CBC wraps responses in ApiResponse { success, data, message }
            Map<String, Object> apiResponse = getWebClient()
                    .get()
                    .uri("/api/v1/roles")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (apiResponse == null) {
                log.warn("Null response from CBC");
                return null;
            }

            // Check if the response indicates success
            Boolean success = (Boolean) apiResponse.get("success");
            if (success == null || !success) {
                log.warn("CBC returned unsuccessful response: {}", apiResponse.get("message"));
                return null;
            }

            // Extract the roles list from the "data" field
            Object data = apiResponse.get("data");
            if (data instanceof List) {
                List<Map<String, Object>> roles = (List<Map<String, Object>>) data;
                log.info("Received {} roles from CBC", roles.size());
                return roles;
            }

            log.warn("Unexpected data format from CBC: {}", data != null ? data.getClass().getName() : "null");
            return null;
        } catch (Exception e) {
            log.error("Failed to fetch roles from CBC: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Manual sync trigger.
     */
    @Transactional
    public void triggerSync() {
        log.info("Manual sync triggered");
        syncRolesFromFineract();
    }

    /**
     * Get sync status.
     */
    public SyncStatus getSyncStatus() {
        long syncedRoles = roleRepository.findBySyncedFromFineractTrue().size();
        long syncedPermissions = permissionRepository.findBySyncedFromFineractTrue().size();
        
        return new SyncStatus(
                cbcProperties.getSync().isEnabled(),
                cbcProperties.getSync().getInterval(),
                syncedRoles,
                syncedPermissions
        );
    }

    /**
     * Sync status record.
     */
    public record SyncStatus(
            boolean enabled,
            long intervalMs,
            long syncedRoles,
            long syncedPermissions
    ) {}
}
