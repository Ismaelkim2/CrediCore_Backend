package com.example.Credit.management.Model;

public final class LoanLifecycle {

    private LoanLifecycle() {
        // Utility class
    }

    /**
     * Determines whether the loan represents a funded credit facility
     * that belongs in portfolio analytics.
     */
    public static boolean isFundedFacility(Loan loan) {

        if (loan == null) {
            return false;
        }

        /*
         * A funded facility must have a disbursement date.
         *
         * This is an additional safety control against incorrectly
         * classifying rejected/pending applications as portfolio loans.
         */
        if (loan.getDisbursedDate() == null) {
            return false;
        }

        LoanStatus status = parseStatus(loan.getStatus());

        return status == LoanStatus.DISBURSED
                || status == LoanStatus.ACTIVE
                || status == LoanStatus.PARTIALLY_PAID;
    }

    /**
     * Determines whether the loan is an application that has not
     * become a funded credit facility.
     */
    public static boolean isUnfundedApplication(Loan loan) {

        if (loan == null) {
            return true;
        }

        return !isFundedFacility(loan);
    }

    /**
     * Determines whether the loan is rejected.
     */
    public static boolean isRejected(Loan loan) {

        return loan != null
                && parseStatus(loan.getStatus()) == LoanStatus.REJECTED;
    }

    /**
     * Determines whether the loan is blocked.
     */
    public static boolean isBlocked(Loan loan) {

        return loan != null
                && parseStatus(loan.getStatus()) == LoanStatus.BLOCKED;
    }

    /**
     * Determines whether the loan has been completed.
     */
    public static boolean isCompleted(Loan loan) {

        return loan != null
                && parseStatus(loan.getStatus()) == LoanStatus.COMPLETED;
    }

    /**
     * Safely converts the existing String status into LoanStatus.
     *
     * Existing database records remain compatible because the Loan
     * entity currently stores status as String.
     */
    public static LoanStatus parseStatus(String status) {

        if (status == null || status.isBlank()) {
            return null;
        }

        try {
            return LoanStatus.valueOf(
                    status.trim()
                            .toUpperCase()
                            .replace('-', '_')
                            .replace(' ', '_')
            );
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Returns a normalized status string for API/UI use.
     */
    public static String normalizeStatus(String status) {

        LoanStatus parsed = parseStatus(status);

        return parsed != null
                ? parsed.name()
                : "UNKNOWN";
    }
}
