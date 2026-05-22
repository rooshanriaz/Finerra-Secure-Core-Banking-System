package com.fyp.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Role response DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {

    private Long id;
    private String name;
    private String description;
    private boolean enabled;
    private boolean systemRole;
    private boolean syncedFromFineract;
    private Long fineractRoleId;
    private Set<String> permissions;
    private long userCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
