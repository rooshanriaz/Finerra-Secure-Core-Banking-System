package com.fyp.auth.service;

import com.fyp.auth.dto.request.CreateUserRequest;
import com.fyp.auth.dto.request.UpdateUserRequest;
import com.fyp.auth.dto.response.UserResponse;
import com.fyp.auth.entity.AuditLog;
import com.fyp.auth.entity.Role;
import com.fyp.auth.entity.RevokedToken;
import com.fyp.auth.entity.User;
import com.fyp.auth.exception.ResourceAlreadyExistsException;
import com.fyp.auth.exception.ResourceNotFoundException;
import com.fyp.auth.exception.ValidationException;
import com.fyp.auth.repository.RoleRepository;
import com.fyp.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for user management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyService passwordPolicyService;
    private final AuditService auditService;
    private final RevocationService revocationService;
    private final KeycloakProvisioningService keycloakProvisioningService;

    /**
     * Create a new user.
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request, String createdBy, String ipAddress) {
        log.info("Creating user: {}", request.getUsername());

        // Check if username exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new ResourceAlreadyExistsException("User", "username", request.getUsername());
        }

        // Check if email exists
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new ResourceAlreadyExistsException("User", "email", request.getEmail());
        }

        passwordPolicyService.validateStrongPassword(request.getPassword());

        // Create user entity
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .enabled(true)
                .build();

        // Assign roles (default USER so Keycloak JWT always has a realm role for new staff)
        if (request.getRoleNames() != null && !request.getRoleNames().isEmpty()) {
            Set<Role> roles = roleRepository.findByNameIn(request.getRoleNames());
            user.setRoles(new HashSet<>(roles));
        } else {
            roleRepository.findByName("USER").ifPresent(r -> user.setRoles(new HashSet<>(Set.of(r))));
        }

        User saved = userRepository.save(user);

        // Audit log
        auditService.log(AuditLog.AuditAction.USER_CREATED, saved.getId(), createdBy,
                "USER", saved.getId(), "User created: " + saved.getUsername(),
                ipAddress, null, null, true, null, null);

        // Keycloak must reflect this user or OIDC login will fail — fail the whole operation if sync fails.
        Set<String> roleNamesForKeycloak = saved.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        if (keycloakProvisioningService.isProvisioningEnabled()) {
            boolean ok = keycloakProvisioningService.provisionUser(
                    saved.getUsername(), saved.getEmail(),
                    saved.getFirstName(), saved.getLastName(),
                    request.getPassword(),
                    roleNamesForKeycloak);
            if (!ok) {
                throw new ValidationException(
                        "User was not created: Keycloak could not be updated. "
                                + "Verify Keycloak is running and KEYCLOAK_ADMIN_URL, KEYCLOAK_ADMIN_USERNAME, "
                                + "and KEYCLOAK_ADMIN_PASSWORD. "
                                + "Set keycloak.provisioning.enabled=false only if you run without OIDC.");
            }
        }

        return mapToResponse(saved);
    }

    /**
     * Update an existing user.
     */
    @Transactional
    public UserResponse updateUser(Long userId, UpdateUserRequest request, 
                                   String updatedBy, String ipAddress) {
        log.info("Updating user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        // Update fields if provided
        if (request.getEmail() != null) {
            if (!request.getEmail().equals(user.getEmail()) 
                    && userRepository.existsByEmail(request.getEmail())) {
                throw new ResourceAlreadyExistsException("User", "email", request.getEmail());
            }
            user.setEmail(request.getEmail());
        }

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }

        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }

        User saved = userRepository.save(user);

        // Audit log
        auditService.log(AuditLog.AuditAction.USER_UPDATED, userId, updatedBy,
                "USER", userId, "User updated: " + saved.getUsername(),
                ipAddress, null, null, true, null, null);

        if (keycloakProvisioningService.isProvisioningEnabled()) {
            boolean profileOk = keycloakProvisioningService.syncUserProfile(
                    saved.getUsername(), saved.getEmail(), saved.getFirstName(), saved.getLastName());
            if (!profileOk) {
                throw new ValidationException(
                        "User was not updated: Keycloak profile sync failed. "
                                + "Check Keycloak availability and admin credentials.");
            }
        }

        return mapToResponse(saved);
    }

    /**
     * Change user password.
     */
    @Transactional
    public void changePassword(Long userId, String newPassword, String changedBy, String ipAddress) {
        log.info("Changing password for user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        passwordPolicyService.validateStrongPassword(newPassword);

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        if (keycloakProvisioningService.isProvisioningEnabled()) {
            if (!keycloakProvisioningService.syncUserPassword(user.getUsername(), newPassword)) {
                throw new ValidationException(
                        "Password was not changed: Keycloak could not be updated. "
                                + "Fix Keycloak connectivity or use sync-keycloak for this user.");
            }
        }

        // Revoke all tokens for this user (security measure)
        revocationService.revokeAllTokensForUser(userId, user.getUsername(),
                RevokedToken.RevocationReason.PASSWORD_CHANGE, changedBy);

        // Audit log
        auditService.log(AuditLog.AuditAction.PASSWORD_CHANGED, userId, changedBy,
                "USER", userId, "Password changed",
                ipAddress, null, null, true, null, null);
    }

    /**
     * Assign roles to a user.
     */
    @Transactional
    public UserResponse assignRoles(Long userId, Set<String> roleNames, 
                                    String assignedBy, String ipAddress) {
        log.info("Assigning roles {} to user {}", roleNames, userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Set<Role> roles = roleRepository.findByNameIn(roleNames);
        user.setRoles(roles);

        User saved = userRepository.save(user);

        Set<String> newRoleNames = saved.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        if (keycloakProvisioningService.isProvisioningEnabled()) {
            if (!keycloakProvisioningService.syncUserRealmRoles(saved.getUsername(), newRoleNames)) {
                throw new ValidationException(
                        "Roles were not updated: Keycloak could not be updated. "
                                + "Ensure every role name exists as a realm role in Keycloak (e.g. COMPLIANCE maps to COMPLIANCE_OFFICER).");
            }
        }

        // Revoke tokens so user gets new roles on next login
        revocationService.revokeAllTokensForUser(userId, user.getUsername(),
                RevokedToken.RevocationReason.ROLE_CHANGE, assignedBy);

        // Audit log
        auditService.log(AuditLog.AuditAction.ROLE_ASSIGNED, userId, assignedBy,
                "USER", userId, "Roles assigned: " + roleNames,
                ipAddress, null, null, true, null, null);

        return mapToResponse(saved);
    }

    /**
     * Enable a user account.
     */
    @Transactional
    public void enableUser(Long userId, String enabledBy, String ipAddress) {
        log.info("Enabling user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        user.setEnabled(true);
        userRepository.save(user);

        // Audit log
        auditService.log(AuditLog.AuditAction.USER_ENABLED, userId, enabledBy,
                "USER", userId, "User enabled",
                ipAddress, null, null, true, null, null);
    }

    /**
     * Disable a user account.
     */
    @Transactional
    public void disableUser(Long userId, String disabledBy, String ipAddress) {
        log.info("Disabling user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        user.setEnabled(false);
        userRepository.save(user);

        // Revoke all tokens
        revocationService.revokeAllTokensForUser(userId, user.getUsername(),
                RevokedToken.RevocationReason.ACCOUNT_DISABLED, disabledBy);

        // Audit log
        auditService.log(AuditLog.AuditAction.USER_DISABLED, userId, disabledBy,
                "USER", userId, "User disabled",
                ipAddress, null, null, true, null, null);
    }

    /**
     * Lock a user account.
     */
    @Transactional
    public void lockUser(Long userId, String lockedBy, String ipAddress) {
        log.info("Locking user: {}", userId);

        userRepository.lockAccount(userId, LocalDateTime.now());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        // Revoke all tokens
        revocationService.revokeAllTokensForUser(userId, user.getUsername(),
                RevokedToken.RevocationReason.SECURITY_CONCERN, lockedBy);

        // Audit log
        auditService.log(AuditLog.AuditAction.USER_LOCKED, userId, lockedBy,
                "USER", userId, "User account locked",
                ipAddress, null, null, true, null, null);
    }

    /**
     * Unlock a user account.
     */
    @Transactional
    public void unlockUser(Long userId, String unlockedBy, String ipAddress) {
        log.info("Unlocking user: {}", userId);

        userRepository.unlockAccount(userId);

        // Audit log
        auditService.log(AuditLog.AuditAction.USER_UNLOCKED, userId, unlockedBy,
                "USER", userId, "User account unlocked",
                ipAddress, null, null, true, null, null);
    }

    /**
     * Creates or updates the user in Keycloak (OIDC identity) so browser login works.
     * Admin must supply the plaintext password so Keycloak credentials match what the user will type.
     */
    public void syncUserToKeycloak(Long userId, String plainPassword) {
        passwordPolicyService.validateStrongPassword(plainPassword);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        boolean ok = keycloakProvisioningService.provisionUser(
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                plainPassword,
                roleNames);

        if (!ok) {
            throw new ValidationException(
                    "Could not synchronize user with Keycloak. "
                            + "Check that Keycloak is running and KEYCLOAK_ADMIN_URL / KEYCLOAK_ADMIN_USERNAME / "
                            + "KEYCLOAK_ADMIN_PASSWORD are correct for your environment.");
        }
    }

    /**
     * Get user by ID.
     */
    public UserResponse getUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return mapToResponse(user);
    }

    /**
     * Get user by username.
     */
    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", username));
        return mapToResponse(user);
    }

    /**
     * Get all users.
     */
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Delete a user.
     */
    @Transactional
    public void deleteUser(Long userId, String deletedBy, String ipAddress) {
        log.info("Deleting user: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        String username = user.getUsername();

        // Revoke all tokens first
        revocationService.revokeAllTokensForUser(userId, username,
                RevokedToken.RevocationReason.ADMIN_ACTION, deletedBy);

        userRepository.delete(user);

        // Remove from Keycloak (best-effort)
        keycloakProvisioningService.deprovisionUser(username);

        // Audit log
        auditService.log(AuditLog.AuditAction.USER_DELETED, null, deletedBy,
                "USER", userId, "User deleted: " + username,
                ipAddress, null, null, true, null, null);
    }

    /**
     * Map User entity to UserResponse DTO.
     */
    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .enabled(user.isEnabled())
                .accountNonLocked(user.isAccountNonLocked())
                .roles(user.getRoles().stream()
                        .map(Role::getName)
                        .collect(Collectors.toSet()))
                .fineractUserId(user.getFineractUserId())
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
