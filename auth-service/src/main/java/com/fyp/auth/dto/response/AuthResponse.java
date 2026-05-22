package com.fyp.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Authentication response DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private boolean success;
    private String message;
    private String accessToken;
    private String refreshToken;
    private String idToken;
    private String tokenType;
    private long expiresIn;
    private UserInfo user;

    private Boolean mfaRequired;
    private String mfaToken;
    private Boolean didVerified;
    private Boolean firstLogin;
    private Boolean mfaSetupRequired;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;
        private String username;
        private String email;
        private String fullName;
        private Set<String> roles;
        private Set<String> permissions;
    }
}
