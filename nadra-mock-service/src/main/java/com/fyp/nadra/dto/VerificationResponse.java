package com.fyp.nadra.dto;

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
public class VerificationResponse {

    private String requestId;
    private String cnicNumber;
    private VerificationStatus status;
    private String verificationToken;
    private String message;
    private Double matchScore;
    private PersonDetails personDetails;
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    public enum VerificationStatus {
        VERIFIED, REJECTED, PENDING, EXPIRED, NOT_FOUND
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PersonDetails {
        private String fullName;
        private String fatherName;
        private String dateOfBirth;
        private String gender;
        private String address;
        private String issuanceDate;
        private String expiryDate;
    }
}
