package com.fyp.kyc.repository;

import com.fyp.kyc.entity.AmlCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AmlCheckRepository extends JpaRepository<AmlCheck, Long> {
    List<AmlCheck> findByKycReferenceId(String kycReferenceId);
    List<AmlCheck> findByCnicHash(String cnicHash);
}
