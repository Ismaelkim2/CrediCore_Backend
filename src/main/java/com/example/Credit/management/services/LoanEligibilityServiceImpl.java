package com.example.Credit.management.services;

import com.example.Credit.management.DTO.LoanApplicationDTO;
import com.example.Credit.management.Model.LoanDecision;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

@Service
public class LoanEligibilityServiceImpl implements LoanEligibilityService {


    private static final double MAX_REPAYMENT_CAPACITY = 0.40;
    private static final double MAX_EXPENSE_TO_INCOME = 0.60;
    private static final double ABSOLUTE_PRODUCT_LIMIT = 1_000_000.00;
    private static final double MIN_VERIFIABLE_INCOME = 1_000.00;
    private static final double MIN_DISPOSABLE_INCOME = 1_000.00;
    private static final double MAX_INCOME_MULTIPLE = 8.0;
    private static final double MIN_NORMAL_RISK_SCORE = 40.0;
    private static final double MIN_LOAN_AMOUNT = 1_000.00;
    private static final int MIN_DURATION_WEEKS = 4;
    private static final int STANDARD_DURATION_WEEKS = 8;
    private static final int EXTENDED_DURATION_WEEKS = 12;
    private static final int MAX_DURATION_WEEKS = 24;
    private static final double SAFE_PAYMENT_RATIO = 0.40;
    private static final double AFFORDABILITY_WEIGHT = 0.30;
    private static final double EXPENSE_WEIGHT = 0.15;
    private static final double BEHAVIOUR_WEIGHT = 0.20;
    private static final double CREDIT_HISTORY_WEIGHT = 0.15;
    private static final double INCOME_CAPACITY_WEIGHT = 0.20;


