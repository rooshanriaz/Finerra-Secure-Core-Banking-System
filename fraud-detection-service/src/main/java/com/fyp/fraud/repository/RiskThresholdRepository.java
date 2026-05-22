package com.fyp.fraud.repository;

import com.fyp.fraud.entity.RiskThreshold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RiskThresholdRepository extends JpaRepository<RiskThreshold, Long> {

    Optional<RiskThreshold> findByThresholdName(String thresholdName);
}
