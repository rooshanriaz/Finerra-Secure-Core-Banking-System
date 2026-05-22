package com.fyp.kyc.repository;

import com.fyp.kyc.entity.KycRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface KycRecordRepository extends JpaRepository<KycRecord, Long> {
    Optional<KycRecord> findByReferenceId(String referenceId);
    Optional<KycRecord> findByCnicHash(String cnicHash);
    Optional<KycRecord> findByDidId(String didId);
    Optional<KycRecord> findByFineractClientId(Long fineractClientId);
    boolean existsByCnicHash(String cnicHash);
}