    @Override
    public LoanDecision assessEligibility(LoanApplicationDTO dto) {

        // ==========================================================
        // 1. VALIDATE APPLICATION
        // ==========================================================

        if (dto == null) {
            return declinedDecision(
                    "INVALID_APPLICATION",
                    "Loan application data is missing."
            );
        }

        /*
         * Monthly income is mandatory.
         *
         * Monthly expenses are OPTIONAL.
         * If the applicant has not declared expenses, the backend
         * treats them as KES 0.00.
         *
         * This does NOT mean zero expenses automatically guarantees
         * approval. The complete credit assessment still applies.
         */

        Double rawIncome = dto.getMonthlyIncome();
        Double rawExpenses = dto.getMonthlyExpenses();
        Double rawRequestedAmount = dto.getAmount();

        // ----------------------------------------------------------
        // 1A. Validate income
        // ----------------------------------------------------------

        if (rawIncome == null
                || rawIncome.isNaN()
                || rawIncome.isInfinite()
                || rawIncome <= 0) {

            return declinedDecision(
                    "INSUFFICIENT_INCOME",
                    "Loan application cannot be assessed because "
                            + "a valid monthly income must be provided."
            );
        }

        // ----------------------------------------------------------
        // 1B. Validate expenses
        //
        // NULL = KES 0.00
        // ZERO = VALID
        // NEGATIVE = INVALID
        // ----------------------------------------------------------

        if (rawExpenses != null
                && (rawExpenses.isNaN()
                || rawExpenses.isInfinite()
                || rawExpenses < 0)) {

            return declinedDecision(
                    "INVALID_MONTHLY_EXPENSES",
                    "Monthly expenses cannot be negative."
            );
        }

        // ----------------------------------------------------------
        // 1C. Validate requested amount
        // ----------------------------------------------------------

        if (rawRequestedAmount == null
                || rawRequestedAmount.isNaN()
                || rawRequestedAmount.isInfinite()
                || rawRequestedAmount <= 0) {

            return declinedDecision(
                    "INVALID_LOAN_AMOUNT",
                    "A valid requested loan amount must be provided."
            );
        }

        double income = rawIncome;

        /*
         * IMPORTANT:
         *
         * Null monthly expenses are treated as zero.
         *
         * This allows:
         *
         *     null -> 0
         *     0    -> 0
         *
         * without treating zero expenditure as an error.
         */
        double expenses =
                rawExpenses == null
                        ? 0.0
                        : rawExpenses;

        double requestedAmount =
                rawRequestedAmount;

        // ==========================================================
        // 2. MINIMUM INCOME
        // ==========================================================

        if (income < MIN_VERIFIABLE_INCOME) {

            return declinedDecision(
                    "INSUFFICIENT_INCOME",
                    "Loan application cannot be assessed because "
                            + "the applicant's verified monthly income of KES "
                            + formatAmount(income)
                            + " is below the minimum required income."
            );
        }

        // ==========================================================
        // 3. MINIMUM LOAN AMOUNT
        // ==========================================================

        if (requestedAmount < MIN_LOAN_AMOUNT) {

            return declinedDecision(
                    "INVALID_LOAN_AMOUNT",
                    "Requested loan amount must be at least KES "
                            + formatAmount(MIN_LOAN_AMOUNT)
                            + "."
            );
        }

        // ==========================================================
        // 4. AFFORDABILITY FOUNDATION
        // ==========================================================

        /*
         * Do NOT use Math.max() here.
         *
         * We need the actual disposable income so that an applicant
         * whose expenses exceed income can be identified correctly.
         */

        double disposableIncome =
                income - expenses;

        double expenseToIncomeRatio =
                income > 0
                        ? expenses / income
                        : 1.0;

        double disposableIncomeRatio =
                income > 0
                        ? disposableIncome / income
                        : 0.0;

        // Prevent tiny floating-point anomalies.
        disposableIncomeRatio =
                Math.max(
                        0.0,
                        disposableIncomeRatio
                );

        // ==========================================================
        // 5. EXPENSE / DISPOSABLE-INCOME SAFETY CHECK
        // ==========================================================

        /*
         * Zero expenses are VALID.
         *
         * High expenses are NOT automatically rejected here.
         *
         * However, if expenses consume all income or exceed income,
         * there is no positive disposable income from which a loan
         * repayment can reasonably be supported.
         */

        if (disposableIncome < MIN_DISPOSABLE_INCOME) {

            LoanDecision decision =
                    declinedDecision(
                            "INSUFFICIENT_DISPOSABLE_INCOME",
                            "Loan declined because the applicant's "
                                    + "declared monthly expenses leave "
                                    + "insufficient disposable income "
                                    + "after income and expenses are assessed."
                    );

            decision.setDebtToIncomeRatio(
                    roundPercent(
                            Math.max(
                                    0.0,
                                    expenseToIncomeRatio * 100
                            )
                    )
            );

            decision.setDisposableIncome(
                    roundMoney(
                            Math.max(
                                    0.0,
                                    disposableIncome
                            )
                    )
            );

            decision.setRiskLevel("VERY_HIGH");

            decision.setApprovedDurationWeeks(null);

            return decision;
        }

        // ==========================================================
        // 6. EXPENSE RISK
        // ==========================================================

        /*
         * Expense level is treated as a RISK SIGNAL.
         *
         * Therefore:
         *
         * 0% expenditure          -> valid
         * Low expenditure         -> low risk
         * Moderate expenditure    -> moderate risk
         * High expenditure        -> high risk
         * Very high expenditure   -> very high risk
         *
         * High spending can therefore reduce:
         *
         * - underwriting score
         * - risk multiplier
         * - eligible amount
         * - approved duration
         *
         * without automatically rejecting the borrower solely because
         * they spend a high percentage of their income.
         */

        String expenseRisk =
                getExpenseRisk(
                        expenseToIncomeRatio
                );

        // ==========================================================
        // 7. MAXIMUM SAFE REPAYMENT
        // ==========================================================

        double maximumRepayment =
                disposableIncome
                        * SAFE_PAYMENT_RATIO;

        // ==========================================================
        // 8. OVERALL RISK LEVEL
        // ==========================================================

        String riskLevel =
                getRiskLevel(
                        expenseToIncomeRatio,
                        disposableIncome,
                        income
                );

        // ==========================================================
        // 9. RISK MULTIPLIER
        // ==========================================================

        double riskMultiplier =
                getRiskMultiplier(
                        riskLevel
                );

        // ==========================================================
        // 10. BEHAVIOURAL MULTIPLIER
        // ==========================================================

        double behaviourMultiplier =
                getBehaviourMultiplier();

        // ==========================================================
        // 11. CREDIT HISTORY MULTIPLIER
        // ==========================================================

        double creditHistoryMultiplier =
                getCreditHistoryMultiplier();

        // ==========================================================
        // 12. INCOME CAPACITY
        // ==========================================================

        double incomeCapacityMultiplier =
                getIncomeCapacityMultiplier(
                        disposableIncomeRatio
                );

        // ==========================================================
        // 13. UNDERWRITING SCORES
        // ==========================================================

        double affordabilityScore =
                calculateAffordabilityScore(
                        disposableIncomeRatio
                );

        double expenseScore =
                calculateExpenseScore(
                        expenseToIncomeRatio
                );

        double behaviourScore =
                behaviourMultiplier * 100;

        double creditHistoryScore =
                creditHistoryMultiplier * 100;

        double incomeCapacityScore =
                incomeCapacityMultiplier * 100;

        // ==========================================================
        // 14. FINAL UNDERWRITING SCORE
        // ==========================================================

        double underwritingScore =
                (affordabilityScore * AFFORDABILITY_WEIGHT)
                        + (expenseScore * EXPENSE_WEIGHT)
                        + (behaviourScore * BEHAVIOUR_WEIGHT)
                        + (creditHistoryScore * CREDIT_HISTORY_WEIGHT)
                        + (incomeCapacityScore * INCOME_CAPACITY_WEIGHT);

        underwritingScore =
                clamp(
                        underwritingScore,
                        0,
                        100
                );

        // ==========================================================
        // 15. BACKEND-OWNED APPROVED DURATION
        // ==========================================================

        /*
         * IMPORTANT:
         *
         * The applicant does NOT determine the final duration.
         *
         * The backend determines it from:
         *
         * - requested amount
         * - disposable income
         * - risk level
         * - underwriting score
         */

        int approvedDurationWeeks =
                determineApprovedDurationWeeks(
                        requestedAmount,
                        disposableIncome,
                        riskLevel,
                        underwritingScore
                );

        // ==========================================================
        // 16. CONVERT WEEKS TO MONTHS
        // ==========================================================

        int assumedMonths =
                Math.max(
                        1,
                        (int) Math.ceil(
                                approvedDurationWeeks / 4.0
                        )
                );

        // ==========================================================
        // 17. REPAYMENT-CAPACITY LIMIT
        // ==========================================================

        double repaymentCapacityLimit =
                calculatePrincipalFromPaymentCapacity(
                        maximumRepayment,
                        assumedMonths
                );

        // ==========================================================
        // 18. RISK-ADJUSTED LIMIT
        // ==========================================================

        double riskAdjustedLimit =
                repaymentCapacityLimit
                        * riskMultiplier
                        * behaviourMultiplier
                        * creditHistoryMultiplier
                        * incomeCapacityMultiplier;

        // ==========================================================
        // 19. INCOME-BASED LIMIT
        // ==========================================================

        double incomeBasedLimit =
                disposableIncome
                        * MAX_INCOME_MULTIPLE;

        // ==========================================================
        // 20. POLICY ELIGIBLE LIMIT
        // ==========================================================

        double policyEligibleLimit =
                Math.min(
                        riskAdjustedLimit,
                        incomeBasedLimit
                );

        // ==========================================================
        // 21. PRODUCT CEILING
        // ==========================================================

        policyEligibleLimit =
                Math.min(
                        policyEligibleLimit,
                        ABSOLUTE_PRODUCT_LIMIT
                );

        policyEligibleLimit =
                Math.max(
                        0.0,
                        policyEligibleLimit
                );

        // ==========================================================
        // 22. ROUND ELIGIBLE LIMIT DOWN
        // ==========================================================

        /*
         * IMPORTANT:
         *
         * Never round an eligible credit limit UP.
         *
         * Example:
         *
         *  54,999 -> 54,000
         *
         * not:
         *
         *  54,999 -> 55,000
         *
         * This prevents the system from granting more than the
         * calculated risk-adjusted limit.
         */

        double eligibleAmount =
                roundDownToNearest(
                        policyEligibleLimit,
                        1_000
                );

        eligibleAmount =
                Math.min(
                        eligibleAmount,
                        ABSOLUTE_PRODUCT_LIMIT
                );

        eligibleAmount =
                Math.max(
                        0.0,
                        eligibleAmount
                );

        // ==========================================================
        // 23. REQUESTED AMOUNT CHECK
        // ==========================================================

        boolean requestedAmountWithinLimit =
                requestedAmount <= eligibleAmount;

        // ==========================================================
        // 24. INTEREST RATE
        // ==========================================================

        double interestRate =
                recommendInterestRate(
                        riskLevel
                );

        // ==========================================================
        // 25. BUILD DECISION
        // ==========================================================

        LoanDecision decision =
                new LoanDecision();

        /*
         * IMPORTANT:
         *
         * Expense ratio is NOT independently used as an automatic
         * decline condition anymore.
         *
         * High expenditure affects:
         *
         * - expense score
         * - risk level
         * - risk multiplier
         * - repayment capacity
         * - eligible amount
         *
         * The actual hard affordability stop is insufficient
         * disposable income.
         */

        boolean eligible =
                requestedAmountWithinLimit
                        && underwritingScore >= MIN_NORMAL_RISK_SCORE;

        decision.setEligible(
                eligible
        );

        decision.setRecommendedAmount(
                roundMoney(
                        eligibleAmount
                )
        );

        decision.setDebtToIncomeRatio(
                roundPercent(
                        Math.max(
                                0.0,
                                expenseToIncomeRatio * 100
                        )
                )
        );

        decision.setDisposableIncome(
                roundMoney(
                        disposableIncome
                )
        );

        decision.setRiskLevel(
                riskLevel
        );

        decision.setInterestRate(
                interestRate
        );

        /*
         * Backend owns the duration.
         */
        decision.setApprovedDurationWeeks(
                approvedDurationWeeks
        );

        // ==========================================================
        // 26. DECISION REASON — HIGH EXPENDITURE
        // ==========================================================

        /*
         * High spending is reported as a risk signal rather than
         * automatically becoming a decline.
         */

        if (expenseToIncomeRatio > 0.60) {

            decision.setReason(
                    "Applicant remains assessable, but declared monthly "
                            + "expenses consume "
                            + formatPercentage(
                            expenseToIncomeRatio
                    )
                            + " of monthly income. This represents a "
                            + expenseRisk.toLowerCase()
                            + " affordability risk and has been reflected "
                            + "in the underwriting score, risk level and "
                            + "eligible credit limit."
            );

        } else if (expenseToIncomeRatio > 0.40) {

            decision.setReason(
                    "Applicant remains within the affordability model, "
                            + "but declared monthly expenses consume "
                            + formatPercentage(
                            expenseToIncomeRatio
                    )
                            + " of monthly income. The elevated expense "
                            + "burden has been incorporated into the "
                            + "underwriting assessment."
            );
        }

        // ==========================================================
        // 27. UNDERWRITING SCORE CHECK
        // ==========================================================

        if (underwritingScore < MIN_NORMAL_RISK_SCORE) {

            decision.setEligible(
                    false
            );

            decision.setApprovedDurationWeeks(
                    null
            );

            decision.setReason(
                    "Loan application requires decline or enhanced "
                            + "review because the calculated underwriting "
                            + "score of "
                            + formatAmount(
                            underwritingScore
                    )
                            + " is below the minimum acceptable "
                            + "threshold. Expense burden, affordability "
                            + "and other credit factors have been "
                            + "considered in the assessment."
            );

            return decision;
        }

        // ==========================================================
        // 28. REQUESTED AMOUNT ABOVE ELIGIBLE LIMIT
        // ==========================================================

        if (!requestedAmountWithinLimit) {

            decision.setEligible(
                    false
            );

            /*
             * Keep duration for assessment transparency.
             *
             * The actual LoanService MUST NOT create an approved
             * loan using this decision because eligible=false.
             */

            decision.setReason(
                    "Requested amount of KES "
                            + formatAmount(
                            requestedAmount
                    )
                            + " exceeds the applicant's calculated "
                            + "risk-adjusted credit limit of KES "
                            + formatAmount(
                            eligibleAmount
                    )
                            + ". Backend-approved assessment duration "
                            + "for the assessed profile is "
                            + approvedDurationWeeks
                            + " weeks."
            );

            return decision;
        }

        // ==========================================================
        // 29. FINAL ELIGIBLE DECISION
        // ==========================================================

        /*
         * Zero expenditure should produce a valid affordability
         * calculation.
         *
         * Example:
         *
         * Income    = KES 50,000
         * Expenses  = KES 0
         * Disposable= KES 50,000
         *
         * The applicant proceeds through the complete underwriting
         * model rather than receiving a "Monthly expenses required"
         * error.
         */

        decision.setEligible(
                true
        );

        decision.setReason(
                "Applicant is within the calculated affordability and "
                        + "risk-adjusted credit limit. Declared monthly "
                        + "expenses are KES "
                        + formatAmount(
                        expenses
                )
                        + ", representing "
                        + formatPercentage(
                        expenseToIncomeRatio
                )
                        + " of monthly income. Maximum eligible amount "
                        + "is KES "
                        + formatAmount(
                        eligibleAmount
                )
                        + ". Backend-approved duration is "
                        + approvedDurationWeeks
                        + " weeks. Risk tier: "
                        + riskLevel
                        + "."
        );

        return decision;
    }


