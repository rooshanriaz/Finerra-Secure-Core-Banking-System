package com.fyp.txn.service;

import com.fyp.txn.dto.TransactionApprovalRequestPayload;
import com.fyp.txn.dto.TransactionApprovalRequestResponse;
import com.fyp.txn.dto.TransactionRequest;
import com.fyp.txn.dto.TransactionResponse;
import com.fyp.txn.entity.TransactionApprovalRequest;
import com.fyp.txn.entity.TransactionApprovalRequest.ApprovalStatus;
import com.fyp.txn.entity.TransactionApprovalRequest.RequestedTransactionType;
import com.fyp.txn.repository.TransactionApprovalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionApprovalWorkflowService {

    private final TransactionApprovalRequestRepository requestRepository;
    private final TransactionProcessingService processingService;

    @Transactional
    public TransactionApprovalRequestResponse createRequest(TransactionApprovalRequestPayload payload, String requestedBy) {
        RequestedTransactionType type = parseType(payload.getTransactionType());
        validateTypeTargets(type, payload.getAccountId(), payload.getLoanId());

        TransactionApprovalRequest request = TransactionApprovalRequest.builder()
            .requestId("TREQ-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase(Locale.ROOT))
            .accountId(payload.getAccountId())
            .loanId(payload.getLoanId())
            .transactionType(type)
            .amount(payload.getTransactionAmount())
            .transactionDate(payload.getTransactionDate())
            .note(payload.getNote())
            .requestedBy(requestedBy)
            .status(ApprovalStatus.PENDING)
            .build();

        return toResponse(requestRepository.save(request));
    }

    public List<TransactionApprovalRequestResponse> listRequests(String status) {
        if (status == null || status.isBlank()) {
            return requestRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
        }
        ApprovalStatus s = ApprovalStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        return requestRepository.findByStatusOrderByCreatedAtDesc(s).stream().map(this::toResponse).toList();
    }

    @Transactional
    public TransactionApprovalRequestResponse decline(String requestId, String actor, String decisionNote) {
        TransactionApprovalRequest request = loadPendingRequest(requestId);
        request.setStatus(ApprovalStatus.DECLINED);
        request.setDecidedBy(actor);
        request.setDecisionNote(decisionNote != null ? decisionNote : "Declined during manual review");
        request.setDecidedAt(java.time.LocalDateTime.now());
        return toResponse(requestRepository.save(request));
    }

    @Transactional
    public TransactionApprovalRequestResponse approveAndExecute(String requestId, String actor, String decisionNote) {
        TransactionApprovalRequest request = loadPendingRequest(requestId);

        request.setStatus(ApprovalStatus.APPROVED);
        request.setDecidedBy(actor);
        request.setDecisionNote(decisionNote != null ? decisionNote : "Approved");
        request.setDecidedAt(java.time.LocalDateTime.now());
        requestRepository.save(request);

        TransactionRequest txRequest = TransactionRequest.builder()
            .transactionAmount(request.getAmount())
            .transactionDate(request.getTransactionDate())
            .note(request.getNote())
            .build();

        try {
            TransactionResponse response;
            switch (request.getTransactionType()) {
                case DEPOSIT -> response = processingService.processDeposit(request.getAccountId(), txRequest);
                case WITHDRAWAL -> response = processingService.processWithdrawal(request.getAccountId(), txRequest);
                case LOAN_REPAYMENT -> response = processingService.processLoanRepayment(request.getLoanId(), txRequest);
                default -> throw new IllegalStateException("Unsupported transaction type: " + request.getTransactionType());
            }

            if ("FAILED".equalsIgnoreCase(response.getStatus())) {
                request.setStatus(ApprovalStatus.EXECUTION_FAILED);
            } else {
                request.setStatus(ApprovalStatus.EXECUTED);
            }
            request.setExecutedTransactionId(response.getTransactionId());
        } catch (Exception ex) {
            log.error("Execution failed for request {}: {}", requestId, ex.getMessage(), ex);
            request.setStatus(ApprovalStatus.EXECUTION_FAILED);
            request.setDecisionNote((request.getDecisionNote() == null ? "" : request.getDecisionNote() + " | ")
                + "Execution error: " + ex.getMessage());
        }

        return toResponse(requestRepository.save(request));
    }

    private TransactionApprovalRequest loadPendingRequest(String requestId) {
        TransactionApprovalRequest request = requestRepository.findByRequestId(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Request not found: " + requestId));
        if (request.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Request is already decided: " + request.getStatus());
        }
        return request;
    }

    private RequestedTransactionType parseType(String raw) {
        try {
            return RequestedTransactionType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("transactionType must be DEPOSIT, WITHDRAWAL, or LOAN_REPAYMENT");
        }
    }

    private void validateTypeTargets(RequestedTransactionType type, Long accountId, Long loanId) {
        if ((type == RequestedTransactionType.DEPOSIT || type == RequestedTransactionType.WITHDRAWAL) && accountId == null) {
            throw new IllegalArgumentException("accountId is required for DEPOSIT/WITHDRAWAL");
        }
        if (type == RequestedTransactionType.LOAN_REPAYMENT && loanId == null) {
            throw new IllegalArgumentException("loanId is required for LOAN_REPAYMENT");
        }
    }

    private TransactionApprovalRequestResponse toResponse(TransactionApprovalRequest req) {
        return TransactionApprovalRequestResponse.builder()
            .requestId(req.getRequestId())
            .accountId(req.getAccountId())
            .loanId(req.getLoanId())
            .transactionType(req.getTransactionType().name())
            .transactionAmount(req.getAmount())
            .transactionDate(req.getTransactionDate())
            .note(req.getNote())
            .requestedBy(req.getRequestedBy())
            .status(req.getStatus().name())
            .decidedBy(req.getDecidedBy())
            .decisionNote(req.getDecisionNote())
            .decidedAt(req.getDecidedAt())
            .executedTransactionId(req.getExecutedTransactionId())
            .createdAt(req.getCreatedAt())
            .updatedAt(req.getUpdatedAt())
            .build();
    }
}

