package com.fyp.cbc.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for role data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {
    
    private Long id;
    private Long resourceId;
    private String name;
    private String description;
    private Boolean disabled;
    private List<PermissionData> permissionUsageData;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionData {
        private String grouping;
        private String code;
        private String entityName;
        private String actionName;
        private Boolean selected;
    }
    
    /**
     * Response for permissions listing.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionResponse {
        private String grouping;
        private String code;
        private String entityName;
        private String actionName;
    }
    
    /**
     * Response containing role permissions data.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionsData {
        private Long id;
        private String name;
        private String description;
        private Boolean disabled;
        private List<PermissionData> permissionUsageData;
    }
}
