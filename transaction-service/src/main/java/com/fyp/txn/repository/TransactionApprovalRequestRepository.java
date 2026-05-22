package com.fyp.txn.repository;

import com.fyp.txn.entity.TransactionApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionApprovalRequestRepository extends JpaRepository<TransactionApprovalRequest, Long> {
    Optional<TransactionApprovalRequest> findByRequestId(String requestId);
    List<TransactionApprovalRequest> findByStatusOrderByCreatedAtDesc(TransactionApprovalRequest.ApprovalStatus status);
    List<TransactionApprovalRequest> findAllByOrderByCreatedAtDesc();
}

