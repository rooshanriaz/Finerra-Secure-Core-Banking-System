package com.fyp.auth.controller;

import com.fyp.auth.dto.request.ChangePasswordRequest;
import com.fyp.auth.dto.request.CreateUserRequest;
import com.fyp.auth.dto.request.UpdateUserRequest;
import com.fyp.auth.dto.response.ApiResponse;
import com.fyp.auth.dto.response.UserResponse;
import com.fyp.auth.service.UserService;
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
import java.util.Map;
import java.util.Set;

/**
 * User management controller.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Create a new user.
     * POST /api/v1/users
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('CREATE_USER')")
    public ResponseEntity<ApiResponse<UserResponse>> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @RequestHeader(value = "X-User-Name", defaultValue = "system") String createdBy,
            HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIp(httpRequest);
        UserResponse user = userService.createUser(request, createdBy, ipAddress);
        
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("User created successfully", user));
    }

    /**
     * Push or update this user in Keycloak (realm users) so OIDC password grant login works.
     * POST /api/v1/users/{id}/sync-keycloak
     * Body: {@code {"password": "PlaintextPasswordMatchingWhatUserWillType"}}
     */
    @PostMapping("/{id}/sync-keycloak")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> syncUserToKeycloak(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String password = body != null ? body.get("password") : null;
        if (password == null || password.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Request body must include a non-blank \"password\" field."));
        }
        userService.syncUserToKeycloak(id, password);
        return ResponseEntity.ok(ApiResponse.success("User synchronized with Keycloak"));
    }

    /**
     * Get user by ID.
     * GET /api/v1/users/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('READ_USER') or #id == authentication.principal.id")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(@PathVariable Long id) {
        UserResponse user = userService.getUser(id);
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    /**
     * Get all users.
     * GET /api/v1/users
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('READ_USER')")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        List<UserResponse> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.success(users));
    }

    /**
     * Update user.
     * PUT /api/v1/users/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('UPDATE_USER') or #id == authentication.principal.id")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            @RequestHeader(value = "X-User-Name", defaultValue = "system") String updatedBy,
            HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIp(httpRequest);
        UserResponse user = userService.updateUser(id, request, updatedBy, ipAddress);
        
        return ResponseEntity.ok(ApiResponse.success("User updated successfully", user));
    }

    /**
     * Change user password.
     * POST /api/v1/users/{id}/password
     */
    @PostMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @PathVariable Long id,
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestHeader(value = "X-User-Name", defaultValue = "system") String changedBy,
            HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIp(httpRequest);
        userService.changePassword(id, request.getNewPassword(), changedBy, ipAddress);
        
        return ResponseEntity.ok(ApiResponse.success("Password changed successfully"));
    }

    /**
     * Assign roles to user.
     * POST /api/v1/users/{id}/roles
     */
    @PostMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('ASSIGN_ROLE')")
    public ResponseEntity<ApiResponse<UserResponse>> assignRoles(
            @PathVariable Long id,
            @RequestBody Set<String> roleNames,
            @RequestHeader(value = "X-User-Name", defaultValue = "system") String assignedBy,
            HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIp(httpRequest);
        UserResponse user = userService.assignRoles(id, roleNames, assignedBy, ipAddress);
        
        return ResponseEntity.ok(ApiResponse.success("Roles assigned successfully", user));
    }

    /**
     * Enable user.
     * POST /api/v1/users/{id}/enable
     */
    @PostMapping("/{id}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> enableUser(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Name", defaultValue = "system") String enabledBy,
            HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIp(httpRequest);
        userService.enableUser(id, enabledBy, ipAddress);
        
        return ResponseEntity.ok(ApiResponse.success("User enabled"));
    }

    /**
     * Disable user.
     * POST /api/v1/users/{id}/disable
     */
    @PostMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> disableUser(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Name", defaultValue = "system") String disabledBy,
            HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIp(httpRequest);
        userService.disableUser(id, disabledBy, ipAddress);
        
        return ResponseEntity.ok(ApiResponse.success("User disabled"));
    }

    /**
     * Lock user account.
     * POST /api/v1/users/{id}/lock
     */
    @PostMapping("/{id}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> lockUser(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Name", defaultValue = "system") String lockedBy,
            HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIp(httpRequest);
        userService.lockUser(id, lockedBy, ipAddress);
        
        return ResponseEntity.ok(ApiResponse.success("User account locked"));
    }

    /**
     * Unlock user account.
     * POST /api/v1/users/{id}/unlock
     */
    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> unlockUser(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Name", defaultValue = "system") String unlockedBy,
            HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIp(httpRequest);
        userService.unlockUser(id, unlockedBy, ipAddress);
        
        return ResponseEntity.ok(ApiResponse.success("User account unlocked"));
    }

    /**
     * Delete user.
     * DELETE /api/v1/users/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Name", defaultValue = "system") String deletedBy,
            HttpServletRequest httpRequest) {
        
        String ipAddress = getClientIp(httpRequest);
        userService.deleteUser(id, deletedBy, ipAddress);
        
        return ResponseEntity.ok(ApiResponse.success("User deleted"));
    }

    private String getClientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request);
    }
}
