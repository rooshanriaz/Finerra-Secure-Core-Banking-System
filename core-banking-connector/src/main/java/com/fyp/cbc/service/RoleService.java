package com.fyp.cbc.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fyp.cbc.client.RolesPermissionsClient;
import com.fyp.cbc.dto.request.CreateRoleRequest;
import com.fyp.cbc.dto.response.ApiResponse;
import com.fyp.cbc.dto.response.RoleResponse;

import lombok.RequiredArgsConstructor;

/**
 * Service layer for roles and permissions management.
 * Handles RBAC operations for insider threat mitigation.
 */
@Service
@RequiredArgsConstructor
public class RoleService {
    
    private static final Logger log = LoggerFactory.getLogger(RoleService.class);
    
    private final RolesPermissionsClient rolesPermissionsClient;
    
    /**
     * Create a new role.
     * 
     * @param request Role creation details
     * @return API response containing created role
     */
    public ApiResponse<RoleResponse> createRole(CreateRoleRequest request) {
        log.info("Creating role: {}", request.getName());
        
        RoleResponse response = rolesPermissionsClient.createRole(request);
        
        log.info("Role created with ID: {}", response.getResourceId());
        return ApiResponse.success("Role created successfully", response);
    }
    
    /**
     * List all roles.
     * 
     * @return API response with list of roles
     */
    public ApiResponse<List<RoleResponse>> listRoles() {
        log.debug("Listing all roles");
        
        List<RoleResponse> roles = rolesPermissionsClient.listRoles();
        return ApiResponse.success(roles);
    }
    
    /**
     * Get role details by ID.
     * 
     * @param roleId The role ID
     * @return API response with role details
     */
    public ApiResponse<RoleResponse> getRole(Long roleId) {
        log.debug("Retrieving role: {}", roleId);
        
        RoleResponse response = rolesPermissionsClient.getRole(roleId);
        return ApiResponse.success(response);
    }
    
    /**
     * Update a role.
     * 
     * @param roleId The role ID
     * @param request Updated role details
     * @return API response with updated role
     */
    public ApiResponse<RoleResponse> updateRole(Long roleId, CreateRoleRequest request) {
        log.info("Updating role: {}", roleId);
        
        RoleResponse response = rolesPermissionsClient.updateRole(roleId, request);
        
        log.info("Role {} updated successfully", roleId);
        return ApiResponse.success("Role updated successfully", response);
    }
    
    /**
     * Get permissions for a role.
     * 
     * @param roleId The role ID
     * @return API response with role permissions
     */
    public ApiResponse<RoleResponse.PermissionsData> getRolePermissions(Long roleId) {
        log.debug("Retrieving permissions for role: {}", roleId);
        
        RoleResponse.PermissionsData permissions = rolesPermissionsClient.getRolePermissions(roleId);
        return ApiResponse.success(permissions);
    }
    
    /**
     * Update permissions for a role.
     * 
     * @param roleId The role ID
     * @param permissions Permissions update request
     * @return API response with updated permissions
     */
    public ApiResponse<Object> updateRolePermissions(Long roleId, Object permissions) {
        log.info("Updating permissions for role: {}", roleId);
        
        Object response = rolesPermissionsClient.updateRolePermissions(roleId, permissions);
        
        log.info("Permissions for role {} updated successfully", roleId);
        return ApiResponse.success("Permissions updated successfully", response);
    }
    
    /**
     * List all available permissions.
     * 
     * @return API response with list of permissions
     */
    public ApiResponse<List<RoleResponse.PermissionData>> listPermissions() {
        log.debug("Listing all permissions");
        
        List<RoleResponse.PermissionData> permissions = rolesPermissionsClient.listPermissions();
        return ApiResponse.success(permissions);
    }
}
