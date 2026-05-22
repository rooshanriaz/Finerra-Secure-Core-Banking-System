package com.fyp.kyc.service;

import com.fyp.kyc.entity.KycRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KycAuditClientService {
    private final WebClient.Builder webClientBuilder;
    private final MachineTokenService machineTokenService;

    @Value("${services.audit-service.url:http://localhost:8087/api}")
    private String auditServiceUrl;

    public void recordOnboardingAudit(KycRecord record, String initiatedBy) {
        if (record == null || record.getReferenceId() == null) {
            return;
        }
        recordAuditEvent(
                record.getReferenceId(),
                record.getFineractClientId(),
                record.getDidId(),
                initiatedBy,
                "KYC_ONBOARD"
        );
    }

    public void recordOnboardingAttempt(String referenceId, Long accountId, String initiatedBy, String actionType) {
        if (referenceId == null || referenceId.isBlank()) {
            return;
        }
        recordAuditEvent(referenceId, accountId, null, initiatedBy, actionType == null ? "KYC_ONBOARD" : actionType);
    }

    private void recordAuditEvent(String transactionId, Long accountId, String fineractTransactionId, String initiatedBy, String transactionType) {
        Map<String, Object> request = new HashMap<>();
        request.put("transactionId", transactionId);
        request.put("accountId", accountId);
        request.put("loanId", null);
        request.put("transactionType", transactionType);
        request.put("amount", BigDecimal.ZERO);
        request.put("transactionDate", LocalDateTime.now().toString());
        request.put("fineractTransactionId", fineractTransactionId);
        request.put("initiatedBy", initiatedBy == null ? "system" : initiatedBy);
        request.put("sourceIp", "kyc-aml-service");

        try {
            WebClient client = webClientBuilder.baseUrl(auditServiceUrl).build();
            Map<String, Object> response = client.post()
                    .uri("/v1/audit/record")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(machineTokenService.getAccessToken()))
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    })
                    .block();

            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                log.info("KYC audit recorded successfully for {} ({})", transactionId, transactionType);
            } else {
                log.warn("KYC audit returned non-success for {} ({})", transactionId, transactionType);
            }
        } catch (Exception ex) {
            log.warn("Failed to record KYC audit for {} ({}): {}", transactionId, transactionType, ex.getMessage());
        }
    }
}

