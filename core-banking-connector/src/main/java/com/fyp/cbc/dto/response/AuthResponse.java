package com.fyp.cbc.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for authentication result.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    
    private String username;
    private Long userId;
    private String base64EncodedAuthenticationKey;
    private boolean authenticated;
    private Long officeId;
    private String officeName;
    private Long staffId;
    private String staffDisplayName;
    private List<RoleData> roles;
    private List<String> permissions;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleData {
        private Long id;
        private String name;
        private String description;
        private Boolean disabled;
    }
}
