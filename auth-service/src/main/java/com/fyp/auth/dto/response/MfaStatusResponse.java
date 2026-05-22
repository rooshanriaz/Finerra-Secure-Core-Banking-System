package com.fyp.auth.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for checking a user's MFA and DID status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaStatusResponse {

    private boolean mfaEnabled;
    private boolean didLinked;
    private String didIdentifier;
}
