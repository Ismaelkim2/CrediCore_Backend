package com.example.Credit.management.services;

import com.example.Credit.management.DTO.LoanApplicationDTO;
import com.example.Credit.management.Model.LoanDecision;
import org.springframework.stereotype.Service;

@Service
public class LoanEligibilityServiceImpl implements LoanEligibilityService {

    @Override
    public LoanDecision assessEligibility(LoanApplicationDTO dto) {

        // ✅ Defensive coding: handle nulls gracefully
        double income = dto.getMonthlyIncome() != null ? dto.getMonthlyIncome() : 0.0;
        double expenses = dto.getMonthlyExpenses() != null ? dto.getMonthlyExpenses() : 0.0;
        double requestedAmount = dto.getAmount();


        // ✅ Calculate key metrics
        double disposableIncome = income - expenses;
        double debtToIncomeRatio = income > 0 ? (expenses / income) * 100 : 100;
        double maxLoanByIncome = disposableIncome * 10; // conservative 10x multiplier
        double suggestedLoan = Math.min(maxLoanByIncome, requestedAmount);

        // ✅ Default risk rating (could be replaced with credit score lookup)
        String riskLevel = getRiskLevel(debtToIncomeRatio, disposableIncome);

        // ✅ Eligibility determination
        boolean isEligible = disposableIncome > 0 && debtToIncomeRatio < 60 && requestedAmount <= maxLoanByIncome;

        // ✅ Construct decision object
        LoanDecision decision = new LoanDecision();
        decision.setEligible(isEligible);
        decision.setRecommendedAmount(roundToNearest(suggestedLoan, 1000));
        decision.setDebtToIncomeRatio(debtToIncomeRatio);
        decision.setDisposableIncome(disposableIncome);
        decision.setRiskLevel(riskLevel);
        decision.setInterestRate(recommendInterestRate(riskLevel));

        // ✅ Business decision reasoning
        if (!isEligible) {
            if (income <= 0) {
                decision.setReason("Loan declined. No verifiable monthly income provided.");
            } else if (disposableIncome <= 0) {
                decision.setReason("Loan declined. Your monthly expenses exceed or equal your income.");
            } else if (debtToIncomeRatio >= 60) {
                decision.setReason("Loan declined. Debt-to-Income ratio exceeds the allowed limit of 60%.");
            } else if (requestedAmount > maxLoanByIncome) {
                decision.setReason("Requested amount (Ksh " + formatAmount(requestedAmount)
                        + ") exceeds your eligibility based on disposable income. "
                        + "You qualify for up to Ksh " + formatAmount(maxLoanByIncome) + ".");
            } else {
                decision.setReason("Loan not approved. Please review your financial profile.");
            }
        } else {
            decision.setReason("Eligible for the requested amount. Maintain a low DTI for better rates.");
        }

        return decision;
    }

    /** 🔹 Assign risk tier based on DTI and disposable income */
    private String getRiskLevel(double dti, double disposableIncome) {
        if (dti < 30 && disposableIncome > 20000) return "LOW";
        else if (dti < 45 && disposableIncome > 10000) return "MODERATE";
        else return "HIGH";
    }

    /** 🔹 Recommend interest rate dynamically */
    private double recommendInterestRate(String riskLevel) {
        switch (riskLevel.toUpperCase()) {
            case "LOW": return 10.5;
            case "MODERATE": return 14.0;
            default: return 18.0; // high-risk clients get higher rates
        }
    }

    /** 🔹 Format currency values neatly */
    private String formatAmount(double amount) {
        return String.format("%,.2f", amount);
    }

    /** 🔹 Round to nearest Ksh 1,000 for clarity */
    private double roundToNearest(double amount, int nearest) {
        return Math.round(amount / nearest) * nearest;
    }
}
