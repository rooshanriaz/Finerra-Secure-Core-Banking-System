package com.fyp.cbc.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fyp.cbc.dto.request.CreateRoleRequest;
import com.fyp.cbc.dto.response.ApiResponse;
import com.fyp.cbc.dto.response.RoleResponse;
import com.fyp.cbc.service.RoleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for roles and permissions management.
 * Provides endpoints for RBAC and insider threat mitigation.
 */
@RestController
@RequestMapping("/v1/roles")
@RequiredArgsConstructor
@Tag(name = "Roles & Permissions", description = "RBAC management APIs")
public class RoleController {
    
    private final RoleService roleService;
    
    /**
     * Create a new role.
     * POST /v1/roles
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create role", description = "Create a new role")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(
            @Valid @RequestBody CreateRoleRequest request) {
        ApiResponse<RoleResponse> response = roleService.createRole(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    /**
     * List all roles.
     * GET /v1/roles
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List roles", description = "List all roles")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> listRoles() {
        ApiResponse<List<RoleResponse>> response = roleService.listRoles();
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get role details by ID.
     * GET /v1/roles/{roleId}
     */
    @GetMapping("/{roleId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get role", description = "Get role details by ID")
    public ResponseEntity<ApiResponse<RoleResponse>> getRole(
            @Parameter(description = "Role ID") @PathVariable Long roleId) {
        ApiResponse<RoleResponse> response = roleService.getRole(roleId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Update a role.
     * PUT /v1/roles/{roleId}
     */
    @PutMapping("/{roleId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update role", description = "Update role details")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @Parameter(description = "Role ID") @PathVariable Long roleId,
            @Valid @RequestBody CreateRoleRequest request) {
        ApiResponse<RoleResponse> response = roleService.updateRole(roleId, request);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get permissions for a role.
     * GET /v1/roles/{roleId}/permissions
     */
    @GetMapping("/{roleId}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get role permissions", description = "Get permissions for a role")
    public ResponseEntity<ApiResponse<RoleResponse.PermissionsData>> getRolePermissions(
            @Parameter(description = "Role ID") @PathVariable Long roleId) {
        ApiResponse<RoleResponse.PermissionsData> response = roleService.getRolePermissions(roleId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Update permissions for a role.
     * PUT /v1/roles/{roleId}/permissions
     */
    @PutMapping("/{roleId}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update role permissions", description = "Assign/update permissions for a role")
    public ResponseEntity<ApiResponse<Object>> updateRolePermissions(
            @Parameter(description = "Role ID") @PathVariable Long roleId,
            @RequestBody Object permissions) {
        ApiResponse<Object> response = roleService.updateRolePermissions(roleId, permissions);
        return ResponseEntity.ok(response);
    }
}
