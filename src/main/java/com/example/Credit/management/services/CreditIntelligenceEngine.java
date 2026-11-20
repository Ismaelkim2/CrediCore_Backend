package com.example.Credit.management.services;

import com.example.Credit.management.DTO.LoanAssessmentRequest;
import com.example.Credit.management.Model.Account;
import com.example.Credit.management.Model.Loan;
import com.example.Credit.management.Model.LoanAssessmentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CreditIntelligenceEngine {

    private final CreditScoringService creditScoringService;
    private final FraudDetectionService fraudDetectionService;
    private final BehavioralAnalysisService behavioralAnalysisService;
    private final EligibilityService eligibilityService;

    // =====================================================
    // MAIN CREDIT INTELLIGENCE ENTRY POINT
    // =====================================================

    public LoanAssessmentResponse analyze(
            Account account,
            List<Loan> previousLoans,
            LoanAssessmentRequest request
    ) {

        // -------------------------------------------------
        // INPUT SAFETY
        // -------------------------------------------------

        previousLoans =
                previousLoans == null
                        ? List.of()
                        : previousLoans;

        if (request == null) {

            return buildResponse(
                    "INVALID REQUEST",
                    "Credit assessment request cannot be null.",
                    0,
                    0,
                    0,
                    false,
                    0,
                    0,
                    0,
                    "NOT_FUNDED",
                    "HIGH",
                    0,
                    100
            );
        }

        if (account == null) {

            return buildResponse(
                    "ACCOUNT NOT FOUND",
                    "Customer account could not be found.",
                    0,
                    0,
                    0,
                    false,
                    0,
                    0,
                    0,
                    "NOT_FUNDED",
                    "HIGH",
                    0,
                    100
            );
        }

        // -------------------------------------------------
        // REQUEST VALUES
        // -------------------------------------------------

        double requestedAmount =
                Math.max(
                        0,
                        safe(request.getRequestedAmount())
                );

        double monthlyIncome =
                Math.max(
                        0,
                        safe(request.getMonthlyIncome())
                );

        // -------------------------------------------------
        // CURRENT PORTFOLIO FACILITIES
        // -------------------------------------------------

        /*
         * Only genuinely funded/current facilities are
         * considered active.
         *
         * REJECTED, BLOCKED, CANCELLED, PENDING and
         * UNDER_REVIEW applications are excluded.
         */

        List<Loan> activeFacilities =
                findActiveFacilities(previousLoans);

        // -------------------------------------------------
        // DAYS PAST DUE
        // -------------------------------------------------

        /*
         * We deliberately calculate the WORST DPD across
         * all active facilities.
         *
         * Example:
         *
         * Loan A = 0 DPD
         * Loan B = 45 DPD
         * Loan C = 0 DPD
         *
         * Customer DPD = 45
         * Customer PAR = PAR 30
         */

        int dpd =
                calculateWorstDaysPastDue(
                        activeFacilities
                );

        // -------------------------------------------------
        // CURRENT EXPOSURE
        // -------------------------------------------------

        double outstandingRatio =
                calculateOutstandingRatio(
                        previousLoans
                );

        // -------------------------------------------------
        // CREDIT RISK SCORE
        // -------------------------------------------------

        int riskScore =
                normalizeRisk(
                        creditScoringService.calculateRiskScore(
                                previousLoans,
                                account,
                                requestedAmount,
                                monthlyIncome
                        )
                );

        // -------------------------------------------------
        // BEHAVIORAL SCORE
        // -------------------------------------------------

        int behavioralScore =
                calculateBehavioralScore(
                        previousLoans
                );

        // -------------------------------------------------
        // KYC SCORE
        // -------------------------------------------------

        int kycScore =
                calculateKycScore(
                        account
                );

        // -------------------------------------------------
        // FRAUD DETECTION
        // -------------------------------------------------

        /*
         * Fraud detection is deliberately separated from
         * ordinary credit risk.
         *
         * TRUE  = qualifying fraud/abnormality evidence
         * FALSE = no sufficient fraud evidence
         */

        boolean fraudFlag =
                fraudDetectionService.detectFraud(
                        previousLoans,
                        requestedAmount,
                        request.getPhoneNumber()
                );

        // -------------------------------------------------
        // HISTORICAL PERFORMANCE
        // -------------------------------------------------

        int completedLoans =
                countCompletedLoans(
                        previousLoans
                );

        int overdueLoans =
                countOverdueLoans(
                        previousLoans
                );

        // -------------------------------------------------
        // PORTFOLIO PAR CLASSIFICATION
        // -------------------------------------------------

        /*
         * PAR is based on funded facilities only.
         *
         * Rejected/unfunded applications do not become
         * CURRENT, PAR or NPL.
         */

        String parCategory =
                calculateParCategory(
                        previousLoans
                );

        // -------------------------------------------------
        // ELIGIBILITY
        // -------------------------------------------------

        double eligibleAmount =
                Math.max(
                        0,
                        eligibilityService.calculateEligibleLimit(
                                account,
                                riskScore,
                                monthlyIncome
                        )
                );

        // -------------------------------------------------
        // FINAL CREDIT SCORE
        // -------------------------------------------------

        /*
         * Final score combines:
         *
         * Credit risk     = 45%
         * Behaviour       = 30%
         * KYC             = 25%
         *
         * Then applies:
         *
         * - DPD penalty
         * - Exposure penalty
         * - Fraud override
         */

        double finalScore =
                calculateFinalScore(
                        riskScore,
                        behavioralScore,
                        kycScore,
                        dpd,
                        outstandingRatio,
                        fraudFlag
                );

        // -------------------------------------------------
        // PREDICTIVE RISK
        // -------------------------------------------------

        /*
         * IMPORTANT:
         *
         * Predictive risk is based on FINAL SCORE, not
         * raw riskScore.
         *
         * This prevents contradictions such as:
         *
         * Final Score = 0
         * Predictive Risk = LOW
         */

        String predictiveRisk =
                calculatePredictiveRisk(
                        (int) Math.round(finalScore)
                );

        // -------------------------------------------------
        // FINAL DECISION
        // -------------------------------------------------

        Decision decision =
                applyDecisionRules(
                        fraudFlag,
                        parCategory,
                        finalScore,
                        behavioralScore,
                        completedLoans,
                        eligibleAmount,
                        requestedAmount,
                        monthlyIncome,
                        kycScore
                );

        // -------------------------------------------------
        // RESPONSE
        // -------------------------------------------------

        return buildResponse(
                decision.recommendation,
                decision.reason,
                riskScore,
                behavioralScore,
                kycScore,
                fraudFlag,
                dpd,
                completedLoans,
                overdueLoans,
                parCategory,
                predictiveRisk,
                eligibleAmount,
                decision.confidence
        );
    }

    // =====================================================
    // DECISION ENGINE
    // =====================================================

    private Decision applyDecisionRules(
            boolean fraudFlag,
            String parCategory,
            double finalScore,
            int behavioralScore,
            int completedLoans,
            double eligibleAmount,
            double requestedAmount,
            double monthlyIncome,
            int kycScore
    ) {

        // -------------------------------------------------
        // RULE 1 — INVALID AMOUNT
        // -------------------------------------------------

        if (requestedAmount <= 0) {

            return new Decision(
                    "DECLINE",
                    "Requested loan amount must be greater than zero.",
                    100
            );
        }

        // -------------------------------------------------
        // RULE 2 — INVALID INCOME
        // -------------------------------------------------

        if (monthlyIncome <= 0) {

            return new Decision(
                    "MANUAL REVIEW",
                    "Monthly income is missing or invalid. Income verification is required.",
                    98
            );
        }

        // -------------------------------------------------
        // RULE 3 — KYC
        // -------------------------------------------------

        if (kycScore < 60) {

            return new Decision(
                    "MANUAL REVIEW",
                    "Customer KYC profile is incomplete or insufficient for automated approval.",
                    98
            );
        }

        // -------------------------------------------------
        // RULE 4 — FRAUD
        // -------------------------------------------------

        /*
         * Fraud has priority over ordinary credit approval.
         *
         * TRUE must always stop automatic lending.
         */

        if (fraudFlag) {

            return new Decision(
                    "BLOCK & INVESTIGATE",
                    "Fraud or abnormal credit activity indicators were detected. Automated lending is blocked pending investigation.",
                    99
            );
        }

        // -------------------------------------------------
        // RULE 5 — NPL
        // -------------------------------------------------

        if ("NPL".equals(parCategory)) {

            return new Decision(
                    "DECLINE",
                    "Customer has a non-performing loan exposure. New lending is not eligible for automated approval.",
                    98
            );
        }

        // -------------------------------------------------
        // RULE 6 — PAR 60
        // -------------------------------------------------

        if ("PAR 60".equals(parCategory)) {

            return new Decision(
                    "MANUAL REVIEW",
                    "Customer has a credit facility 60 or more days past due. Credit officer review is required.",
                    97
            );
        }

        // -------------------------------------------------
        // RULE 7 — PAR 30
        // -------------------------------------------------

        if ("PAR 30".equals(parCategory)) {

            return new Decision(
                    "MANUAL REVIEW",
                    "Customer has a credit facility 30 or more days past due. Automated approval is suspended.",
                    95
            );
        }

        // -------------------------------------------------
        // RULE 8 — NO CREDIT LIMIT
        // -------------------------------------------------

        if (eligibleAmount <= 0) {

            return new Decision(
                    "DECLINE",
                    "Customer does not currently qualify for a positive credit limit.",
                    96
            );
        }

        // -------------------------------------------------
        // RULE 9 — REQUEST ABOVE LIMIT
        // -------------------------------------------------

        if (requestedAmount > eligibleAmount) {

            if (finalScore >= 70) {

                return new Decision(
                        "MANUAL REVIEW",
                        String.format(
                                Locale.ROOT,
                                "Customer profile is strong, but requested amount of Ksh %.0f exceeds the automated eligible limit of Ksh %.0f.",
                                requestedAmount,
                                eligibleAmount
                        ),
                        92
                );
            }

            return new Decision(
                    "DECLINE",
                    String.format(
                            Locale.ROOT,
                            "Requested amount of Ksh %.0f exceeds eligible limit of Ksh %.0f.",
                            requestedAmount,
                            eligibleAmount
                    ),
                    94
            );
        }

        // -------------------------------------------------
        // RULE 10 — VERY LOW SCORE
        // -------------------------------------------------

        if (finalScore < 40) {

            return new Decision(
                    "DECLINE",
                    String.format(
                            Locale.ROOT,
                            "Overall credit assessment score of %.0f is below the minimum automated lending threshold.",
                            finalScore
                    ),
                    96
            );
        }

        // -------------------------------------------------
        // RULE 11 — WEAK REPAYMENT BEHAVIOR
        // -------------------------------------------------

        if (behavioralScore < 50) {

            return new Decision(
                    "MANUAL REVIEW",
                    "Repayment behavior does not meet the threshold for automatic approval.",
                    90
            );
        }

        // -------------------------------------------------
        // RULE 12 — VIP CUSTOMER
        // -------------------------------------------------

        if (completedLoans >= 3
                && behavioralScore >= 80
                && finalScore >= 80
                && requestedAmount <= eligibleAmount) {

            return new Decision(
                    "VIP APPROVAL",
                    "Customer demonstrates excellent repayment history, strong behavioral performance and sufficient credit capacity.",
                    99
            );
        }

        // -------------------------------------------------
        // RULE 13 — STANDARD APPROVAL
        // -------------------------------------------------

        if (finalScore >= 70
                && behavioralScore >= 65
                && requestedAmount <= eligibleAmount) {

            return new Decision(
                    "APPROVED",
                    "Customer meets automated credit, behavioral, KYC and affordability requirements.",
                    96
            );
        }

        // -------------------------------------------------
        // RULE 14 — BORDERLINE CASE
        // -------------------------------------------------

        if (finalScore >= 50) {

            return new Decision(
                    "MANUAL REVIEW",
                    "Customer has acceptable but non-strong credit characteristics. Additional credit review is required.",
                    88
            );
        }

        // -------------------------------------------------
        // RULE 15 — FINAL DECLINE
        // -------------------------------------------------

        return new Decision(
                "DECLINE",
                "Customer does not meet the minimum automated lending criteria.",
                95
        );
    }

    // =====================================================
    // FINAL WEIGHTED SCORE
    // =====================================================

    private double calculateFinalScore(
            int riskScore,
            int behavioralScore,
            int kycScore,
            int dpd,
            double outstandingRatio,
            boolean fraudFlag
    ) {

        /*
         * =================================================
         * BASE WEIGHTING
         * =================================================
         *
         * Credit risk = 45%
         * Behaviour   = 30%
         * KYC         = 25%
         *
         * Total       = 100%
         */

        double score =
                (riskScore * 0.45)
                        + (behavioralScore * 0.30)
                        + (kycScore * 0.25);

        // -------------------------------------------------
        // DELINQUENCY PENALTY
        // -------------------------------------------------

        /*
         * Only ONE penalty is applied according to the
         * highest DPD severity.
         */

        if (dpd >= 90) {

            score -= 30;

        } else if (dpd >= 60) {

            score -= 20;

        } else if (dpd >= 30) {

            score -= 15;
        }

        // -------------------------------------------------
        // HIGH EXPOSURE PENALTY
        // -------------------------------------------------

        /*
         * Only ONE exposure penalty is applied.
         */

        if (outstandingRatio > 0.90) {

            score -= 20;

        } else if (outstandingRatio > 0.80) {

            score -= 10;
        }

        // -------------------------------------------------
        // FRAUD OVERRIDE
        // -------------------------------------------------

        /*
         * A genuine fraud flag overrides the numerical
         * score.
         */

        if (fraudFlag) {
            score = 0;
        }

        return Math.max(
                0,
                Math.min(
                        100,
                        score
                )
        );
    }

    // =====================================================
    // BEHAVIORAL SCORE
    // =====================================================

    private int calculateBehavioralScore(
            List<Loan> loans
    ) {

        if (loans == null || loans.isEmpty()) {
            return 0;
        }

        int behavioral =
                behavioralAnalysisService
                        .calculateBehavioralScore(
                                loans
                        );

        int repaymentDiscipline =
                behavioralAnalysisService
                        .calculateRepaymentDiscipline(
                                loans
                        );

        /*
         * Both components are expected to be 0–100.
         *
         * Average rather than add them.
         */

        return normalizeBehavior(
                (behavioral + repaymentDiscipline) / 2
        );
    }

    // =====================================================
    // FIND ALL CURRENT ACTIVE FUNDED FACILITIES
    // =====================================================

    private List<Loan> findActiveFacilities(
            List<Loan> loans
    ) {

        if (loans == null || loans.isEmpty()) {
            return List.of();
        }

        return loans.stream()
                .filter(this::isCurrentlyActiveFacility)
                .toList();
    }

    // =====================================================
    // CURRENT ACTIVE FACILITY
    // =====================================================

    private boolean isCurrentlyActiveFacility(
            Loan loan
    ) {

        if (loan == null) {
            return false;
        }

        String status =
                normalizeStatus(
                        loan.getStatus()
                );

        /*
         * STATUS is authoritative for current portfolio
         * classification.
         *
         * We deliberately DO NOT require disbursedDate.
         *
         * This protects the engine against historical data
         * where ACTIVE loans have a missing disbursedDate.
         */

        return "ACTIVE".equals(status)
                || "DISBURSED".equals(status)
                || "PARTIALLY_PAID".equals(status);
    }

    // =====================================================
    // HISTORICAL FUNDED LOAN
    // =====================================================

    private boolean isHistoricalFundedLoan(
            Loan loan
    ) {

        if (loan == null) {
            return false;
        }

        String status =
                normalizeStatus(
                        loan.getStatus()
                );

        /*
         * Explicit funded statuses.
         */

        if ("ACTIVE".equals(status)
                || "DISBURSED".equals(status)
                || "PARTIALLY_PAID".equals(status)
                || "COMPLETED".equals(status)) {

            return true;
        }

        /*
         * A disbursement date is additional evidence of
         * historical funding.
         *
         * However, explicit rejection, cancellation or
         * blocking takes precedence.
         */

        if (loan.getDisbursedDate() != null) {

            return !"REJECTED".equals(status)
                    && !"CANCELLED".equals(status)
                    && !"BLOCKED".equals(status)
                    && !"PENDING".equals(status)
                    && !"UNDER_REVIEW".equals(status)
                    && !"APPROVED".equals(status);
        }

        return false;
    }

    // =====================================================
    // NORMALIZE LOAN STATUS
    // =====================================================

    private String normalizeStatus(
            String status
    ) {

        if (status == null
                || status.isBlank()) {

            return "";
        }

        return status.trim()
                .toUpperCase(Locale.ROOT)
                .replace("-", "_")
                .replace(" ", "_");
    }

    // =====================================================
    // OUTSTANDING RATIO
    // =====================================================

    private double calculateOutstandingRatio(
            List<Loan> loans
    ) {

        if (loans == null || loans.isEmpty()) {
            return 0;
        }

        double totalOutstanding = 0;
        double totalRepayable = 0;

        for (Loan loan : loans) {

            /*
             * Rejected/unfunded loans are excluded.
             */

            if (!isCurrentlyActiveFacility(loan)) {
                continue;
            }

            double outstanding =
                    loan.getDueAmount() == null
                            ? 0
                            : Math.max(
                            0,
                            loan.getDueAmount()
                    );

            double repayable =
                    loan.getTotalRepayableAmount() == null
                            ? 0
                            : Math.max(
                            0,
                            loan.getTotalRepayableAmount()
                    );

            totalOutstanding += outstanding;
            totalRepayable += repayable;
        }

        if (totalRepayable <= 0) {
            return 0;
        }

        return Math.max(
                0,
                Math.min(
                        1,
                        totalOutstanding
                                / totalRepayable
                )
        );
    }

    // =====================================================
    // KYC SCORE
    // =====================================================

    private int calculateKycScore(
            Account account
    ) {

        if (account == null) {
            return 0;
        }

        int score = 0;

        if (account.getEmail() != null
                && !account.getEmail().isBlank()) {

            score += 40;
        }

        if (account.getPhoneNumber() != null
                && !account.getPhoneNumber().isBlank()) {

            score += 40;
        }

        if (account.getNationalId() != null
                && !account.getNationalId().isBlank()) {

            score += 20;
        }

        return Math.min(
                score,
                100
        );
    }

    // =====================================================
    // COMPLETED LOANS
    // =====================================================

    private int countCompletedLoans(
            List<Loan> loans
    ) {

        if (loans == null || loans.isEmpty()) {
            return 0;
        }

        return (int) loans.stream()
                .filter(this::isHistoricalFundedLoan)
                .filter(this::isCompletedLoan)
                .count();
    }

    // =====================================================
    // COMPLETED LOAN CHECK
    // =====================================================

    private boolean isCompletedLoan(
            Loan loan
    ) {

        if (loan == null) {
            return false;
        }

        String status =
                normalizeStatus(
                        loan.getStatus()
                );

        /*
         * Explicit COMPLETED status is authoritative.
         */

        if ("COMPLETED".equals(status)) {
            return true;
        }

        /*
         * Fallback for older records where status may not
         * have been updated but the facility is fully paid.
         */

        Double amountPaid =
                loan.getAmountPaid();

        Double totalRepayable =
                loan.getTotalRepayableAmount();

        return amountPaid != null
                && totalRepayable != null
                && totalRepayable > 0
                && amountPaid >= totalRepayable;
    }

    // =====================================================
    // OVERDUE LOANS
    // =====================================================

    private int countOverdueLoans(
            List<Loan> loans
    ) {

        if (loans == null || loans.isEmpty()) {
            return 0;
        }

        /*
         * Only currently active funded facilities are
         * counted as overdue.
         *
         * Rejected applications are never overdue loans.
         */

        return (int) loans.stream()
                .filter(this::isCurrentlyActiveFacility)
                .filter(loan ->
                        calculateDaysPastDue(loan) > 30
                )
                .count();
    }

    // =====================================================
    // PAR CLASSIFICATION
    // =====================================================

    private String calculateParCategory(
            List<Loan> loans
    ) {

        if (loans == null || loans.isEmpty()) {
            return "NOT_FUNDED";
        }

        /*
         * -------------------------------------------------
         * STEP 1
         * -------------------------------------------------
         *
         * Identify currently active funded facilities.
         */

        List<Loan> activeFacilities =
                findActiveFacilities(
                        loans
                );

        // -------------------------------------------------
        // ACTIVE FUNDED FACILITY EXISTS
        // -------------------------------------------------

        if (!activeFacilities.isEmpty()) {

            /*
             * Calculate worst DPD across every active
             * facility.
             */

            int worstDpd =
                    calculateWorstDaysPastDue(
                            activeFacilities
                    );

            // ---------------------------------------------
            // 90+ DAYS
            // ---------------------------------------------

            if (worstDpd >= 90) {
                return "NPL";
            }

            // ---------------------------------------------
            // 60–89 DAYS
            // ---------------------------------------------

            if (worstDpd >= 60) {
                return "PAR 60";
            }

            // ---------------------------------------------
            // 30–59 DAYS
            // ---------------------------------------------

            if (worstDpd >= 30) {
                return "PAR 30";
            }

            // ---------------------------------------------
            // 1–29 DAYS
            // ---------------------------------------------

            if (worstDpd > 0) {
                return "PAR 1";
            }

            // ---------------------------------------------
            // CURRENT
            // ---------------------------------------------

            /*
             * An ACTIVE/DISBURSED/PARTIALLY_PAID facility
             * with DPD 0 is CURRENT.
             *
             * It must NEVER become NOT_FUNDED.
             */

            return "CURRENT";
        }

        // -------------------------------------------------
        // NO CURRENT ACTIVE FACILITY
        // -------------------------------------------------

        /*
         * Determine whether the customer has ever had
         * a genuine funded facility.
         */

        boolean everFunded =
                loans.stream()
                        .anyMatch(
                                this::isHistoricalFundedLoan
                        );

        if (everFunded) {

            /*
             * No current active facility, but the customer
             * has a genuine funded history.
             *
             * This is NOT a never-funded customer.
             */

            return "CURRENT";
        }

        // -------------------------------------------------
        // NEVER FUNDED
        // -------------------------------------------------

        return "NOT_FUNDED";
    }

    // =====================================================
    // WORST DPD
    // =====================================================

    private int calculateWorstDaysPastDue(
            List<Loan> loans
    ) {

        if (loans == null || loans.isEmpty()) {
            return 0;
        }

        return loans.stream()
                .mapToInt(
                        this::calculateDaysPastDue
                )
                .max()
                .orElse(0);
    }

    // =====================================================
    // DAYS PAST DUE
    // =====================================================

    public int calculateDaysPastDue(
            Loan loan
    ) {

        /*
         * Only current active funded facilities can have
         * current portfolio DPD.
         */

        if (!isCurrentlyActiveFacility(loan)) {
            return 0;
        }

        // -------------------------------------------------
        // NO OUTSTANDING BALANCE
        // -------------------------------------------------

        /*
         * If nothing is outstanding, the facility is not
         * currently delinquent.
         */

        if (loan.getDueAmount() == null
                || loan.getDueAmount() <= 0) {

            return 0;
        }

        // -------------------------------------------------
        // NO DUE DATE
        // -------------------------------------------------

        if (loan.getDueDate() == null) {
            return 0;
        }

        LocalDate today =
                LocalDate.now();

        // -------------------------------------------------
        // FUTURE DUE DATE
        // -------------------------------------------------

        if (loan.getDueDate().isAfter(today)) {
            return 0;
        }

        // -------------------------------------------------
        // CALCULATE DPD
        // -------------------------------------------------

        return Math.max(
                0,
                (int) ChronoUnit.DAYS.between(
                        loan.getDueDate(),
                        today
                )
        );
    }

    // =====================================================
    // PREDICTIVE RISK
    // =====================================================

    private String calculatePredictiveRisk(
            int score
    ) {

        if (score >= 80) {
            return "LOW";
        }

        if (score >= 50) {
            return "MEDIUM";
        }

        return "HIGH";
    }

    // =====================================================
    // NORMALIZE RISK
    // =====================================================

    private int normalizeRisk(
            int score
    ) {

        return Math.max(
                0,
                Math.min(
                        100,
                        score
                )
        );
    }

    // =====================================================
    // NORMALIZE BEHAVIOR
    // =====================================================

    private int normalizeBehavior(
            int score
    ) {

        return Math.max(
                0,
                Math.min(
                        100,
                        score
                )
        );
    }

    // =====================================================
    // SAFE NUMBER
    // =====================================================

    private double safe(
            Double value
    ) {

        if (value == null
                || Double.isNaN(value)
                || Double.isInfinite(value)) {

            return 0;
        }

        return value;
    }

    // =====================================================
    // STANDARD RESPONSE BUILDER
    // =====================================================

    private LoanAssessmentResponse buildResponse(
            String recommendation,
            String reason,
            int riskScore,
            int behavioralScore,
            int kycScore,
            boolean fraudFlag,
            int dpd,
            int completedLoans,
            int overdueLoans,
            String parCategory,
            String predictiveRisk,
            double eligibleAmount,
            int confidence
    ) {

        return LoanAssessmentResponse.builder()

                .riskScore(riskScore)

                .behavioralScore(behavioralScore)

                .kycScore(kycScore)

                .fraudFlag(fraudFlag)

                .daysPastDue(dpd)

                .completedLoans(completedLoans)

                .overdueLoans(overdueLoans)

                .parCategory(parCategory)

                .predictiveRisk(predictiveRisk)

                .eligibleAmount(eligibleAmount)

                .recommendation(recommendation)

                .aiReason(reason)

                .aiConfidence(confidence)

                .build();
    }

    // =====================================================
    // INTERNAL DECISION MODEL
    // =====================================================

    private static class Decision {

        private final String recommendation;

        private final String reason;

        private final int confidence;

        private Decision(
                String recommendation,
                String reason,
                int confidence
        ) {

            this.recommendation =
                    recommendation;

            this.reason =
                    reason;

            this.confidence =
                    confidence;
        }
    }
}

