package com.fyp.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for MFA setup containing the TOTP secret and QR code URI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaSetupResponse {

    private String secret;
    private String qrCodeUri;
    private String issuer;
}