    private double roundDownToNearest(double amount, double nearest) {

        if (amount <= 0 || nearest <= 0) {
            return 0.0;
        }

        return Math.floor(amount / nearest) * nearest;
    }

    private int determineApprovedDurationWeeks(
            double requestedAmount,
            double disposableIncome,
            String riskLevel,
            double underwritingScore) {

        if (requestedAmount <= 0
                || disposableIncome <= 0) {

            return MIN_DURATION_WEEKS;
        }

        /*
         * LOW RISK + STRONG AFFORDABILITY
         *
         * Smaller loans can be settled quickly.
         */
        if ("LOW".equalsIgnoreCase(riskLevel)
                && underwritingScore >= 80) {

            if (requestedAmount <= disposableIncome) {
                return MIN_DURATION_WEEKS;
            }

            if (requestedAmount <= disposableIncome * 2) {
                return STANDARD_DURATION_WEEKS;
            }

            if (requestedAmount <= disposableIncome * 4) {
                return EXTENDED_DURATION_WEEKS;
            }

            return MAX_DURATION_WEEKS;
        }

        /*
         * MODERATE RISK
         */
        if ("MODERATE".equalsIgnoreCase(riskLevel)) {

            if (requestedAmount <= disposableIncome) {
                return STANDARD_DURATION_WEEKS;
            }

            if (requestedAmount <= disposableIncome * 2) {
                return EXTENDED_DURATION_WEEKS;
            }

            return MAX_DURATION_WEEKS;
        }

        /*
         * HIGH RISK
         *
         * We do NOT automatically make the loan longer merely to
         * make the instalment look affordable.
         *
         * Higher risk receives controlled tenure.
         */
        if ("HIGH".equalsIgnoreCase(riskLevel)) {

            if (requestedAmount <= disposableIncome) {
                return STANDARD_DURATION_WEEKS;
            }

            return EXTENDED_DURATION_WEEKS;
        }

        /*
         * VERY HIGH RISK
         *
         * Long tenure should not be used to hide weak affordability.
         */
        return STANDARD_DURATION_WEEKS;
    }

