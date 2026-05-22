package com.fyp.txn.repository;

import com.fyp.txn.entity.TransactionRecord;
import com.fyp.txn.entity.TransactionRecord.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRecordRepository extends JpaRepository<TransactionRecord, Long> {

    Optional<TransactionRecord> findByTransactionId(String transactionId);

    List<TransactionRecord> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    List<TransactionRecord> findByLoanIdOrderByCreatedAtDesc(Long loanId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM TransactionRecord t " +
           "WHERE t.accountId = :accountId AND t.transactionType = :type " +
           "AND t.status = 'COMPLETED' AND t.createdAt >= :since")
    BigDecimal sumAmountByAccountAndTypeSince(
        @Param("accountId") Long accountId,
        @Param("type") TransactionType type,
        @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(t) FROM TransactionRecord t " +
           "WHERE t.accountId = :accountId AND t.status = 'COMPLETED' " +
           "AND t.createdAt >= :since")
    int countTransactionsByAccountSince(
        @Param("accountId") Long accountId,
        @Param("since") LocalDateTime since);

    List<TransactionRecord> findByCreatedAtBetweenOrderByCreatedAtDesc(
        LocalDateTime from, LocalDateTime to);
}
