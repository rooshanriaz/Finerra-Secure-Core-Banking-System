package com.fyp.cbc.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fyp.cbc.model.LoanReviewWorkflow;

public interface LoanReviewWorkflowRepository extends JpaRepository<LoanReviewWorkflow, Long> {
    Optional<LoanReviewWorkflow> findByLoanId(Long loanId);
}