    // ==============================================================
    // RISK CLASSIFICATION
    // ==============================================================

    private String getRiskLevel(
            double expenseRatio,
            double disposableIncome,
            double income) {

        if (income <= 0) {
            return "VERY_HIGH";
        }

        if (expenseRatio <= 0.30
                && disposableIncome >= 50_000) {

            return "LOW";
        }

        if (expenseRatio <= 0.40
                && disposableIncome >= 25_000) {

            return "LOW";
        }

        if (expenseRatio <= 0.50
                && disposableIncome >= 15_000) {

            return "MODERATE";
        }

        if (expenseRatio < 0.60
                && disposableIncome >= 5_000) {

            return "HIGH";
        }

        return "VERY_HIGH";
    }

    // ==============================================================
    // RISK MULTIPLIER
    // ==============================================================

    private double getRiskMultiplier(
            String riskLevel) {

        if (riskLevel == null) {
            return 0.25;
        }

        return switch (
                riskLevel.toUpperCase(Locale.ROOT)
                ) {

            case "LOW" ->
                    1.00;

            case "MODERATE" ->
                    0.80;

            case "HIGH" ->
                    0.55;

            case "VERY_HIGH" ->
                    0.25;

            default ->
                    0.25;
        };
    }

    // ==============================================================
    // BEHAVIOUR
    // ==============================================================

