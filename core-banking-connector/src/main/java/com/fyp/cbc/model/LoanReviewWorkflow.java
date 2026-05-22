package com.fyp.cbc.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "loan_review_workflow")
@Getter
@Setter
public class LoanReviewWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long loanId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private LoanWorkflowStatus status;

    @Column(length = 120)
    private String forwardedBy;

    private LocalDateTime forwardedAt;

    @Column(length = 120)
    private String managerDecisionBy;

    private LocalDateTime managerDecisionAt;

    @Column(length = 1000)
    private String note;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

