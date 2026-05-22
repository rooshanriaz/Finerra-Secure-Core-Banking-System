package com.fyp.kyc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class KycOnboardResponse {

    private String referenceId;
    private String kycStatus;
    private String amlStatus;
    
    // NADRA verification
    private String nadraVerificationToken;
    private String nadraRequestId;
    
    // DID details
    private String didId;
    private String credentialId;
    
    // Fineract client
    private Long fineractClientId;
    
    // Rejection
    private String rejectionReason;
    
    private LocalDateTime timestamp;
}
