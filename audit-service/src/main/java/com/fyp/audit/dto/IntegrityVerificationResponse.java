package com.fyp.audit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IntegrityVerificationResponse {

    private String transactionId;
    private String auditId;
    private boolean verified;
    private boolean hashMatch;
    private boolean chainLinkValid;
    private boolean blockchainVerified;
    private String storedHash;
    private String computedHash;
    private String message;
}
