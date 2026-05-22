package com.fyp.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for verifying a TOTP MFA code during login.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MfaVerifyRequest {

    @NotBlank(message = "MFA token is required")
    private String mfaToken;

    @NotBlank(message = "TOTP code is required")
    @Size(min = 6, max = 6, message = "TOTP code must be 6 digits")
    private String code;
}
