package com.example.Credit.management.services;

import com.example.Credit.management.Model.Loan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FraudDetectionService {

    private final CreditScoringService creditScoringService;

    /*
     * =========================================================
     * FUNDED FACILITY STATUSES
     * =========================================================
     *
     * Only these statuses represent facilities that were
     * actually funded or are currently part of the portfolio.
     */
    private static final Set<String> FUNDED_STATUSES = Set.of(
            "DISBURSED",
            "ACTIVE",
            "PARTIALLY_PAID",
            "COMPLETED"
    );

    /*
     * =========================================================
     * EXPLICITLY UNFUNDED STATUSES
     * =========================================================
     */
    private static final Set<String> UNFUNDED_STATUSES = Set.of(
            "PENDING",
            "UNDER_REVIEW",
            "APPROVED",
            "REJECTED",
            "BLOCKED",
            "CANCELLED"
    );

    /*
     * =========================================================
     * FRAUD DETECTION
     * =========================================================
     *
     * IMPORTANT:
     *
     * This method returns TRUE only when there is a strong
     * fraud signal or multiple meaningful abnormal signals.
     *
     * It does NOT classify ordinary credit risk as fraud.
     */
    public boolean detectFraud(
            List<Loan> previousLoans,
            double requestedAmount,
            String phoneNumber
    ) {

        previousLoans =
                previousLoans == null
                        ? List.of()
                        : previousLoans;

        /*
         * -----------------------------------------------------
         * SIGNAL 1 — MULTIPLE GENUINE DEFAULTS
         * -----------------------------------------------------
         *
         * Only funded facilities are considered.
         *
         * Rejected applications are NOT defaults.
         */
        long defaults =
                previousLoans.stream()
                        .filter(this::isHistoricalFundedLoan)
                        .filter(loan ->
                                creditScoringService
                                        .calculateDaysPastDue(loan) >= 90
                        )
                        .count();

        boolean multipleDefaults =
                defaults >= 2;

        /*
         * -----------------------------------------------------
         * SIGNAL 2 — EXTREME REQUEST SIZE
         * -----------------------------------------------------
         *
         * A high request is NOT automatically fraud.
         *
         * It becomes a fraud signal only when combined with
         * another strong abnormality.
         */
        boolean extremeAmount =
                requestedAmount >= 1_000_000;

        /*
         * -----------------------------------------------------
         * SIGNAL 3 — EXCESSIVE PHONE VELOCITY
         * -----------------------------------------------------
         *
         * Count only genuine funded facilities.
         */
        long phoneRelatedFacilities =
                previousLoans.stream()
                        .filter(this::isHistoricalFundedLoan)
                        .filter(loan ->
                                samePhone(
                                        phoneNumber,
                                        loan.getPhoneNumber()
                                )
                        )
                        .count();

        boolean excessivePhoneVelocity =
                phoneRelatedFacilities >= 5;

        /*
         * -----------------------------------------------------
         * SIGNAL 4 — EXTREME PHONE VELOCITY
         * -----------------------------------------------------
         *
         * A very high number of funded facilities linked to
         * one phone number is materially more suspicious.
         */
        boolean extremePhoneVelocity =
                phoneRelatedFacilities >= 8;

        /*
         * -----------------------------------------------------
         * STRONG FRAUD RULE
         * -----------------------------------------------------
         *
         * Multiple genuine defaults alone are concerning,
         * but the system should distinguish credit distress
         * from fraud.
         *
         * Therefore:
         *
         * 1. Extreme phone velocity = TRUE
         *
         * 2. Multiple defaults + extreme request = TRUE
         *
         * 3. Multiple defaults + excessive phone velocity = TRUE
         *
         * 4. Extreme request + excessive phone velocity = TRUE
         *
         * Otherwise FALSE.
         */
        if (extremePhoneVelocity) {
            return true;
        }

        if (multipleDefaults && extremeAmount) {
            return true;
        }

        if (multipleDefaults && excessivePhoneVelocity) {
            return true;
        }

        if (extremeAmount && excessivePhoneVelocity) {
            return true;
        }

        /*
         * -----------------------------------------------------
         * NO SUFFICIENT FRAUD EVIDENCE
         * -----------------------------------------------------
         */
        return false;
    }

    // =========================================================
    // HISTORICAL FUNDED LOAN
    // =========================================================

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
         * Explicitly unfunded applications must never be
         * treated as funded facilities.
         */
        if (UNFUNDED_STATUSES.contains(status)) {
            return false;
        }

        /*
         * Explicit funded statuses.
         */
        if (FUNDED_STATUSES.contains(status)) {
            return true;
        }

        /*
         * Fallback:
         *
         * A disbursement date is evidence that money was
         * released, provided the loan is not explicitly
         * rejected, blocked or cancelled.
         */
        if (loan.getDisbursedDate() != null) {

            return !"REJECTED".equals(status)
                    && !"BLOCKED".equals(status)
                    && !"CANCELLED".equals(status);
        }

        return false;
    }

    // =========================================================
    // PHONE COMPARISON
    // =========================================================

    private boolean samePhone(
            String requestedPhone,
            String previousPhone
    ) {

        if (requestedPhone == null
                || previousPhone == null) {

            return false;
        }

        String requested =
                normalizePhone(
                        requestedPhone
                );

        String previous =
                normalizePhone(
                        previousPhone
                );

        return !requested.isBlank()
                && requested.equals(previous);
    }

    // =========================================================
    // PHONE NORMALIZATION
    // =========================================================

    private String normalizePhone(
            String phone
    ) {

        if (phone == null) {
            return "";
        }

        /*
         * Remove all spaces, brackets, dashes and other
         * formatting characters.
         */
        String normalized =
                phone.replaceAll(
                        "[^0-9+]",
                        ""
                );

        /*
         * Convert Kenyan local format:
         *
         * 0712345678
         *
         * to:
         *
         * 254712345678
         */
        if (normalized.startsWith("0")
                && normalized.length() == 10) {

            normalized =
                    "254"
                            + normalized.substring(1);
        }

        /*
         * Convert:
         *
         * +254712345678
         *
         * to:
         *
         * 254712345678
         */
        if (normalized.startsWith("+")) {

            normalized =
                    normalized.substring(1);
        }

        return normalized;
    }

    // =========================================================
    // STATUS NORMALIZATION
    // =========================================================

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
}

