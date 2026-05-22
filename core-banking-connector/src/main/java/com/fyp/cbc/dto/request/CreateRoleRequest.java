package com.fyp.cbc.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new role.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoleRequest {
    
    @NotBlank(message = "Role name is required")
    private String name;
    
    private String description;
    
    /**
     * List of permission codes to assign to this role.
     */
    private List<String> permissions;
}
