package com.fyp.kyc.repository;

import com.fyp.kyc.entity.DidRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DidRecordRepository extends JpaRepository<DidRecord, Long> {
    Optional<DidRecord> findByDidId(String didId);
    Optional<DidRecord> findByCnicHash(String cnicHash);
    Optional<DidRecord> findByKycReferenceId(String kycReferenceId);
}
