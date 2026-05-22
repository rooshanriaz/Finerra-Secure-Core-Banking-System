package com.fyp.auth.controller;

import com.fyp.auth.dto.request.CreateRoleRequest;
import com.fyp.auth.dto.request.UpdateRoleRequest;
import com.fyp.auth.dto.response.ApiResponse;
import com.fyp.auth.dto.response.RoleResponse;
import com.fyp.auth.entity.Permission;
import com.fyp.auth.service.RoleService;
import com.fyp.auth.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * Role and permission management controller.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /**
     * Create a new role.
     * POST /api/v1/roles
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CREATE_ROLE')")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            @Valid @RequestBody CreateRoleRequest request,
            @RequestHeader(value = "X-User-Name", defaultValue = "system") String createdBy,
            HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIp(httpRequest);
        RoleResponse role = roleService.createRole(request, createdBy, ipAddress);
        
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Role created successfully", role));
    }

    /**
     * Get role by ID.
     * GET /api/v1/roles/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('READ_ROLE')")
    public ResponseEntity<ApiResponse<RoleResponse>> getRole(@PathVariable Long id) {
        RoleResponse role = roleService.getRole(id);
        return ResponseEntity.ok(ApiResponse.success(role));
    }

    /**
     * Get all roles.
     * GET /api/v1/roles
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('READ_ROLE')")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        List<RoleResponse> roles = roleService.getAllRoles();
        return ResponseEntity.ok(ApiResponse.success(roles));
    }

    /**
     * Get enabled roles only.
     * GET /api/v1/roles/enabled
     */
    @GetMapping("/enabled")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('READ_ROLE')")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getEnabledRoles() {
        List<RoleResponse> roles = roleService.getEnabledRoles();
        return ResponseEntity.ok(ApiResponse.success(roles));
    }

    /**
     * Update role.
     * PUT /api/v1/roles/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('UPDATE_ROLE')")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest request,
            @RequestHeader(value = "X-User-Name", defaultValue = "system") String updatedBy,
            HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIp(httpRequest);
        RoleResponse role = roleService.updateRole(id, request, updatedBy, ipAddress);
        
        return ResponseEntity.ok(ApiResponse.success("Role updated successfully", role));
    }

    /**
     * Assign permissions to role.
     * POST /api/v1/roles/{id}/permissions
     */
    @PostMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('UPDATE_ROLE')")
    public ResponseEntity<ApiResponse<RoleResponse>> assignPermissions(
            @PathVariable Long id,
            @RequestBody Set<String> permissionCodes,
            @RequestHeader(value = "X-User-Name", defaultValue = "system") String assignedBy,
            HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIp(httpRequest);
        RoleResponse role = roleService.assignPermissions(id, permissionCodes, assignedBy, ipAddress);
        
        return ResponseEntity.ok(ApiResponse.success("Permissions assigned successfully", role));
    }

    /**
     * Delete role.
     * DELETE /api/v1/roles/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteRole(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Name", defaultValue = "system") String deletedBy,
            HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIp(httpRequest);
        roleService.deleteRole(id, deletedBy, ipAddress);
        
        return ResponseEntity.ok(ApiResponse.success("Role deleted"));
    }

    /**
     * Get all permissions.
     * GET /api/v1/roles/permissions
     */
    @GetMapping("/permissions")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('READ_PERMISSION')")
    public ResponseEntity<ApiResponse<List<Permission>>> getAllPermissions() {
        List<Permission> permissions = roleService.getAllPermissions();
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }

    /**
     * Get permission groupings.
     * GET /api/v1/roles/permissions/groupings
     */
    @GetMapping("/permissions/groupings")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('READ_PERMISSION')")
    public ResponseEntity<ApiResponse<List<String>>> getPermissionGroupings() {
        List<String> groupings = roleService.getPermissionGroupings();
        return ResponseEntity.ok(ApiResponse.success(groupings));
    }

    /**
     * Get permissions by grouping.
     * GET /api/v1/roles/permissions/group/{grouping}
     */
    @GetMapping("/permissions/group/{grouping}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('READ_PERMISSION')")
    public ResponseEntity<ApiResponse<List<Permission>>> getPermissionsByGrouping(
            @PathVariable String grouping) {
        List<Permission> permissions = roleService.getPermissionsByGrouping(grouping);
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }

    private String getClientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request);
    }
}
