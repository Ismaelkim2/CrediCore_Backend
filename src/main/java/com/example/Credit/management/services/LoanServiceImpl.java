package com.example.Credit.management.services;

import com.example.Credit.management.DTO.LoanApplicationDTO;
import com.example.Credit.management.Model.*;
import com.example.Credit.management.Repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class LoanServiceImpl implements LoanService {

    // =========================================================
    // REPOSITORIES / SERVICES
    // =========================================================

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private StatementService statementService;

    @Autowired
    private LoanSettingsService loanSettingsService;

    @Autowired
    private RepaymentRepository repaymentRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountService accountService;

    /**
     * AUTHORITATIVE BACKEND CREDIT ELIGIBILITY ENGINE.
     *
     * The frontend must NEVER determine:
     *
     * - eligible amount
     * - duration
     * - risk
     * - interest
     * - penalty
     * - due date
     * - approval decision
     */
    @Autowired
    private LoanEligibilityService loanEligibilityService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SmsService smsService;

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    private final StaffRepository staffRepo;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    @Autowired
    public LoanServiceImpl(
            StaffRepository staffRepo
    ) {
        this.staffRepo = staffRepo;
    }

    // =========================================================
    // CONSTANTS
    // =========================================================

    private static final String STATUS_APPROVED =
            "APPROVED";

    private static final String STATUS_PARTIAL =
            "PARTIAL";

    private static final String STATUS_AWAITING_AUTHORIZATION =
            "AWAITING_AUTHORIZATION";

    private static final String STATUS_AUTO_APPROVED =
            "AUTO_APPROVED";

    private static final String STATUS_AUTO_REJECTED =
            "AUTO_REJECTED";

    private static final String STATUS_REJECTED =
            "REJECTED";

    private static final String STATUS_OVERDUE =
            "OVERDUE";

    // =========================================================
    // DURATION UTILITIES
    // =========================================================

    private int parseWeeks(
            String duration
    ) {

        if (duration == null
                || duration.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Backend loan duration is missing."
            );
        }

        String normalized =
                duration.trim().toLowerCase();

        String numericPart =
                normalized.replaceAll(
                        "[^0-9]",
                        ""
                );

        if (numericPart.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Invalid backend loan duration: "
                            + duration
            );
        }

        int number;

        try {

            number =
                    Integer.parseInt(
                            numericPart
                    );

        } catch (NumberFormatException ex) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Invalid backend loan duration: "
                            + duration
            );
        }

        if (number <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Backend loan duration must be greater than zero."
            );
        }

        return normalized.contains("month")
                ? number * 4
                : number;
    }

    private String formatDurationWeeks(
            int approvedDurationWeeks
    ) {

        if (approvedDurationWeeks <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Backend approved loan duration must be greater than zero."
            );
        }

        return approvedDurationWeeks + " weeks";
    }

    private int getApprovedDurationWeeks(
            LoanDecision decision
    ) {

        if (decision == null) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Credit decision is missing."
            );
        }

        Integer approvedWeeks =
                decision.getApprovedDurationWeeks();

        if (approvedWeeks == null
                || approvedWeeks <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Credit engine did not return an approved loan duration."
            );
        }

        return approvedWeeks;
    }

    // =========================================================
    // SAFE LIMIT ROUNDING
    // =========================================================

    /**
     * Rounds a calculated credit limit DOWN.
     *
     * Example:
     *
     * 54,999 -> 54,000
     * 55,001 -> 55,000
     *
     * The credit system must never round an eligible amount upward.
     */
    private double roundDownToNearest(
            double amount,
            double nearest
    ) {

        if (Double.isNaN(amount)
                || Double.isInfinite(amount)
                || amount <= 0
                || nearest <= 0
                || Double.isNaN(nearest)
                || Double.isInfinite(nearest)) {

            return 0.0;
        }

        return Math.floor(
                amount / nearest
        ) * nearest;
    }

    // =========================================================
    // GET ALL LOANS
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<Loan> getAllLoans() {

        return loanRepository.findAllWithAccount();
    }

    // =========================================================
    // CORE LOAN APPLICATION
    // =========================================================

    @Override
    @Transactional
    public Loan saveLoan(
            Loan loanRequest
    ) {

        if (loanRequest == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Loan application is required."
            );
        }

        // =====================================================
        // 1. IDENTIFY APPLICANT
        // =====================================================

        Account account =
                validateAndGetAccount(
                        loanRequest
                );

        // =====================================================
        // 2. PREVENT MULTIPLE ACTIVE LOANS
        // =====================================================

        ensureNoActiveLoans(
                account
        );

        // =====================================================
        // 3. VALIDATE VERIFIED FINANCIAL PROFILE
        // =====================================================

        validateApplicantFinancialInformation(
                account
        );

        // =====================================================
        // 4. BUILD AUTHORITATIVE DTO
        // =====================================================

        LoanApplicationDTO dto =
                buildLoanDTO(
                        loanRequest,
                        account
                );

        // =====================================================
        // 5. AUTHORITATIVE BACKEND CREDIT ASSESSMENT
        // =====================================================

        LoanDecision decision =
                loanEligibilityService.assessEligibility(
                        dto
                );

        if (decision == null) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Loan eligibility engine returned no decision."
            );
        }

        // =====================================================
        // 6. VALIDATE REQUESTED AMOUNT
        // =====================================================

        validateRequestedAmountAgainstDecision(
                loanRequest.getAmount(),
                decision
        );

        // =====================================================
        // 7. BUILD BASIC LOAN ENTITY
        // =====================================================

        Loan loan =
                buildLoanEntity(
                        loanRequest,
                        account,
                        decision
                );

        // =====================================================
        // 8. APPLY TERMS ONLY TO ELIGIBLE LOANS
        // =====================================================

        if (decision.isEligible()) {

            int approvedDurationWeeks =
                    getApprovedDurationWeeks(
                            decision
                    );

            applyLoanPricingAndTerms(
                    loan,
                    approvedDurationWeeks
            );
        }

        // =====================================================
        // 9. FINAL DECISION + PERSISTENCE
        // =====================================================

        return processDecisionAndSave(
                loan,
                decision
        );
    }

    // =========================================================
    // VERIFIED FINANCIAL INFORMATION
    // =========================================================

    private void validateApplicantFinancialInformation(
            Account account
    ) {

        if (account == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Applicant account is required."
            );
        }

        // =====================================================
        // MONTHLY INCOME
        // =====================================================

        Double monthlyIncome =
                account.getMonthlyIncome();

        if (monthlyIncome == null
                || monthlyIncome.isNaN()
                || monthlyIncome.isInfinite()
                || monthlyIncome <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Monthly income must be provided and must be greater than zero."
            );
        }

        // =====================================================
        // MONTHLY EXPENSES
        //
        // NULL = KES 0.00
        // ZERO = VALID
        // POSITIVE = VALID
        // NEGATIVE = INVALID
        //
        // High expenses are NOT rejected here.
        // The credit engine evaluates affordability and risk.
        // =====================================================

        Double monthlyExpenses =
                account.getMonthlyExpenses();

        if (monthlyExpenses == null) {

            monthlyExpenses = 0.0;

            /*
             * Normalize null expenses to zero.
             *
             * The account is already managed by the current
             * transaction, so the normalized value will remain
             * available to the subsequent credit assessment.
             */
            account.setMonthlyExpenses(0.0);

        } else {

            if (monthlyExpenses.isNaN()
                    || monthlyExpenses.isInfinite()) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Monthly expenses contain an invalid numeric value."
                );
            }

            if (monthlyExpenses < 0) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Monthly expenses cannot be negative."
                );
            }
        }

        /*
         * IMPORTANT BUSINESS RULE:
         *
         * Do NOT reject here because:
         *
         *     expenses == 0
         *
         * and do NOT reject here simply because:
         *
         *     expenses >= income
         *
         * The eligibility engine is responsible for affordability,
         * risk scoring and the final lending decision.
         */
    }

    // =========================================================
    // ACCOUNT VALIDATION
    // =========================================================

    private Account validateAndGetAccount(
            Loan loanRequest
    ) {

        if (loanRequest.getAccount() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Applicant account is required."
            );
        }

        String nationalId =
                loanRequest
                        .getAccount()
                        .getNationalId();

        if (nationalId == null
                || nationalId.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "National ID is required."
            );
        }

        Account account =
                accountRepository
                        .findByNationalId(
                                nationalId.trim()
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Account not found. Please open an account first."
                                )
                        );

        String accountStatus =
                account.getAccountStatus();

        if (accountStatus == null
                || accountStatus.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Applicant account status is not configured."
            );
        }

        if ("CLOSED".equalsIgnoreCase(
                accountStatus
        )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "This account has been closed. Loan applications are not permitted for closed accounts."
            );
        }

        if (!"ACTIVE".equalsIgnoreCase(
                accountStatus
        )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only active accounts are eligible to submit loan applications."
            );
        }

        return account;
    }

    // =========================================================
    // ACTIVE LOAN VALIDATION
    // =========================================================

    private void ensureNoActiveLoans(
            Account account
    ) {

        long activeLoanCount =
                loanRepository.countByAccountAndStatusIn(
                        account,
                        List.of(
                                STATUS_APPROVED,
                                STATUS_PARTIAL,
                                STATUS_AWAITING_AUTHORIZATION,
                                STATUS_AUTO_APPROVED,
                                STATUS_OVERDUE
                        )
                );

        if (activeLoanCount > 0) {

            throw new LoanValidationException(
                    "Applicant already has an active loan."
            );
        }
    }

    // =========================================================
    // CUSTOM VALIDATION EXCEPTION
    // =========================================================

    public class LoanValidationException
            extends RuntimeException {

        public LoanValidationException(
                String message
        ) {
            super(message);
        }
    }

    // =========================================================
    // BUILD AUTHORITATIVE LOAN DTO
    // =========================================================

    private LoanApplicationDTO buildLoanDTO(
            Loan loanRequest,
            Account account
    ) {

        if (loanRequest == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Loan application is required."
            );
        }

        if (account == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Applicant account is required."
            );
        }

        LoanApplicationDTO dto =
                new LoanApplicationDTO();

        // =====================================================
        // CUSTOMER INFORMATION
        // =====================================================

        dto.setApplicantName(
                account.getFullName()
        );

        String nationalId =
                account.getNationalId();

        if (nationalId == null
                || nationalId.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "National ID is required."
            );
        }

        try {

            dto.setIdNo(
                    Long.parseLong(
                            nationalId.trim()
                    )
            );

        } catch (NumberFormatException ex) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid National ID."
            );
        }

        dto.setPhoneNumber(
                account.getPhoneNumber()
        );

        dto.setLocation(
                account.getLocation()
        );

        // =====================================================
        // REQUESTED LOAN AMOUNT
        // =====================================================

        double requestedAmount =
                loanRequest.getAmount();

        if (Double.isNaN(requestedAmount)
                || Double.isInfinite(requestedAmount)
                || requestedAmount <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Loan amount must be greater than zero."
            );
        }

        dto.setAmount(
                requestedAmount
        );

        // =====================================================
        // AUTHORITATIVE MONTHLY INCOME
        // =====================================================

        Double monthlyIncome =
                account.getMonthlyIncome();

        if (monthlyIncome == null
                || monthlyIncome.isNaN()
                || monthlyIncome.isInfinite()
                || monthlyIncome <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Monthly income must be provided and must be greater than zero."
            );
        }

        dto.setMonthlyIncome(
                monthlyIncome
        );

        // =====================================================
        // AUTHORITATIVE MONTHLY EXPENSES
        //
        // NULL = 0
        // =====================================================

        Double monthlyExpenses =
                account.getMonthlyExpenses();

        if (monthlyExpenses == null) {

            monthlyExpenses = 0.0;
        }

        if (monthlyExpenses.isNaN()
                || monthlyExpenses.isInfinite()
                || monthlyExpenses < 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Monthly expenses must be zero or greater."
            );
        }

        dto.setMonthlyExpenses(
                monthlyExpenses
        );

        /*
         * Notice that the DTO does NOT receive:
         *
         * - frontend duration
         * - frontend interest rate
         * - frontend due date
         * - frontend risk
         *
         * Those values belong to the backend.
         */

        return dto;
    }

    // =========================================================
    // VALIDATE REQUESTED AMOUNT
    // =========================================================

    private void validateRequestedAmountAgainstDecision(
            double requestedAmount,
            LoanDecision decision
    ) {

        if (Double.isNaN(requestedAmount)
                || Double.isInfinite(requestedAmount)
                || requestedAmount <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Requested loan amount must be greater than zero."
            );
        }

        /*
         * A rejected application is still a legitimate application.
         *
         * Therefore, do NOT throw merely because its recommended
         * amount is zero.
         */

        if (!decision.isEligible()) {
            return;
        }

        double eligibleAmount =
                decision.getRecommendedAmount();

        if (eligibleAmount <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The backend credit engine did not return a valid eligible amount."
            );
        }

        if (requestedAmount > eligibleAmount) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Requested amount of KES "
                            + String.format(
                            "%,.2f",
                            requestedAmount
                    )
                            + " exceeds the applicant's eligible limit of KES "
                            + String.format(
                            "%,.2f",
                            eligibleAmount
                    )
                            + "."
            );
        }
    }

    // =========================================================
    // BUILD LOAN ENTITY
    // =========================================================

    private Loan buildLoanEntity(
            Loan loanRequest,
            Account account,
            LoanDecision decision
    ) {

        Loan loan =
                new Loan();

        loan.setAccount(
                account
        );

        loan.setApplicantName(
                account.getFullName()
        );

        loan.setEmail(
                account.getEmail()
        );

        loan.setPhoneNumber(
                account.getPhoneNumber()
        );

        loan.setLocation(
                account.getLocation()
        );

        loan.setAmount(
                loanRequest.getAmount()
        );

        loan.setApplicationDate(
                LocalDate.now()
        );

        /*
         * Duration is intentionally null at this stage.
         *
         * It is populated only after the credit engine confirms
         * eligibility.
         */

        loan.setDuration(
                null
        );

        /*
         * Due date is NEVER supplied by the frontend.
         *
         * It is generated only when the loan is actually approved
         * and disbursed.
         */

        loan.setDueDate(
                null
        );

        /*
         * Rejected applications use zero values for primitive
         * financial fields.
         */

        loan.setInterestRate(
                0.0
        );

        loan.setPenaltyRate(
                0.0
        );

        loan.setTotalRepayableAmount(
                0.0
        );

        loan.setDueAmount(
                0.0
        );

        loan.setAmountPaid(
                0.0
        );

        loan.setDisbursedDate(
                null
        );

        return loan;
    }

    // =========================================================
    // APPLY OFFICIAL LOAN PRICING
    // =========================================================

    private void applyLoanPricingAndTerms(
            Loan loan,
            int approvedDurationWeeks
    ) {

        if (loan == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Loan is required."
            );
        }

        if (loan.getAmount() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Loan amount must be greater than zero."
            );
        }

        if (approvedDurationWeeks <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Backend-approved loan duration is invalid."
            );
        }

        LoanSettings settings =
                loanSettingsService
                        .findByDurationInWeeks(
                                approvedDurationWeeks
                        );

        if (settings == null) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Loan settings are not configured for "
                            + approvedDurationWeeks
                            + " weeks."
            );
        }

        loan.setDuration(
                formatDurationWeeks(
                        approvedDurationWeeks
                )
        );

        double interest =
                loan.getAmount()
                        * settings.getInterestRate()
                        / 100.0;

        double penalty =
                loan.getAmount()
                        * settings.getPenaltyRate()
                        / 100.0;

        loan.setInterestRate(
                settings.getInterestRate()
        );

        loan.setPenaltyRate(
                settings.getPenaltyRate()
        );

        loan.setTotalRepayableAmount(
                loan.getAmount()
                        + interest
                        + penalty
        );

        loan.setDueAmount(
                loan.getTotalRepayableAmount()
        );

        loan.setAmountPaid(
                0.0
        );
    }

    // =========================================================
    // DECISION ENGINE / SAVE
    // =========================================================

    private Loan processDecisionAndSave(
            Loan loan,
            LoanDecision decision
    ) {

        // =====================================================
        // AUTOMATIC REJECTION
        // =====================================================

        if (!decision.isEligible()) {

            loan.setDuration(
                    null
            );

            loan.setInterestRate(
                    0.0
            );

            loan.setPenaltyRate(
                    0.0
            );

            loan.setTotalRepayableAmount(
                    0.0
            );

            loan.setDueAmount(
                    0.0
            );

            loan.setAmountPaid(
                    0.0
            );

            loan.setDueDate(
                    null
            );

            loan.setDisbursedDate(
                    null
            );

            loan.setStatus(
                    STATUS_AUTO_REJECTED
            );

            loan.setRejectionReason(
                    decision.getReason()
            );

            loan.setRejectedAt(
                    LocalDateTime.now()
            );

            Loan saved =
                    saveLoanWithApplicationNumber(
                            loan
                    );

            String message =
                    "Your loan application "
                            + saved.getApplicationNumber()
                            + " of KES "
                            + String.format(
                            "%,.2f",
                            saved.getAmount()
                    )
                            + " was automatically declined.\n\n"
                            + "Reason: "
                            + (
                            decision.getReason() != null
                                    ? decision.getReason()
                                    : "The backend credit assessment determined that the application does not currently meet the lending criteria."
                    );

            // =================================================
            // CUSTOMER NOTIFICATION
            // =================================================

            try {

                if (saved.getAccount() != null) {

                    notificationService.createNotification(
                            saved.getAccount(),
                            message,
                            "LOAN_REJECTED"
                    );
                }

            } catch (Exception ex) {

                System.err.println(
                        "Loan rejection notification failed for Loan ID "
                                + saved.getId()
                                + ": "
                                + ex.getMessage()
                );
            }

            // =================================================
            // CUSTOMER SMS
            // =================================================

            try {

                if (saved.getPhoneNumber() != null
                        && !saved.getPhoneNumber().isBlank()) {

                    smsService.sendSms(
                            saved.getPhoneNumber(),
                            message
                    );
                }

            } catch (Exception ex) {

                System.err.println(
                        "Loan rejection SMS failed for Loan ID "
                                + saved.getId()
                                + ": "
                                + ex.getMessage()
                );
            }

            // =================================================
            // CUSTOMER EMAIL
            // =================================================

            try {

                if (saved.getEmail() != null
                        && !saved.getEmail().isBlank()) {

                    emailService.sendEmail(
                            saved.getEmail(),
                            "Loan Application Declined",
                            message
                    );
                }

            } catch (Exception ex) {

                System.err.println(
                        "Loan rejection email failed for Loan ID "
                                + saved.getId()
                                + ": "
                                + ex.getMessage()
                );
            }

            return saved;
        }

        // =====================================================
        // ELIGIBLE LOAN
        // =====================================================

        int approvedDurationWeeks =
                getApprovedDurationWeeks(
                        decision
                );

        LoanSettings settings =
                loanSettingsService
                        .findByDurationInWeeks(
                                approvedDurationWeeks
                        );

        if (settings == null) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Loan settings are not configured for the backend-approved duration."
            );
        }

        // =====================================================
        // CURRENT AUTHENTICATED USER
        // =====================================================

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || authentication.getName() == null
                || authentication.getName().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user is required to submit a loan."
            );
        }

        String email =
                authentication.getName();

        User loggedInUser =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.UNAUTHORIZED,
                                        "Logged in user not found."
                                )
                        );

        String loggedInRole =
                loggedInUser
                        .getRole()
                        .name()
                        .toUpperCase();

        // =====================================================
        // AUTO APPROVAL
        // =====================================================

        boolean isAdmin =
                "ADMIN".equalsIgnoreCase(
                        loggedInRole
                );

        boolean autoApprove =
                settings.isAutoApproval()
                        && "LOW".equalsIgnoreCase(
                        decision.getRiskLevel()
                );

        if (isAdmin && autoApprove) {

            return approveLoanProcess(
                    loan
            );
        }

        // =====================================================
        // WAITING FOR ADMIN AUTHORIZATION
        // =====================================================

        loan.setStatus(
                STATUS_AWAITING_AUTHORIZATION
        );

        Loan saved =
                saveLoanWithApplicationNumber(
                        loan
                );

        String applicantMessage =
                "Your loan application "
                        + saved.getApplicationNumber()
                        + " has been submitted successfully "
                        + "and is awaiting administrator approval.";

        try {

            if (saved.getAccount() != null) {

                notificationService.createNotification(
                        saved.getAccount(),
                        applicantMessage,
                        "LOAN_PENDING_APPROVAL"
                );
            }

        } catch (Exception ex) {

            System.err.println(
                    "Applicant notification failed for Loan ID "
                            + saved.getId()
                            + ": "
                            + ex.getMessage()
            );
        }

        // =====================================================
        // NOTIFY ADMINS
        // =====================================================

        List<User> admins =
                userRepository.findByRole(
                        Role.ADMIN
                );

        for (User admin : admins) {

            try {

                if (admin == null) {
                    continue;
                }

                Account adminAccount =
                        accountRepository
                                .findByNationalId(
                                        admin.getNationalId()
                                )
                                .orElse(null);

                if (adminAccount == null) {
                    continue;
                }

                String adminMessage =
                        "A new loan requires your approval.\n\n"
                                + "Applicant: "
                                + saved.getApplicantName()
                                + "\nAmount: KES "
                                + String.format(
                                "%,.2f",
                                saved.getAmount()
                        )
                                + "\nBackend Approved Duration: "
                                + saved.getDuration()
                                + "\nApplication Reference: "
                                + saved.getApplicationNumber();

                try {

                    notificationService.createNotification(
                            adminAccount,
                            adminMessage,
                            "ADMIN_LOAN_APPROVAL"
                    );

                } catch (Exception ex) {

                    System.err.println(
                            "Admin notification failed: "
                                    + ex.getMessage()
                    );
                }

                try {

                    if (admin.getEmail() != null
                            && !admin.getEmail().isBlank()) {

                        emailService.sendEmail(
                                admin.getEmail(),
                                "Loan Awaiting Approval",
                                adminMessage
                        );
                    }

                } catch (Exception ex) {

                    System.err.println(
                            "Admin email failed: "
                                    + ex.getMessage()
                    );
                }

                try {

                    if (adminAccount.getPhoneNumber() != null
                            && !adminAccount.getPhoneNumber().isBlank()) {

                        smsService.sendSms(
                                adminAccount.getPhoneNumber(),
                                "Loan awaiting approval. Application Reference: "
                                        + saved.getApplicationNumber()
                        );
                    }

                } catch (Exception ex) {

                    System.err.println(
                            "Admin SMS failed: "
                                    + ex.getMessage()
                    );
                }

            } catch (Exception ex) {

                System.err.println(
                        "Failed processing admin notification: "
                                + ex.getMessage()
                );
            }
        }

        return saved;
    }

    // =========================================================
    // APPLICATION NUMBER
    // =========================================================

    private String generateApplicationNumber(
            Long loanId
    ) {

        if (loanId == null) {

            throw new IllegalStateException(
                    "Cannot generate application number before loan ID is generated."
            );
        }

        return "CB-LOAN-"
                + String.format(
                "%06d",
                loanId
        );
    }

    // =========================================================
    // SAVE WITH APPLICATION NUMBER
    // =========================================================

    private Loan saveLoanWithApplicationNumber(
            Loan loan
    ) {

        Loan savedLoan =
                loanRepository.saveAndFlush(
                        loan
                );

        if (savedLoan.getApplicationNumber() == null
                || savedLoan
                .getApplicationNumber()
                .isBlank()) {

            savedLoan.setApplicationNumber(
                    generateApplicationNumber(
                            savedLoan.getId()
                    )
            );
        }

        return loanRepository.saveAndFlush(
                savedLoan
        );
    }

    // =========================================================
    // APPROVAL FLOW
    // =========================================================

    @Transactional
    private Loan approveLoanProcess(
            Loan loan
    ) {

        if (STATUS_APPROVED.equalsIgnoreCase(
                loan.getStatus()
        )) {

            return loan;
        }

        int approvedDurationWeeks =
                parseWeeks(
                        loan.getDuration()
                );

        LoanSettings settings =
                loanSettingsService
                        .findByDurationInWeeks(
                                approvedDurationWeeks
                        );

        if (settings == null) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Loan settings are not configured for the approved duration."
            );
        }

        /*
         * Re-apply the official settings at approval time.
         *
         * This ensures that approval uses the configured backend
         * pricing rather than values supplied by the frontend.
         */

        applyLoanPricingAndTerms(
                loan,
                approvedDurationWeeks
        );

        loan.setStatus(
                STATUS_APPROVED
        );

        loan.setDisbursedDate(
                LocalDate.now()
        );

        loan.setDueDate(
                LocalDate.now()
                        .plusWeeks(
                                approvedDurationWeeks
                        )
        );

        Loan savedLoan =
                saveLoanWithApplicationNumber(
                        loan
                );

        String approvalMessage =
                "Congratulations "
                        + savedLoan.getApplicantName()
                        + "!\n\n"
                        + "Your loan application "
                        + savedLoan.getApplicationNumber()
                        + " has been APPROVED and successfully disbursed.\n\n"
                        + "LOAN SUMMARY\n"
                        + "────────────────────────────\n"
                        + "Loan Amount: KES "
                        + String.format(
                        "%,.2f",
                        savedLoan.getAmount()
                )
                        + "\nApproved Duration: "
                        + savedLoan.getDuration()
                        + "\nInterest Rate: "
                        + String.format(
                        "%.2f",
                        savedLoan.getInterestRate()
                )
                        + "%"
                        + "\nTotal Repayable: KES "
                        + String.format(
                        "%,.2f",
                        savedLoan.getTotalRepayableAmount()
                )
                        + "\nDue Date: "
                        + savedLoan.getDueDate()
                        + "\nStatus: "
                        + savedLoan.getStatus()
                        + "\n\nThe loan amount has been credited to your account."
                        + "\n\nThank you for choosing CrediCore.";

        // =====================================================
        // CREDIT ACCOUNT
        // =====================================================

        try {

            creditAccountBalance(
                    savedLoan
            );

        } catch (Exception ex) {

            /*
             * IMPORTANT:
             *
             * We log the error, but the approval itself has already
             * been persisted. This is retained from the existing
             * application architecture.
             */

            System.err.println(
                    "Failed to credit account for Loan ID "
                            + savedLoan.getId()
                            + ": "
                            + ex.getMessage()
            );
        }

        // =====================================================
        // CUSTOMER NOTIFICATION
        // =====================================================

        try {

            if (savedLoan.getAccount() != null) {

                notificationService.createNotification(
                        savedLoan.getAccount(),
                        approvalMessage,
                        "LOAN_APPROVED"
                );
            }

        } catch (Exception ex) {

            System.err.println(
                    "Notification failed for Loan ID "
                            + savedLoan.getId()
                            + ": "
                            + ex.getMessage()
            );
        }

        // =====================================================
        // SMS
        // =====================================================

        try {

            if (savedLoan.getPhoneNumber() != null
                    && !savedLoan.getPhoneNumber().isBlank()) {

                smsService.sendSms(
                        savedLoan.getPhoneNumber(),
                        "Congratulations! Your loan of KES "
                                + String.format(
                                "%,.2f",
                                savedLoan.getAmount()
                        )
                                + " has been approved and credited to your account."
                );
            }

        } catch (Exception ex) {

            System.err.println(
                    "SMS delivery failed for Loan ID "
                            + savedLoan.getId()
                            + ": "
                            + ex.getMessage()
            );
        }

        // =====================================================
        // EMAIL
        // =====================================================

        try {

            if (savedLoan.getEmail() != null
                    && !savedLoan.getEmail().isBlank()) {

                emailService.sendEmail(
                        savedLoan.getEmail(),
                        "Loan Approved",
                        approvalMessage
                );
            }

        } catch (Exception ex) {

            System.err.println(
                    "Email delivery failed for Loan ID "
                            + savedLoan.getId()
                            + ": "
                            + ex.getMessage()
            );
        }

        return savedLoan;
    }

    // =========================================================
    // STATUS UPDATE
    // =========================================================

    @Override
    @Transactional
    public Loan updateLoanStatus(
            Long loanId,
            String status
    ) {

        Loan loan =
                loanRepository
                        .findByIdWithAccount(
                                loanId
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Loan not found"
                                )
                        );

        if (status == null
                || status.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Loan status is required."
            );
        }

        String normalizedStatus =
                status.trim().toUpperCase();

        if (STATUS_APPROVED.equals(
                normalizedStatus
        )) {

            if (!STATUS_AWAITING_AUTHORIZATION.equalsIgnoreCase(
                    loan.getStatus()
            )) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Only loans awaiting authorization can be approved."
                );
            }

            return approveLoanProcess(
                    loan
            );
        }

        loan.setStatus(
                normalizedStatus
        );

        return loanRepository.save(
                loan
        );
    }

    // =========================================================
    // APPROVE LOAN
    // =========================================================

    @Override
    @Transactional
    public Loan approveLoan(
            Long loanId
    ) {

        Loan loan =
                loanRepository
                        .findByIdWithAccount(
                                loanId
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Loan not found"
                                )
                        );

        if (!STATUS_AWAITING_AUTHORIZATION.equalsIgnoreCase(
                loan.getStatus()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only loans awaiting authorization can be approved."
            );
        }

        return approveLoanProcess(
                loan
        );
    }

    // =========================================================
    // REJECT LOAN
    // =========================================================

    @Override
    @Transactional
    public Loan rejectLoan(
            Long loanId,
            String reason
    ) {

        Loan loan =
                loanRepository
                        .findByIdWithAccount(
                                loanId
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Loan not found"
                                )
                        );

        if (!STATUS_AWAITING_AUTHORIZATION.equalsIgnoreCase(
                loan.getStatus()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only loans awaiting authorization can be rejected."
            );
        }

        if (reason == null
                || reason.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A rejection reason is required."
            );
        }

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        User user = null;

        if (authentication != null
                && authentication.getName() != null
                && !authentication.getName().isBlank()) {

            user =
                    userRepository
                            .findByEmail(
                                    authentication.getName()
                            )
                            .orElse(null);
        }

        loan.setStatus(
                STATUS_REJECTED
        );

        loan.setRejectionReason(
                reason.trim()
        );

        loan.setRejectedAt(
                LocalDateTime.now()
        );

        if (user != null) {

            loan.setRejectedByName(
                    user.getName()
            );

            loan.setRejectedByRole(
                    user.getRole().name()
            );
        }

        Loan saved =
                loanRepository.saveAndFlush(
                        loan
                );

        String message =
                "Dear "
                        + saved.getApplicantName()
                        + ", your loan application "
                        + saved.getApplicationNumber()
                        + " has been rejected.\n\n"
                        + "Reason:\n"
                        + saved.getRejectionReason();

        // =====================================================
        // NOTIFICATION
        // =====================================================

        try {

            if (saved.getAccount() != null) {

                notificationService.createNotification(
                        saved.getAccount(),
                        message,
                        "LOAN_REJECTED"
                );
            }

        } catch (Exception ex) {

            System.err.println(
                    "Rejection notification failed for Loan ID "
                            + saved.getId()
                            + ": "
                            + ex.getMessage()
            );
        }

        // =====================================================
        // SMS
        // =====================================================

        try {

            if (saved.getPhoneNumber() != null
                    && !saved.getPhoneNumber().isBlank()) {

                smsService.sendSms(
                        saved.getPhoneNumber(),
                        message
                );
            }

        } catch (Exception ex) {

            System.err.println(
                    "Rejection SMS failed for Loan ID "
                            + saved.getId()
                            + ": "
                            + ex.getMessage()
            );
        }

        // =====================================================
        // EMAIL
        // =====================================================

        try {

            if (saved.getEmail() != null
                    && !saved.getEmail().isBlank()) {

                emailService.sendEmail(
                        saved.getEmail(),
                        "Loan Rejected",
                        message
                );
            }

        } catch (Exception ex) {

            System.err.println(
                    "Rejection email failed for Loan ID "
                            + saved.getId()
                            + ": "
                            + ex.getMessage()
            );
        }

        return saved;
    }

    // =========================================================
    // CREDIT ACCOUNT BALANCE
    // =========================================================

    private void creditAccountBalance(
            Loan loan
    ) {

        Account account =
                loan.getAccount();

        if (account == null) {
            return;
        }

        boolean alreadyDisbursed =
                statementService
                        .getByNationalId(
                                account.getNationalId()
                        )
                        .stream()
                        .anyMatch(
                                s ->
                                        s.getDescription() != null
                                                && s.getDescription()
                                                .contains(
                                                        "Loan Disbursement - Loan ID "
                                                                + loan.getId()
                                                )
                        );

        if (alreadyDisbursed) {

            System.err.println(
                    "Loan ID "
                            + loan.getId()
                            + " has already been disbursed. Skipping duplicate credit."
            );

            return;
        }

        double currentBalance =
                account.getBalance() == null
                        ? 0.0
                        : account.getBalance();

        double newBalance =
                currentBalance
                        + loan.getAmount();

        account.setBalance(
                newBalance
        );

        accountRepository.save(
                account
        );

        Statement statement =
                new Statement();

        statement.setNationalId(
                account.getNationalId()
        );

        statement.setDate(
                LocalDateTime.now()
        );

        statement.setDescription(
                "Loan Disbursement - Loan ID "
                        + loan.getId()
        );

        statement.setCredit(
                loan.getAmount()
        );

        statement.setDebit(
                0.0
        );

        statement.setBalanceAfter(
                newBalance
        );

        statementService.save(
                statement
        );
    }

    // =========================================================
    // IMAGE / APPLICATION UPDATE
    // =========================================================

    @Override
    @Transactional
    public Loan updateLoanWithImage(
            Long id,
            String applicantName,
            String idNo,
            String phoneNumber,
            String location,
            Double amount,
            Double monthlyIncome,
            Double monthlyExpenses,
            String applicationDate,
            String email,
            String dueDate,
            MultipartFile file
    ) {

        if (id == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Loan ID is required."
            );
        }

        if (amount == null
                || amount.isNaN()
                || amount.isInfinite()
                || amount <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Loan amount must be greater than zero."
            );
        }

        // =====================================================
        // FIND EXISTING LOAN
        // =====================================================

        Loan loan =
                loanRepository
                        .findByIdWithAccount(
                                id
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Loan not found."
                                )
                        );

        // =====================================================
        // AUTHORITATIVE ACCOUNT
        // =====================================================

        if (idNo == null
                || idNo.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "National ID is required."
            );
        }

        Account account =
                accountRepository
                        .findByNationalId(
                                idNo.trim()
                        )
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST,
                                        "Account not found."
                                )
                        );

        // =====================================================
        // ACCOUNT STATUS
        // =====================================================

        if (!"ACTIVE".equalsIgnoreCase(
                account.getAccountStatus()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only active accounts may submit loan applications."
            );
        }

        // =====================================================
        // VERIFIED FINANCIAL INFORMATION
        // =====================================================

        validateApplicantFinancialInformation(
                account
        );

        // =====================================================
        // AUTHORITATIVE CUSTOMER DATA
        // =====================================================

        loan.setPhoneNumber(
                account.getPhoneNumber()
        );

        loan.setApplicantName(
                account.getFullName()
        );

        loan.setLocation(
                account.getLocation()
        );

        loan.setEmail(
                account.getEmail()
        );

        loan.setAccount(
                account
        );

        // =====================================================
        // ONLY CREDIT INPUT
        // =====================================================

        loan.setAmount(
                amount
        );

        // =====================================================
        // BACKEND APPLICATION DATE
        // =====================================================

        loan.setApplicationDate(
                LocalDate.now()
        );

        // =====================================================
        // NEVER ACCEPT FRONTEND DUE DATE
        // =====================================================

        loan.setDueDate(
                null
        );

        loan.setDisbursedDate(
                null
        );

        // =====================================================
        // IMAGE
        // =====================================================

        handleImageUpload(
                file,
                account
        );

        accountRepository.save(
                account
        );

        // =====================================================
        // AUTHORITATIVE CREDIT DTO
        // =====================================================

        LoanApplicationDTO dto =
                new LoanApplicationDTO();

        dto.setApplicantName(
                account.getFullName()
        );

        String accountNationalId =
                account.getNationalId();

        if (accountNationalId == null
                || accountNationalId.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Account National ID is missing."
            );
        }

        try {

            dto.setIdNo(
                    Long.parseLong(
                            accountNationalId.trim()
                    )
            );

        } catch (NumberFormatException ex) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid National ID."
            );
        }

        dto.setPhoneNumber(
                account.getPhoneNumber()
        );

        dto.setLocation(
                account.getLocation()
        );

        dto.setAmount(
                amount
        );

        dto.setMonthlyIncome(
                account.getMonthlyIncome()
        );

        /*
         * Null expenses are intentionally converted to zero.
         */
        dto.setMonthlyExpenses(
                account.getMonthlyExpenses() == null
                        ? 0.0
                        : account.getMonthlyExpenses()
        );

        // =====================================================
        // AUTHORITATIVE REASSESSMENT
        // =====================================================

        LoanDecision decision =
                loanEligibilityService.assessEligibility(
                        dto
                );

        if (decision == null) {

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Loan eligibility engine returned no decision."
            );
        }

        // =====================================================
        // VALIDATE AMOUNT
        // =====================================================

        validateRequestedAmountAgainstDecision(
                amount,
                decision
        );

        // =====================================================
        // CLEAR PREVIOUS CREDIT TERMS
        // =====================================================

        loan.setDuration(
                null
        );

        loan.setInterestRate(
                0.0
        );

        loan.setPenaltyRate(
                0.0
        );

        loan.setTotalRepayableAmount(
                0.0
        );

        loan.setDueAmount(
                0.0
        );

        loan.setAmountPaid(
                0.0
        );

        loan.setDueDate(
                null
        );

        loan.setDisbursedDate(
                null
        );

        // =====================================================
        // APPLY TERMS ONLY IF ELIGIBLE
        // =====================================================

        if (decision.isEligible()) {

            int approvedDurationWeeks =
                    getApprovedDurationWeeks(
                            decision
                    );

            applyLoanPricingAndTerms(
                    loan,
                    approvedDurationWeeks
            );
        }

        // =====================================================
        // FINAL DECISION
        // =====================================================

        return processDecisionAndSave(
                loan,
                decision
        );
    }

    // =========================================================
    // IMAGE UPLOAD
    // =========================================================

    private void handleImageUpload(
            MultipartFile file,
            Account account
    ) {

        if (file == null
                || file.isEmpty()) {

            return;
        }

        try {

            String originalFilename =
                    file.getOriginalFilename();

            String cleanFilename =
                    StringUtils.cleanPath(
                            originalFilename != null
                                    ? originalFilename
                                    : "profile-image"
                    );

            String filename =
                    System.currentTimeMillis()
                            + "_"
                            + cleanFilename;

            Path uploadPath =
                    Paths.get(
                            "uploads/profile/"
                    );

            Files.createDirectories(
                    uploadPath
            );

            Path filePath =
                    uploadPath.resolve(
                            filename
                    );

            Files.copy(
                    file.getInputStream(),
                    filePath,
                    StandardCopyOption.REPLACE_EXISTING
            );

            account.setImageUrl(
                    "uploads/profile/"
                            + filename
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to upload image: "
                            + e.getMessage(),
                    e
            );
        }
    }

    // =========================================================
    // DELETE LOAN
    // =========================================================

    @Override
    @Transactional
    public void deleteLoan(
            Long id
    ) {

        Loan loan =
                loanRepository
                        .findById(
                                id
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Loan not found"
                                )
                        );

        Account account =
                loan.getAccount();

        loanRepository.deleteById(
                id
        );

        try {

            if (account != null) {

                notificationService.createNotification(
                        account,
                        "Your loan has been deleted by admin.",
                        "LOAN_DELETED"
                );
            }

        } catch (Exception ex) {

            System.err.println(
                    "Loan deletion notification failed: "
                            + ex.getMessage()
            );
        }
    }

    // =========================================================
    // REFUND LOAN
    // =========================================================

    @Override
    @Transactional
    public Loan refundLoan(
            Long id
    ) {

        Loan loan =
                loanRepository
                        .findById(
                                id
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Loan not found"
                                )
                        );

        if (!STATUS_REJECTED.equalsIgnoreCase(
                loan.getStatus()
        )
                && !STATUS_AUTO_REJECTED.equalsIgnoreCase(
                loan.getStatus()
        )) {

            throw new IllegalStateException(
                    "Refund allowed only for rejected loans."
            );
        }

        loan.setAmount(
                0.0
        );

        loan.setTotalRepayableAmount(
                0.0
        );

        loan.setDueAmount(
                0.0
        );

        loan.setInterestRate(
                0.0
        );

        loan.setPenaltyRate(
                0.0
        );

        loan.setDueDate(
                null
        );

        loan.setDisbursedDate(
                null
        );

        return loanRepository.save(
                loan
        );
    }

    // =========================================================
    // OVERDUE CHARGES
    // =========================================================

    @Override
    @Transactional
    public Loan applyOverdueCharges(
            Loan loan
    ) {

        if (loan == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Loan is required."
            );
        }

        if (loan.getDuration() == null
                || loan.getDuration().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot apply overdue charges because loan duration is missing."
            );
        }

        int weeks =
                parseWeeks(
                        loan.getDuration()
                );

        LoanSettings settings =
                loanSettingsService
                        .findByDurationInWeeks(
                                weeks
                        );

        if (settings == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Loan settings not configured for "
                            + weeks
                            + " weeks."
            );
        }

        loan.setInterestRate(
                settings.getInterestRate()
        );

        loan.setPenaltyRate(
                settings.getPenaltyRate()
        );

        double totalRepayable =
                loan.getAmount()
                        + (
                        loan.getAmount()
                                * settings.getInterestRate()
                                / 100
                )
                        + (
                        loan.getAmount()
                                * settings.getPenaltyRate()
                                / 100
                );

        loan.setTotalRepayableAmount(
                totalRepayable
        );

        double amountPaid =
                loan.getAmountPaid() == null
                        ? 0.0
                        : loan.getAmountPaid();

        loan.setDueAmount(
                Math.max(
                        totalRepayable - amountPaid,
                        0.0
                )
        );

        loan.setStatus(
                STATUS_OVERDUE
        );

        Loan saved =
                loanRepository.save(
                        loan
                );

        String notificationMessage =
                "Dear "
                        + saved.getApplicantName()
                        + ",\n\n"
                        + "Your loan repayment is now OVERDUE.\n\n"
                        + "LOAN DETAILS\n"
                        + "────────────────────────────\n"
                        + "Application Reference: "
                        + saved.getApplicationNumber()
                        + "\nLoan ID: "
                        + saved.getId()
                        + "\nLoan Amount: KES "
                        + String.format(
                        "%,.2f",
                        saved.getAmount()
                )
                        + "\nOutstanding Balance: KES "
                        + String.format(
                        "%,.2f",
                        saved.getDueAmount()
                )
                        + "\nInterest Rate: "
                        + String.format(
                        "%.2f",
                        saved.getInterestRate()
                )
                        + "%"
                        + "\nPenalty Rate: "
                        + String.format(
                        "%.2f",
                        saved.getPenaltyRate()
                )
                        + "%"
                        + "\nDue Date: "
                        + saved.getDueDate()
                        + "\nStatus: "
                        + saved.getStatus()
                        + "\n\nImmediate payment is required to avoid additional penalties and possible recovery action."
                        + "\n\nIf payment has already been made, kindly disregard this notification."
                        + "\n\nCrediCore Intelligent Lending Platform";

        try {

            if (saved.getAccount() != null) {

                notificationService.createNotification(
                        saved.getAccount(),
                        notificationMessage,
                        "LOAN_OVERDUE"
                );
            }

        } catch (Exception ex) {

            System.err.println(
                    "Overdue notification failed: "
                            + ex.getMessage()
            );
        }

        return saved;
    }

    // =========================================================
    // GET LOAN BY ID
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Loan getLoanById(
            Long id
    ) {

        Loan loan =
                loanRepository
                        .findByIdWithAccount(
                                id
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Loan not found"
                                )
                        );

        BigDecimal totalPaid =
                repaymentRepository
                        .findTotalPaidByLoanId(
                                id
                        );

        double paid =
                totalPaid == null
                        ? 0.0
                        : totalPaid.doubleValue();

        loan.setAmountPaid(
                paid
        );

        loan.setDueAmount(
                Math.max(
                        loan.getTotalRepayableAmount()
                                - paid,
                        0.0
                )
        );

        return loan;
    }

    // =========================================================
    // GET ACCOUNT
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Account getAccountById(
            Long id
    ) {

        return accountRepository
                .findById(id)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Account not found"
                        )
                );
    }

    // =========================================================
    // GET LOANS BY ACCOUNT
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<Loan> getLoansByAccount(
            Long accountId
    ) {

        return loanRepository
                .findByAccount_Id(
                        accountId
                );
    }

    // =========================================================
    // GET STAFF
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public List<Staff> getAllStaff() {

        return staffRepo.findAll();
    }

    // =========================================================
    // ASSIGN STAFF
    // =========================================================

    @Override
    @Transactional
    public Loan assignStaff(
            Long loanId,
            Long staffId
    ) {

        Loan loan =
                loanRepository
                        .findByIdWithAccount(
                                loanId
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Loan not found"
                                )
                        );

        Staff staff =
                staffRepo
                        .findById(
                                staffId
                        )
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Staff not found"
                                )
                        );

        loan.setAssignedStaff(
                staff
        );

        try {

            if (loan.getAccount() != null) {

                notificationService.createNotification(
                        loan.getAccount(),
                        "Loan Assignment: Borrower="
                                + loan.getApplicantName()
                                + ", Amount=KES "
                                + String.format(
                                "%,.2f",
                                loan.getAmount()
                        )
                                + ", Loan ID="
                                + loan.getId()
                                + ", Assigned To="
                                + staff.getName()
                                + ", Status="
                                + loan.getStatus(),
                        "STAFF_ASSIGNED"
                );
            }

        } catch (Exception ex) {

            System.err.println(
                    "Staff assignment notification failed: "
                            + ex.getMessage()
            );
        }

        return loanRepository.save(
                loan
        );
    }

    // =========================================================
    // UPDATE LOAN
    // =========================================================

    @Override
    public Loan updateLoan(
            Long id,
            Loan updatedLoan
    ) {

        throw new UnsupportedOperationException(
                "General loan update is not enabled. "
                        + "Use the dedicated loan approval, rejection, "
                        + "repayment or application assessment workflow."
        );
    }

    // =========================================================
    // GET LOAN BY APPLICATION NUMBER
    // =========================================================

    @Override
    @Transactional(readOnly = true)
    public Loan getLoanByApplicationNumber(
            String applicationNumber
    ) {

        if (applicationNumber == null
                || applicationNumber.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Application number is required."
            );
        }

        return loanRepository
                .findByApplicationNumber(
                        applicationNumber.trim()
                )
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Loan application not found: "
                                        + applicationNumber
                        )
                );
    }
}