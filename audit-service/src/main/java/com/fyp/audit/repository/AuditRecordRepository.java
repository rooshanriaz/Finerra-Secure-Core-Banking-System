package com.fyp.audit.repository;

import com.fyp.audit.entity.AuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {

    Optional<AuditRecord> findByAuditId(String auditId);

    Optional<AuditRecord> findByTransactionId(String transactionId);

    List<AuditRecord> findByAccountIdOrderByRecordedAtDesc(Long accountId);

    List<AuditRecord> findByInitiatedByOrderByRecordedAtDesc(String initiatedBy);

    List<AuditRecord> findByTransactionTypeOrderByRecordedAtDesc(String transactionType);

    List<AuditRecord> findByRecordedAtBetweenOrderByRecordedAtDesc(
        LocalDateTime from, LocalDateTime to);

    @Query("SELECT a FROM AuditRecord a ORDER BY a.recordedAt DESC")
    List<AuditRecord> findRecentAudits();

    /**
     * Get the most recent audit record (for chain linking).
     */
    Optional<AuditRecord> findTopByOrderByIdDesc();

    @Query("SELECT COUNT(a) FROM AuditRecord a WHERE a.blockchainStatus = :status")
    long countByBlockchainStatus(@Param("status") AuditRecord.BlockchainStatus status);
}