    private double getBehaviourMultiplier() {

        /*
         * Conservative baseline until actual behavioural data is
         * passed directly into this service.
         */
        return 0.85;
    }

    // ==============================================================
    // CREDIT HISTORY
    // ==============================================================

    private double getCreditHistoryMultiplier() {

        return 0.90;
    }

    // ==============================================================
    // INCOME CAPACITY
    // ==============================================================

    private double getIncomeCapacityMultiplier(
            double disposableIncomeRatio) {

        if (disposableIncomeRatio >= 0.70) {
            return 1.00;
        }

        if (disposableIncomeRatio >= 0.60) {
            return 0.95;
        }

        if (disposableIncomeRatio >= 0.50) {
            return 0.85;
        }

        if (disposableIncomeRatio >= 0.40) {
            return 0.75;
        }

        return 0.55;
    }

    // ==============================================================
    // AFFORDABILITY SCORE
    // ==============================================================

    private double calculateAffordabilityScore(
            double disposableRatio) {

        if (disposableRatio >= 0.70) {
            return 100;
        }

        if (disposableRatio >= 0.60) {
            return 90;
        }

        if (disposableRatio >= 0.50) {
            return 80;
        }

        if (disposableRatio >= 0.40) {
            return 65;
        }

        if (disposableRatio >= 0.30) {
            return 50;
        }

        return 25;
    }

