package com.fyp.auth.service;

import com.fyp.auth.dto.request.CreateRoleRequest;
import com.fyp.auth.dto.request.UpdateRoleRequest;
import com.fyp.auth.dto.response.RoleResponse;
import com.fyp.auth.entity.AuditLog;
import com.fyp.auth.entity.Permission;
import com.fyp.auth.entity.Role;
import com.fyp.auth.exception.ResourceAlreadyExistsException;
import com.fyp.auth.exception.ResourceNotFoundException;
import com.fyp.auth.exception.ValidationException;
import com.fyp.auth.repository.PermissionRepository;
import com.fyp.auth.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for role and permission management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditService auditService;

    /**
     * Create a new role.
     */
    @Transactional
    public RoleResponse createRole(CreateRoleRequest request, String createdBy, String ipAddress) {
        log.info("Creating role: {}", request.getName());

        if (roleRepository.existsByName(request.getName())) {
            throw new ResourceAlreadyExistsException("Role", "name", request.getName());
        }

        Role role = Role.builder()
                .name(request.getName())
                .description(request.getDescription())
                .enabled(true)
                .systemRole(false)
                .syncedFromFineract(false)
                .build();

        // Assign permissions if specified
        if (request.getPermissionCodes() != null && !request.getPermissionCodes().isEmpty()) {
            Set<Permission> permissions = permissionRepository.findByCodeIn(request.getPermissionCodes());
            role.setPermissions(permissions);
        }

        Role saved = roleRepository.save(role);

        // Audit log
        auditService.log(AuditLog.AuditAction.ROLE_CREATED, null, createdBy,
                "ROLE", saved.getId(), "Role created: " + saved.getName(),
                ipAddress, null, null, true, null, null);

        return mapToResponse(saved);
    }

    /**
     * Update an existing role.
     */
    @Transactional
    public RoleResponse updateRole(Long roleId, UpdateRoleRequest request, 
                                   String updatedBy, String ipAddress) {
        log.info("Updating role: {}", roleId);

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));

        if (role.isSystemRole()) {
            throw new ValidationException("System roles cannot be modified");
        }

        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }

        if (request.getEnabled() != null) {
            role.setEnabled(request.getEnabled());
        }

        Role saved = roleRepository.save(role);

        // Audit log
        auditService.log(AuditLog.AuditAction.ROLE_UPDATED, null, updatedBy,
                "ROLE", roleId, "Role updated: " + saved.getName(),
                ipAddress, null, null, true, null, null);

        return mapToResponse(saved);
    }

    /**
     * Assign permissions to a role.
     */
    @Transactional
    public RoleResponse assignPermissions(Long roleId, Set<String> permissionCodes,
                                          String assignedBy, String ipAddress) {
        log.info("Assigning permissions {} to role {}", permissionCodes, roleId);

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));

        if (role.isSystemRole()) {
            throw new ValidationException("System role permissions cannot be modified");
        }

        Set<Permission> permissions = permissionRepository.findByCodeIn(permissionCodes);
        role.setPermissions(permissions);

        Role saved = roleRepository.save(role);

        // Audit log
        auditService.log(AuditLog.AuditAction.PERMISSION_GRANTED, null, assignedBy,
                "ROLE", roleId, "Permissions assigned to role: " + permissionCodes,
                ipAddress, null, null, true, null, null);

        return mapToResponse(saved);
    }

    /**
     * Get role by ID.
     */
    public RoleResponse getRole(Long roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
        return mapToResponse(role);
    }

    /**
     * Get role by name.
     */
    public RoleResponse getRoleByName(String name) {
        Role role = roleRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Role", "name", name));
        return mapToResponse(role);
    }

    /**
     * Get all roles.
     */
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get all enabled roles.
     */
    public List<RoleResponse> getEnabledRoles() {
        return roleRepository.findByEnabledTrue().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Delete a role.
     */
    @Transactional
    public void deleteRole(Long roleId, String deletedBy, String ipAddress) {
        log.info("Deleting role: {}", roleId);

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));

        if (role.isSystemRole()) {
            throw new ValidationException("System roles cannot be deleted");
        }

        // Check if role is assigned to any users
        long userCount = roleRepository.countUsersWithRole(roleId);
        if (userCount > 0) {
            throw new ValidationException("Cannot delete role: assigned to " + userCount + " users");
        }

        String roleName = role.getName();
        roleRepository.delete(role);

        // Audit log
        auditService.log(AuditLog.AuditAction.ROLE_DELETED, null, deletedBy,
                "ROLE", roleId, "Role deleted: " + roleName,
                ipAddress, null, null, true, null, null);
    }

    /**
     * Get all permissions.
     */
    public List<Permission> getAllPermissions() {
        return permissionRepository.findByEnabledTrue();
    }

    /**
     * Get permissions by grouping.
     */
    public List<Permission> getPermissionsByGrouping(String grouping) {
        return permissionRepository.findByGrouping(grouping);
    }

    /**
     * Get distinct permission groupings.
     */
    public List<String> getPermissionGroupings() {
        return permissionRepository.findDistinctGroupings();
    }

    /**
     * Map Role entity to RoleResponse DTO.
     */
    private RoleResponse mapToResponse(Role role) {
        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .description(role.getDescription())
                .enabled(role.isEnabled())
                .systemRole(role.isSystemRole())
                .syncedFromFineract(role.isSyncedFromFineract())
                .fineractRoleId(role.getFineractRoleId())
                .permissions(role.getPermissions().stream()
                        .map(Permission::getCode)
                        .collect(Collectors.toSet()))
                .userCount(roleRepository.countUsersWithRole(role.getId()))
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
}
