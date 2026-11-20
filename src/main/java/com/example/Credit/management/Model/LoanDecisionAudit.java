package com.example.Credit.management.Model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "loan_decision_audit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanDecisionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =====================================================
    // CUSTOMER
    // =====================================================

    private String nationalId;

    // =====================================================
    // LOAN REQUEST
    // =====================================================

    private Double requestedAmount;

    private Double eligibleAmount;

    // =====================================================
    // AI SCORES
    // =====================================================

    private Integer riskScore;

    private Integer behavioralScore;

    private Integer aiConfidence;

    private Integer daysPastDue;

    // =====================================================
    // AI DECISION
    // =====================================================

    private String recommendation;

    @Column(length = 3000)
    private String aiReason;

    @Column(length = 3000)
    private String decisionReason;

    // =====================================================
    // FRAUD
    // =====================================================

    private Boolean fraudFlag;

    // =====================================================
    // TIMESTAMPS
    // =====================================================

    private LocalDateTime createdAt;

    private LocalDateTime decisionDate;
}