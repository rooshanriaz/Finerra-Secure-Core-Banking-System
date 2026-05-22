package com.fyp.kyc.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DidResponse {

    private String didId;
    private String context;
    private String controller;
    private String status;
    private String publicKey;
    private String authenticationMethod;
    private String createdAt;
    private String updatedAt;
    private List<CredentialInfo> credentials;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CredentialInfo {
        private String id;
        private String type;
        private String status;
        private String issuanceDate;
        private String expirationDate;
    }
}
