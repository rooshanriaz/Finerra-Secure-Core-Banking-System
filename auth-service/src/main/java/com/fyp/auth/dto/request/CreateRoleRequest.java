package com.fyp.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Create role request DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoleRequest {

    @NotBlank(message = "Role name is required")
    @Size(min = 2, max = 100, message = "Role name must be 2-100 characters")
    private String name;

    @Size(max = 500, message = "Description must be max 500 characters")
    private String description;

    /**
     * Permission codes to assign to the role.
     */
    private Set<String> permissionCodes;
}
