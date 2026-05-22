package com.fyp.txn.service;

import com.fyp.txn.dto.AuditRecordRequest;
import com.fyp.txn.entity.TransactionRecord;
import com.fyp.txn.entity.TransactionRecord.TransactionStatus;
import com.fyp.txn.repository.TransactionRecordRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * Async client for audit-service.
 * Sends transaction audit records after successful transaction processing.
 */
@Slf4j
@Service
public class AuditClientService {

    private final WebClient webClient;
    private final TransactionRecordRepository txnRepository;
    private final MachineTokenService machineTokenService;

    public AuditClientService(WebClient.Builder webClientBuilder,
                              @Value("${services.audit-service.url}") String auditUrl,
                              MachineTokenService machineTokenService,
                              TransactionRecordRepository txnRepository) {
        this.machineTokenService = machineTokenService;
        this.webClient = webClientBuilder
            .baseUrl(auditUrl)
            .filter((request, next) -> {
                String token = resolveBearerToken();
                var authorized = org.springframework.web.reactive.function.client.ClientRequest.from(request)
                        .headers(headers -> headers.setBearerAuth(token))
                        .build();
                return next.exchange(authorized);
            })
            .build();
        this.txnRepository = txnRepository;
        log.info("Audit client configured with URL: {}", auditUrl);
    }

    /**
     * Send transaction to audit-service for blockchain recording.
     */
    @CircuitBreaker(name = "auditService", fallbackMethod = "recordAuditFallback")
    public void recordTransactionAudit(TransactionRecord txnRecord) {
        log.info("Sending audit record for transaction: {}", txnRecord.getTransactionId());

        TransactionStatus st = txnRecord.getStatus();
        if (st == TransactionStatus.FAILED || st == TransactionStatus.REJECTED) {
            log.debug("Skip audit for transaction {} (status={})", txnRecord.getTransactionId(), st);
            return;
        }

        AuditRecordRequest request = AuditRecordRequest.builder()
            .transactionId(txnRecord.getTransactionId())
            .accountId(txnRecord.getAccountId())
            .loanId(txnRecord.getLoanId())
            .transactionType(txnRecord.getTransactionType().name())
            .amount(txnRecord.getAmount())
            .transactionDate(txnRecord.getTransactionDate())
            .fineractTransactionId(txnRecord.getFineractTransactionId())
            .initiatedBy(txnRecord.getInitiatedBy())
            .sourceIp(txnRecord.getSourceIp())
            .build();

        try {
            Map<String, Object> response = webClient.post()
                .uri("/v1/audit/record")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .block();

            if (response != null && truthy(response.get("success"))) {
                Object dataObj = response.get("data");
                String auditRef = extractAuditId(dataObj);
                txnRecord.setAuditReference(auditRef);
                txnRecord.setAuditStatus("RECORDED");
                txnRepository.save(txnRecord);
                log.info("Audit recorded successfully: txn={}, auditRef={}",
                    txnRecord.getTransactionId(), auditRef);
            } else {
                applyAuditFailureStatus(txnRecord);
                txnRepository.save(txnRecord);
                log.warn("Audit recording returned non-success for txn: {}",
                    txnRecord.getTransactionId());
            }
        } catch (Exception e) {
            applyAuditFailureStatus(txnRecord);
            txnRepository.save(txnRecord);
            log.error("Failed to record audit for txn {}: {}",
                txnRecord.getTransactionId(), e.getMessage());
        }
    }

    private static boolean truthy(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            return "true".equalsIgnoreCase(s);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static String extractAuditId(Object dataObj) {
        if (!(dataObj instanceof Map<?, ?> raw)) {
            return null;
        }
        Map<String, Object> data = (Map<String, Object>) raw;
        Object aid = data.get("auditId");
        return aid == null ? null : String.valueOf(aid);
    }

    /** Do not overwrite FRAUD_BLOCKED with AUDIT_FAILED — UI keeps fraud semantics. */
    private static void applyAuditFailureStatus(TransactionRecord txnRecord) {
        if ("FRAUD_BLOCKED".equals(txnRecord.getAuditStatus())) {
            return;
        }
        txnRecord.setAuditStatus("AUDIT_FAILED");
    }

    @SuppressWarnings("unused")
    private void recordAuditFallback(TransactionRecord txnRecord, Throwable t) {
        log.warn("Audit service unavailable. Transaction {} completed but audit pending. Error: {}",
            txnRecord.getTransactionId(), t.getMessage());
        if (!"FRAUD_BLOCKED".equals(txnRecord.getAuditStatus())) {
            txnRecord.setAuditStatus("AUDIT_PENDING");
        }
        txnRepository.save(txnRecord);
    }

    private String resolveBearerToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth && jwtAuth.getToken() != null) {
            String tokenValue = jwtAuth.getToken().getTokenValue();
            if (tokenValue != null && !tokenValue.isBlank()) {
                return tokenValue;
            }
        }
        return machineTokenService.getAccessToken();
    }
}