    // ==============================================================
    // EXPENSE SCORE
    // ==============================================================

    private double calculateExpenseScore(
            double expenseRatio) {

        if (expenseRatio <= 0.20) {
            return 100;
        }

        if (expenseRatio <= 0.30) {
            return 90;
        }

        if (expenseRatio <= 0.40) {
            return 80;
        }

        if (expenseRatio <= 0.50) {
            return 65;
        }

        if (expenseRatio < 0.60) {
            return 45;
        }

        return 0;
    }

    // ==============================================================
    // EXPENSE RISK
    // ==============================================================

    private String getExpenseRisk(
            double expenseRatio) {

        if (expenseRatio <= 0.30) {
            return "LOW";
        }

        if (expenseRatio <= 0.40) {
            return "MODERATE";
        }

        if (expenseRatio < 0.60) {
            return "HIGH";
        }

        return "VERY_HIGH";
    }

    // ==============================================================
    // PAYMENT CAPACITY → PRINCIPAL
    // ==============================================================

    private double calculatePrincipalFromPaymentCapacity(
            double monthlyPayment,
            int months) {

        if (monthlyPayment <= 0
                || months <= 0) {

            return 0;
        }

        /*
         * Conservative internal sizing rate.
         *
         * This is NOT the final customer interest rate.
         */
        double monthlyRate = 0.015;

        double factor =
                Math.pow(
                        1 + monthlyRate,
                        months
                );

        double principal =
                monthlyPayment
                        * (
                        (factor - 1)
                                / (monthlyRate * factor)
                );

        return Math.max(
                0,
                principal
        );
    }

    // ==============================================================
    // PRICING
    // ==============================================================

    private double recommendInterestRate(
            String riskLevel) {

        if (riskLevel == null) {
            return 18.0;
        }

        return switch (
                riskLevel.toUpperCase(Locale.ROOT)
                ) {

            case "LOW" ->
                    10.50;

            case "MODERATE" ->
                    14.00;

            case "HIGH" ->
                    17.00;

            case "VERY_HIGH" ->
                    20.00;

            default ->
                    20.00;
        };
    }

    // ==============================================================
    // DECLINED DECISION
    // ==============================================================

    private LoanDecision declinedDecision(
            String code,
            String reason) {

        LoanDecision decision =
                new LoanDecision();

        decision.setEligible(false);

        decision.setRecommendedAmount(0.0);

        decision.setDebtToIncomeRatio(100.0);

        decision.setDisposableIncome(0.0);

        decision.setRiskLevel("VERY_HIGH");

        decision.setInterestRate(
                recommendInterestRate("VERY_HIGH")
        );

        /*
         * No approved duration for a declined application.
         */
        decision.setApprovedDurationWeeks(null);

        decision.setReason(
                "[" + code + "] " + reason
        );

        return decision;
    }

    // ==============================================================
    // NUMERIC HELPERS
    // ==============================================================

    private double sanitize(Double value) {

        if (value == null
                || value.isNaN()
                || value.isInfinite()
                || value < 0) {

            return 0.0;
        }

        return value;
    }

    private double clamp(
            double value,
            double min,
            double max) {

        return Math.max(
                min,
                Math.min(max, value)
        );
    }

    private double roundMoney(
            double amount) {

        return BigDecimal
                .valueOf(amount)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                )
                .doubleValue();
    }

    private double roundPercent(
            double amount) {

        return BigDecimal
                .valueOf(amount)
                .setScale(
                        2,
                        RoundingMode.HALF_UP
                )
                .doubleValue();
    }

    private double roundToNearest(
            double amount,
            int nearest) {

        if (amount <= 0) {
            return 0;
        }

        return Math.round(
                amount / nearest
        ) * nearest;
    }

    private String formatAmount(
            double amount) {

        return String.format(
                Locale.US,
                "%,.2f",
                amount
        );
    }

    private String formatPercentage(
            double ratio) {

        return String.format(
                Locale.US,
                "%.2f%%",
                ratio * 100
        );
    }
}