package com.fyp.audit.service;

import com.fyp.audit.dto.AuditRecordRequest;
import com.fyp.audit.dto.AuditRecordResponse;
import com.fyp.audit.dto.IntegrityCheckResponse;
import com.fyp.audit.entity.AuditRecord;
import com.fyp.audit.entity.AuditRecord.BlockchainStatus;
import com.fyp.audit.repository.AuditRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.nio.charset.StandardCharsets;

/**
 * Core audit processing service.
 * Handles audit record creation, hashing, blockchain anchoring, and integrity verification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditProcessingService {

    private final AuditRecordRepository repository;
    private final HashService hashService;
    private final FabricAuditService fabricAuditService;

    /**
     * Record a transaction audit entry.
     */
    public AuditRecordResponse recordAudit(AuditRecordRequest request) {
        String auditId = "AUDIT-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        log.info("Recording audit: auditId={}, txnId={}", auditId, request.getTransactionId());

        // Check for duplicate
        Optional<AuditRecord> existing = repository.findByTransactionId(request.getTransactionId());
        if (existing.isPresent()) {
            log.warn("Audit record already exists for transaction: {}", request.getTransactionId());
            return mapToResponse(existing.get());
        }

        // Compute data hash
        String dataHash = hashService.computeTransactionHash(
            request.getTransactionId(),
            request.getAccountId(),
            request.getLoanId(),
            request.getTransactionType(),
            request.getAmount(),
            request.getTransactionDate(),
            request.getFineractTransactionId(),
            request.getInitiatedBy()
        );

        // Get previous hash for chain linking
        String previousHash = repository.findTopByOrderByIdDesc()
            .map(AuditRecord::getDataHash)
            .orElse("GENESIS");

        // Create audit record
        AuditRecord record = AuditRecord.builder()
            .auditId(auditId)
            .transactionId(request.getTransactionId())
            .accountId(request.getAccountId())
            .loanId(request.getLoanId())
            .transactionType(request.getTransactionType())
            .amount(request.getAmount())
            .transactionDate(request.getTransactionDate())
            .fineractTransactionId(request.getFineractTransactionId())
            .initiatedBy(request.getInitiatedBy())
            .sourceIp(request.getSourceIp())
            .dataHash(dataHash)
            .previousHash(previousHash)
            .blockchainStatus(BlockchainStatus.LOCAL_ONLY)
            .build();

        record = repository.save(record);

        // Anchor to blockchain (async-like behavior via circuit breaker)
        try {
            record = fabricAuditService.anchorToBlockchain(record);
        } catch (Exception e) {
            log.error("Blockchain anchoring failed: {}", e.getMessage());
            record.setBlockchainStatus(BlockchainStatus.FAILED);
            record = repository.save(record);
        }

        log.info("Audit recorded: auditId={}, hash={}, blockchainStatus={}", 
            auditId, dataHash.substring(0, 16) + "...", record.getBlockchainStatus());

        return mapToResponse(record);
    }

    /**
     * Get audit record by ID.
     */
    public AuditRecordResponse getAuditRecord(String auditId) {
        return repository.findByAuditId(auditId)
            .map(this::mapToResponse)
            .orElse(null);
    }

    /**
     * Get audit record by transaction ID.
     */
    public AuditRecordResponse getAuditByTransactionId(String transactionId) {
        return repository.findByTransactionId(transactionId)
            .map(this::mapToResponse)
            .orElse(null);
    }

    /**
     * Get audit records for an account.
     */
    public List<AuditRecordResponse> getAuditsByAccount(Long accountId) {
        return repository.findByAccountIdOrderByRecordedAtDesc(accountId)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get audit records by user.
     */
    public List<AuditRecordResponse> getAuditsByUser(String username) {
        return repository.findByInitiatedByOrderByRecordedAtDesc(username)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get audit records by date range.
     */
    public List<AuditRecordResponse> getAuditsByDateRange(LocalDateTime from, LocalDateTime to) {
        return repository.findByRecordedAtBetweenOrderByRecordedAtDesc(from, to)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get all recent audit records.
     */
    public List<AuditRecordResponse> getRecentAudits() {
        return repository.findRecentAudits()
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Verify integrity of a single audit record.
     */
    public Map<String, Object> verifyAuditIntegrity(String auditId) {
        AuditRecord record = repository.findByAuditId(auditId).orElse(null);
        if (record == null) {
            return Map.of("verified", false, "message", "Audit record not found");
        }

        // Recompute hash and compare
        boolean hashValid = hashService.verifyHash(
            record.getDataHash(),
            record.getTransactionId(),
            record.getAccountId(),
            record.getLoanId(),
            record.getTransactionType(),
            record.getAmount(),
            record.getTransactionDate(),
            record.getFineractTransactionId(),
            record.getInitiatedBy()
        );

        // Also verify blockchain
        Map<String, Object> blockchainVerification = fabricAuditService.verifyOnBlockchain(auditId);

        boolean chainOk = Boolean.TRUE.equals(blockchainVerification.get("verified"));
        boolean overall = hashValid && chainOk;

        Map<String, Object> result = new HashMap<>();
        result.put("auditId", auditId);
        result.put("hashValid", hashValid);
        result.put("blockchainVerification", blockchainVerification);
        result.put("overallIntegrity", overall);
        // Alias for clients expecting a single boolean (e.g. frontend `data.verified`)
        result.put("verified", overall);
        result.put("message", overall
                ? "Integrity verified: local hash matches and blockchain check passed"
                : (!hashValid
                        ? "Local data hash does not match recomputed hash — record fields may have changed"
                        : String.valueOf(blockchainVerification.getOrDefault("message", "Blockchain verification failed"))));

        return result;
    }

    /**
     * Run full integrity check on all audit records.
     */
    public IntegrityCheckResponse runFullIntegrityCheck() {
        log.info("Running full integrity check...");
        List<AuditRecord> allRecords = repository.findAll();
        
        List<IntegrityCheckResponse.FailedRecord> failures = new ArrayList<>();
        int verified = 0;

        for (AuditRecord record : allRecords) {
            boolean valid = hashService.verifyHash(
                record.getDataHash(),
                record.getTransactionId(),
                record.getAccountId(),
                record.getLoanId(),
                record.getTransactionType(),
                record.getAmount(),
                record.getTransactionDate(),
                record.getFineractTransactionId(),
                record.getInitiatedBy()
            );

            if (valid) {
                verified++;
            } else {
                String computedHash = hashService.computeTransactionHash(
                    record.getTransactionId(),
                    record.getAccountId(),
                    record.getLoanId(),
                    record.getTransactionType(),
                    record.getAmount(),
                    record.getTransactionDate(),
                    record.getFineractTransactionId(),
                    record.getInitiatedBy()
                );
                failures.add(IntegrityCheckResponse.FailedRecord.builder()
                    .auditId(record.getAuditId())
                    .transactionId(record.getTransactionId())
                    .expectedHash(record.getDataHash())
                    .actualHash(computedHash)
                    .reason("Hash mismatch - data may have been tampered with")
                    .build());
            }
        }

        log.info("Integrity check complete: {}/{} verified, {} failures", 
            verified, allRecords.size(), failures.size());

        return IntegrityCheckResponse.builder()
            .totalRecords(allRecords.size())
            .verifiedCount(verified)
            .failedCount(failures.size())
            .allIntact(failures.isEmpty())
            .failedRecords(failures.isEmpty() ? null : failures)
            .checkedAt(LocalDateTime.now())
            .build();
    }

    public List<AuditRecordResponse> getAuditsForReport(LocalDateTime from, LocalDateTime to) {
        if (from != null && to != null) {
            return getAuditsByDateRange(from, to);
        }
        return getRecentAudits();
    }

    public byte[] generateCsvReport(List<AuditRecordResponse> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("auditId,transactionId,accountId,loanId,transactionType,amount,transactionDate,initiatedBy,blockchainStatus,fabricTxId,dataHash,recordedAt\n");
        for (AuditRecordResponse r : records) {
            sb.append(csv(r.getAuditId())).append(',')
                    .append(csv(r.getTransactionId())).append(',')
                    .append(csv(r.getAccountId())).append(',')
                    .append(csv(r.getLoanId())).append(',')
                    .append(csv(r.getTransactionType())).append(',')
                    .append(csv(r.getAmount())).append(',')
                    .append(csv(r.getTransactionDate())).append(',')
                    .append(csv(r.getInitiatedBy())).append(',')
                    .append(csv(r.getBlockchainStatus())).append(',')
                    .append(csv(r.getFabricTxId())).append(',')
                    .append(csv(r.getDataHash())).append(',')
                    .append(csv(r.getRecordedAt()))
                    .append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] generatePdfReport(List<AuditRecordResponse> records, LocalDateTime from, LocalDateTime to) {
        StringBuilder text = new StringBuilder();
        text.append("Compliance Audit Export\n");
        text.append("Generated At: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        if (from != null && to != null) {
            text.append("Range: ").append(from).append(" to ").append(to).append("\n");
        } else {
            text.append("Range: recent records\n");
        }
        text.append("Total Records: ").append(records.size()).append("\n\n");
        for (AuditRecordResponse r : records) {
            text.append("Audit ID: ").append(nullSafe(r.getAuditId())).append("\n");
            text.append("Txn ID: ").append(nullSafe(r.getTransactionId())).append(" | Type: ").append(nullSafe(r.getTransactionType())).append(" | Amount: ").append(nullSafe(r.getAmount())).append("\n");
            text.append("Initiated By: ").append(nullSafe(r.getInitiatedBy())).append(" | Status: ").append(nullSafe(r.getBlockchainStatus())).append("\n");
            text.append("Fabric Tx: ").append(nullSafe(r.getFabricTxId())).append("\n");
            text.append("Hash: ").append(nullSafe(r.getDataHash())).append("\n");
            text.append("Recorded At: ").append(nullSafe(r.getRecordedAt())).append("\n");
            text.append("------------------------------------------------------------\n");
        }
        return minimalPdf(text.toString());
    }

    private String csv(Object value) {
        if (value == null) {
            return "\"\"";
        }
        String raw = String.valueOf(value).replace("\"", "\"\"");
        return "\"" + raw + "\"";
    }

    private String nullSafe(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    /**
     * Small PDF generator without external dependency.
     */
    private byte[] minimalPdf(String content) {
        String escaped = content
                .replace("\\", "\\\\")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("\r", "");

        String[] lines = escaped.split("\n");
        StringBuilder stream = new StringBuilder();
        stream.append("BT /F1 10 Tf 40 800 Td ");
        boolean first = true;
        for (String line : lines) {
            if (!first) {
                stream.append("T* ");
            }
            stream.append("(").append(line).append(") Tj ");
            first = false;
        }
        stream.append("ET");

        String streamContent = stream.toString();

        String obj1 = "1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n";
        String obj2 = "2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n";
        String obj3 = "3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n";
        String obj4 = "4 0 obj << /Length " + streamContent.getBytes(StandardCharsets.US_ASCII).length + " >> stream\n" +
                streamContent + "\nendstream endobj\n";
        String obj5 = "5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Courier >> endobj\n";

        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        int xref1 = pdf.length(); pdf.append(obj1);
        int xref2 = pdf.length(); pdf.append(obj2);
        int xref3 = pdf.length(); pdf.append(obj3);
        int xref4 = pdf.length(); pdf.append(obj4);
        int xref5 = pdf.length(); pdf.append(obj5);
        int xrefStart = pdf.length();

        pdf.append("xref\n0 6\n");
        pdf.append("0000000000 65535 f \n");
        pdf.append(String.format("%010d 00000 n \n", xref1));
        pdf.append(String.format("%010d 00000 n \n", xref2));
        pdf.append(String.format("%010d 00000 n \n", xref3));
        pdf.append(String.format("%010d 00000 n \n", xref4));
        pdf.append(String.format("%010d 00000 n \n", xref5));
        pdf.append("trailer << /Size 6 /Root 1 0 R >>\n");
        pdf.append("startxref\n").append(xrefStart).append("\n%%EOF");

        return pdf.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private AuditRecordResponse mapToResponse(AuditRecord record) {
        return AuditRecordResponse.builder()
            .auditId(record.getAuditId())
            .transactionId(record.getTransactionId())
            .accountId(record.getAccountId())
            .loanId(record.getLoanId())
            .transactionType(record.getTransactionType())
            .amount(record.getAmount())
            .transactionDate(record.getTransactionDate())
            .fineractTransactionId(record.getFineractTransactionId())
            .initiatedBy(record.getInitiatedBy())
            .dataHash(record.getDataHash())
            .fabricTxId(record.getFabricTxId())
            .blockchainStatus(record.getBlockchainStatus().name())
            .integrityVerified(hashService.verifyHash(
                record.getDataHash(),
                record.getTransactionId(),
                record.getAccountId(),
                record.getLoanId(),
                record.getTransactionType(),
                record.getAmount(),
                record.getTransactionDate(),
                record.getFineractTransactionId(),
                record.getInitiatedBy()
            ))
            .recordedAt(record.getRecordedAt())
            .build();
    }
}
