package com.example.Credit.management.Model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoanDecision {
    private boolean isEligible;
    private double recommendedAmount;
    private double disposableIncome;
    private double debtToIncomeRatio;
    private String riskLevel;
    private double interestRate;
    private String reason;
    private Integer approvedDurationWeeks;
}
