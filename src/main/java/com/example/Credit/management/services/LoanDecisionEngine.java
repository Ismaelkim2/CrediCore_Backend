package com.example.Credit.management.services;

import com.example.Credit.management.DTO.LoanAssessmentRequest;
import com.example.Credit.management.Model.Account;
import com.example.Credit.management.Model.Loan;
import com.example.Credit.management.Model.LoanAssessmentResponse;
import com.example.Credit.management.Model.LoanDecisionAudit;
import com.example.Credit.management.Model.LoanDecision;
import com.example.Credit.management.Repository.AccountRepository;
import com.example.Credit.management.Repository.LoanDecisionAuditRepository;
import com.example.Credit.management.Repository.LoanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LoanDecisionEngine {

    private final LoanRepository loanRepository;
    private final AccountRepository accountRepository;

    private final CreditScoringService creditScoringService;
    private final FraudDetectionService fraudDetectionService;
    private final BehavioralAnalysisService behavioralAnalysisService;

    /*
     * IMPORTANT:
     *
     * This service is now the orchestration layer.
     *
     * The actual affordability / eligible amount / duration
     * calculation belongs to LoanEligibilityService.
     */
    private final LoanEligibilityService loanEligibilityService;

    private final LoanDecisionAuditRepository auditRepository;

    // ==============================================================
    // MAIN ASSESSMENT
    // ==============================================================

    public LoanAssessmentResponse assess(
            LoanAssessmentRequest request) {

        // ----------------------------------------------------------
        // 1. VALIDATE REQUEST
        // ----------------------------------------------------------

        if (request == null) {
            throw new IllegalArgumentException(
                    "Loan assessment request is required."
            );
        }

        if (request.getNationalId() == null
                || request.getNationalId().isBlank()) {

            throw new IllegalArgumentException(
                    "National ID is required for credit assessment."
            );
        }

        if (request.getRequestedAmount() == null
                || request.getRequestedAmount() <= 0) {

            throw new IllegalArgumentException(
                    "Requested loan amount must be greater than zero."
            );
        }

        // ----------------------------------------------------------
        // 2. FETCH CUSTOMER
        // ----------------------------------------------------------

        Account account =
                accountRepository
                        .findByNationalId(
                                request.getNationalId()
                        )
                        .orElseThrow(
                                () -> new RuntimeException(
                                        "Customer not found."
                                )
                        );

        // ----------------------------------------------------------
        // 3. FETCH CUSTOMER LOAN HISTORY
        // ----------------------------------------------------------

        List<Loan> previousLoans =
                loanRepository
                        .findByAccount_NationalId(
                                request.getNationalId()
                        );

        if (previousLoans == null) {
            previousLoans = List.of();
        }

        // ----------------------------------------------------------
        // 4. RISK SCORE
        // ----------------------------------------------------------

        int riskScore =
                creditScoringService.calculateRiskScore(
                        previousLoans,
                        account,
                        request.getRequestedAmount(),
                        request.getMonthlyIncome()
                );

        riskScore =
                clampScore(riskScore);

        // ----------------------------------------------------------
        // 5. BEHAVIOURAL SCORE
        // ----------------------------------------------------------

        int behavioralScore =
                behavioralAnalysisService
                        .calculateBehavioralScore(
                                previousLoans
                        );

        behavioralScore =
                clampScore(behavioralScore);

        // ----------------------------------------------------------
        // 6. FRAUD DETECTION
        // ----------------------------------------------------------

        boolean fraudFlag =
                fraudDetectionService.detectFraud(
                        previousLoans,
                        request.getRequestedAmount(),
                        request.getPhoneNumber()
                );

        // ----------------------------------------------------------
        // 7. COMPLETED LOANS
        // ----------------------------------------------------------

        int completedLoans =
                (int) previousLoans.stream()
                        .filter(this::isCompletedLoan)
                        .count();

        // ----------------------------------------------------------
        // 8. OVERDUE LOANS
        // ----------------------------------------------------------

        int overdueLoans =
                (int) previousLoans.stream()
                        .filter(
                                loan ->
                                        creditScoringService
                                                .calculateDaysPastDue(
                                                        loan
                                                ) > 30
                        )
                        .count();

        // ----------------------------------------------------------
        // 9. MAXIMUM DPD
        // ----------------------------------------------------------

        int maxDpd =
                previousLoans.stream()
                        .mapToInt(
                                creditScoringService
                                        ::calculateDaysPastDue
                        )
                        .max()
                        .orElse(0);

        // ----------------------------------------------------------
        // 10. PAR CATEGORY
        // ----------------------------------------------------------

        String parCategory =
                determineParCategory(maxDpd);

        // ----------------------------------------------------------
        // 11. PREDICTIVE RISK
        // ----------------------------------------------------------

        String predictiveRisk =
                determinePredictiveRisk(riskScore);

        // ----------------------------------------------------------
        // 12. BACKEND ELIGIBILITY ENGINE
        // ----------------------------------------------------------
        //
        // IMPORTANT:
        //
        // We now let ONE backend authority determine:
        //
        //      - eligible amount
        //      - approved duration
        //      - interest rate
        //      - affordability
        //      - risk tier
        //
        // The borrower cannot override these values.
        //
        // ----------------------------------------------------------

        LoanApplicationDTOAdapter adapter =
                new LoanApplicationDTOAdapter(
                        account,
                        request
                );

        LoanDecision eligibilityDecision =
                loanEligibilityService.assessEligibility(
                        adapter.toDTO()
                );

        double eligibleAmount =
                eligibilityDecision
                        .getRecommendedAmount();

        Integer approvedDurationWeeks =
                eligibilityDecision
                        .getApprovedDurationWeeks();

        Double interestRate =
                eligibilityDecision
                        .getInterestRate();

        // ----------------------------------------------------------
        // 13. FINAL CREDIT RECOMMENDATION
        // ----------------------------------------------------------

        String recommendation;

        String aiReason;

        int aiConfidence;

        /*
         * Fraud has absolute priority.
         */
        if (fraudFlag) {

            recommendation =
                    "BLOCK & INVESTIGATE";

            aiReason =
                    "Fraud indicators were detected during "
                            + "the backend assessment.";

            aiConfidence = 98;

            /*
             * A blocked application must never receive an
             * approved duration.
             */
            approvedDurationWeeks = null;

        }

        /*
         * Existing NPL exposure has priority over ordinary
         * risk-score approval.
         */
        else if ("NPL".equalsIgnoreCase(parCategory)) {

            recommendation =
                    "DECLINE APPLICATION";

            aiReason =
                    "Existing loan exposure contains a "
                            + "non-performing loan.";

            aiConfidence = 97;

            approvedDurationWeeks = null;

        }

        /*
         * Eligibility engine has final authority over whether
         * the requested amount is affordable.
         */
        else if (!eligibilityDecision.isEligible()) {

            recommendation =
                    "DECLINE APPLICATION";

            aiReason =
                    eligibilityDecision.getReason();

            aiConfidence = 94;

            approvedDurationWeeks = null;

        }

        /*
         * High-quality borrower.
         */
        else if (riskScore >= 85
                && behavioralScore >= 80) {

            recommendation =
                    "VIP PRIORITY APPROVAL";

            aiReason =
                    "Strong credit risk profile and "
                            + "positive repayment behaviour.";

            aiConfidence = 96;
        }

        /*
         * Low-risk borrower.
         */
        else if (riskScore >= 70
                && behavioralScore >= 65) {

            recommendation =
                    "AUTO APPROVE";

            aiReason =
                    "Low-risk borrower with acceptable "
                            + "repayment behaviour and affordability.";

            aiConfidence = 92;
        }

        /*
         * Moderate risk.
         */
        else if (riskScore >= 50) {

            recommendation =
                    "FAST TRACK REVIEW";

            aiReason =
                    "Moderate credit risk. Backend affordability "
                            + "requirements are satisfied, but "
                            + "additional review is recommended.";

            aiConfidence = 82;
        }

        /*
         * High risk.
         */
        else if (riskScore >= 35) {

            recommendation =
                    "HIGH RISK MONITORING";

            aiReason =
                    "Weak credit risk indicators require "
                            + "enhanced underwriting review.";

            aiConfidence = 72;
        }

        /*
         * Very high risk.
         */
        else {

            recommendation =
                    "DECLINE APPLICATION";

            aiReason =
                    "Credit risk score is below the minimum "
                            + "acceptable lending threshold.";

            aiConfidence = 95;

            approvedDurationWeeks = null;
        }

        // ----------------------------------------------------------
        // 14. SAFETY CHECK
        // ----------------------------------------------------------

        /*
         * A non-approved decision cannot expose an approved term.
         */
        if (isDecliningRecommendation(recommendation)) {
            approvedDurationWeeks = null;
        }

        // ----------------------------------------------------------
        // 15. AUDIT
        // ----------------------------------------------------------

        LoanDecisionAudit audit =
                new LoanDecisionAudit();

        audit.setNationalId(
                request.getNationalId()
        );

        audit.setRiskScore(
                riskScore
        );

        audit.setRecommendation(
                recommendation
        );

        audit.setAiReason(
                aiReason
        );

        audit.setFraudFlag(
                fraudFlag
        );

        audit.setEligibleAmount(
                eligibleAmount
        );

        audit.setAiConfidence(
                aiConfidence
        );

        audit.setDecisionDate(
                LocalDateTime.now()
        );

        /*
         * If LoanDecisionAudit later receives:
         *
         * approvedDurationWeeks
         * interestRate
         * behavioralScore
         * parCategory
         *
         * they should also be persisted here.
         */

        auditRepository.save(audit);

        // ----------------------------------------------------------
        // 16. RESPONSE
        // ----------------------------------------------------------

        return LoanAssessmentResponse.builder()

                .riskScore(
                        riskScore
                )

                .behavioralScore(
                        behavioralScore
                )

                .fraudFlag(
                        fraudFlag
                )

                .completedLoans(
                        completedLoans
                )

                .overdueLoans(
                        overdueLoans
                )

                .parCategory(
                        parCategory
                )

                .predictiveRisk(
                        predictiveRisk
                )

                .recommendation(
                        recommendation
                )

                .aiReason(
                        aiReason
                )

                .aiConfidence(
                        aiConfidence
                )

                .eligibleAmount(
                        eligibleAmount
                )

                /*
                 * These two fields require the corresponding fields
                 * to exist in LoanAssessmentResponse.
                 */
                .approvedDurationWeeks(
                        approvedDurationWeeks
                )

                .interestRate(
                        interestRate
                )

                .build();
    }

    // ==============================================================
    // COMPLETED LOAN
    // ==============================================================

    private boolean isCompletedLoan(
            Loan loan) {

        if (loan == null) {
            return false;
        }

        double amountPaid =
                loan.getAmountPaid() != null
                        ? loan.getAmountPaid()
                        : 0.0;

        double amount =
                loan.getAmount() != null
                        ? loan.getAmount()
                        : 0.0;

        return amount > 0
                && amountPaid >= amount;
    }

    // ==============================================================
    // PAR CLASSIFICATION
    // ==============================================================

    private String determineParCategory(
            int maxDpd) {

        if (maxDpd >= 90) {
            return "NPL";
        }

        if (maxDpd >= 30) {
            return "PAR 30";
        }

        if (maxDpd > 0) {
            return "PAR 1";
        }

        return "CURRENT";
    }

    // ==============================================================
    // PREDICTIVE RISK
    // ==============================================================

    private String determinePredictiveRisk(
            int riskScore) {

        if (riskScore >= 80) {
            return "LOW";
        }

        if (riskScore >= 50) {
            return "MEDIUM";
        }

        return "HIGH";
    }

    // ==============================================================
    // RECOMMENDATION CLASSIFICATION
    // ==============================================================

    private boolean isDecliningRecommendation(
            String recommendation) {

        if (recommendation == null) {
            return true;
        }

        return recommendation.equalsIgnoreCase(
                "DECLINE APPLICATION"
        )
                || recommendation.equalsIgnoreCase(
                "BLOCK & INVESTIGATE"
        );
    }

    // ==============================================================
    // SCORE SAFETY
    // ==============================================================

    private int clampScore(
            int score) {

        return Math.max(
                0,
                Math.min(
                        score,
                        100
                )
        );
    }

    // ==============================================================
    // INTERNAL DTO ADAPTER
    // ==============================================================

    /**
     * Keeps the eligibility engine independent from the
     * intelligence request DTO.
     *
     * IMPORTANT:
     *
     * No duration is copied from the request.
     */
    private static class LoanApplicationDTOAdapter {

        private final Account account;
        private final LoanAssessmentRequest request;

        private LoanApplicationDTOAdapter(
                Account account,
                LoanAssessmentRequest request) {

            this.account = account;
            this.request = request;
        }

        private com.example.Credit.management.DTO.LoanApplicationDTO toDTO() {

            com.example.Credit.management.DTO.LoanApplicationDTO dto =
                    new com.example.Credit.management.DTO.LoanApplicationDTO();

            /*
             * Requested amount comes from the borrower.
             *
             * Financial information should ideally come from the
             * verified Account profile rather than blindly trusting
             * the assessment request.
             */

            dto.setAmount(
                    request.getRequestedAmount()
            );

            dto.setMonthlyIncome(
                    account.getMonthlyIncome()
            );

            dto.setMonthlyExpenses(
                    account.getMonthlyExpenses()
            );

            /*
             * DELIBERATELY NOT SET:
             *
             * dto.setDuration(...)
             *
             * Duration belongs to the backend.
             */

            return dto;
        }
    }
}