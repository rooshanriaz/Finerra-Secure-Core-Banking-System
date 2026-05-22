package com.fyp.fraud.repository;

import com.fyp.fraud.entity.FraudAlert;
import com.fyp.fraud.entity.FraudAlert.AlertStatus;
import com.fyp.fraud.entity.FraudAlert.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FraudAlertRepository extends JpaRepository<FraudAlert, Long> {

    Optional<FraudAlert> findByAlertId(String alertId);

    Optional<FraudAlert> findByTransactionId(String transactionId);

    List<FraudAlert> findByStatusOrderByCreatedAtDesc(AlertStatus status);

    List<FraudAlert> findByRiskLevelOrderByCreatedAtDesc(RiskLevel riskLevel);

    List<FraudAlert> findByAccountIdOrderByCreatedAtDesc(Long accountId);

    List<FraudAlert> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);

    List<FraudAlert> findTop50ByOrderByCreatedAtDesc();

    long countByStatus(AlertStatus status);

    long countByRiskLevel(RiskLevel riskLevel);
}
