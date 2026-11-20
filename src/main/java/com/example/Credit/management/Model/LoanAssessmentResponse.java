package com.example.Credit.management.Model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanAssessmentResponse {

    private int riskScore;
    private int behavioralScore;
    private int kycScore;

    private boolean fraudFlag;

    private int daysPastDue;
    private int completedLoans;
    private int overdueLoans;

    private String parCategory;
    private String predictiveRisk;

    private String recommendation;
    private String aiReason;
    private int aiConfidence;

    private double eligibleAmount;

    private Double interestRate;

    private Integer approvedDurationWeeks;
}