package com.fyp.cbc.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fyp.cbc.dto.response.ApiResponse;
import com.fyp.cbc.dto.response.RoleResponse;
import com.fyp.cbc.service.RoleService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * REST controller for permissions management.
 * Provides endpoints for listing available permissions.
 */
@RestController
@RequestMapping("/v1/permissions")
@RequiredArgsConstructor
@Tag(name = "Permissions", description = "Permissions listing APIs")
public class PermissionController {
    
    private final RoleService roleService;
    
    /**
     * List all available permissions.
     * GET /v1/permissions
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List permissions", description = "List all available permissions")
    public ResponseEntity<ApiResponse<List<RoleResponse.PermissionData>>> listPermissions() {
        ApiResponse<List<RoleResponse.PermissionData>> response = roleService.listPermissions();
        return ResponseEntity.ok(response);
    }
}
