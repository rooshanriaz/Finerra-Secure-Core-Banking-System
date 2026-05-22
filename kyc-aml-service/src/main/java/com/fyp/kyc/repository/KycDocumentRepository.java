package com.fyp.kyc.repository;

import com.fyp.kyc.entity.KycDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KycDocumentRepository extends JpaRepository<KycDocument, Long> {
    List<KycDocument> findByKycReferenceId(String kycReferenceId);
}
