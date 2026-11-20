package com.example.Credit.management.services;

import com.example.Credit.management.Model.Account;
import com.example.Credit.management.Model.Loan;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class CreditScoringService {

    public int calculateRiskScore(
            List<Loan> previousLoans,
            Account account,
            double requestedAmount,
            double monthlyIncome
    ) {

        double score = 100;

        // =====================================================
        // REPAYMENT PERFORMANCE
        // =====================================================

        double totalBorrowed = previousLoans.stream()
                .mapToDouble(l -> l.getAmount() == null ? 0 : l.getAmount())
                .sum();

        double totalPaid = previousLoans.stream()
                .mapToDouble(l -> l.getAmountPaid() == null ? 0 : l.getAmountPaid())
                .sum();

        double repaymentRatio =
                totalBorrowed > 0
                        ? (totalPaid / totalBorrowed) * 100
                        : 100;

        score -= (100 - repaymentRatio) * 0.35;

        // =====================================================
        // OVERDUE HISTORY
        // =====================================================

        long overdueLoans = previousLoans.stream()
                .filter(l -> calculateDaysPastDue(l) > 30)
                .count();

        score -= overdueLoans * 10;

        // Reward customers with clean repayment history
        if (overdueLoans == 0 && !previousLoans.isEmpty()) {
            score += 15;
        }

        // =====================================================
        // COMPLETED LOANS BONUS
        // =====================================================

        long completedLoans = previousLoans.stream()
                .filter(l -> {

                    Double paid = l.getAmountPaid();
                    Double total = l.getTotalRepayableAmount();

                    return paid != null
                            && total != null
                            && paid >= total;

                })
                .count();

        // Reward loyal customers
        score += Math.min(completedLoans * 3, 30);

        // =====================================================
        // DEBT SERVICE RATIO (DSR)
        // =====================================================

        double dsr = monthlyIncome > 0
                ? (requestedAmount / monthlyIncome) * 100
                : 100;

        if (dsr > 50) {

            score -= 25;

        } else if (dsr < 20) {

            score += 10;
        }

        // =====================================================
        // KYC PENALTIES
        // =====================================================

        if (account.getEmail() == null || account.getEmail().isBlank()) {
            score -= 10;
        }

        if (account.getPhoneNumber() == null || account.getPhoneNumber().isBlank()) {
            score -= 10;
        }

        if (account.getNationalId() == null || account.getNationalId().isBlank()) {
            score -= 5;
        }

        // =====================================================
        // FINAL NORMALIZATION
        // =====================================================

        return (int) Math.max(0, Math.min(100, score));
    }

    // =========================================================
    // DAYS PAST DUE (FIXED - SINGLE CLEAN IMPLEMENTATION)
    // =========================================================

    public int calculateDaysPastDue(Loan loan) {

        if (loan == null) {
            return 0;
        }

        if (loan.getDueDate() == null) {
            return 0;
        }

        Double dueAmount = loan.getDueAmount();
        Double total = loan.getTotalRepayableAmount();

        boolean cleared =
                (dueAmount != null && dueAmount <= 0)
                        || (
                        total != null
                                && loan.getAmountPaid() != null
                                && loan.getAmountPaid() >= total
                )
                        || "PAID".equalsIgnoreCase(
                        loan.getStatus() == null ? "" : loan.getStatus()
                )
                        || "CLEARED".equalsIgnoreCase(
                        loan.getStatus() == null ? "" : loan.getStatus()
                );

        if (cleared) {
            return 0;
        }

        LocalDate dueDate = loan.getDueDate();
        LocalDate today = LocalDate.now();

        if (today.isBefore(dueDate)) {
            return 0;
        }

        return (int) ChronoUnit.DAYS.between(dueDate, today);
    }
}