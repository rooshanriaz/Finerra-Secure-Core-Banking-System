package com.fyp.fraud.repository;

import com.fyp.fraud.entity.TransactionProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionProfileRepository extends JpaRepository<TransactionProfile, Long> {

    Optional<TransactionProfile> findByAccountId(Long accountId);
}
