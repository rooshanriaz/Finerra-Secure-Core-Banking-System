package com.fyp.auth.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Update role request DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRoleRequest {

    @Size(max = 500, message = "Description must be max 500 characters")
    private String description;

    private Boolean enabled;
}
