package com.example.Credit.management.controller;

import com.example.Credit.management.DTO.AccountDTO;
import com.example.Credit.management.DTO.RepaymentTransactionDTO;
import com.example.Credit.management.Model.Account;
import com.example.Credit.management.Model.Loan;
import com.example.Credit.management.Model.Staff;
import com.example.Credit.management.Repository.StaffRepository;
import com.example.Credit.management.services.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    // =========================================================
    // SERVICES
    // =========================================================

    @Autowired
    private LoanService loanService;

    @Autowired
    private RepaymentService repaymentService;

    @Autowired
    private AccountServiceImpl accountService;

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private TransactionService transactionService;


    @Autowired
    private CloudinaryImageStorageService cloudinaryStorage;


    // =========================================================
    // GET ALL LOANS
    // =========================================================

    @GetMapping
    public ResponseEntity<List<Loan>> getAllLoans() {

        List<Loan> loans =
                loanService.getAllLoans();

        List<Loan> enrichedLoans =
                loans.stream()
                        .map(repaymentService::attachLiveDueAmount)
                        .toList();

        return ResponseEntity.ok(enrichedLoans);
    }


    // =========================================================
    // SEARCH ACCOUNT BY NATIONAL ID
    // =========================================================

    @GetMapping("/search/{idNo}")
    public ResponseEntity<Account> searchAccount(
            @PathVariable String idNo
    ) {

        if (idNo == null || idNo.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        Account account =
                accountService.getByNationalId(
                        idNo.trim()
                );

        if (account == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(account);
    }


    // =========================================================
    // GET LOAN BY ID
    // =========================================================

    @GetMapping("/{id}")
    public ResponseEntity<Loan> getLoanById(
            @PathVariable Long id
    ) {

        if (id == null || id <= 0) {
            return ResponseEntity.badRequest().build();
        }

        try {

            Loan loan =
                    loanService.getLoanById(id);

            if (loan == null) {
                return ResponseEntity.notFound().build();
            }

            Loan enrichedLoan =
                    repaymentService.attachLiveDueAmount(
                            loan
                    );

            return ResponseEntity.ok(enrichedLoan);

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .build();
        }
    }


    // =========================================================
    // CREATE LOAN WITH IMAGE
    //
    // FRONTEND CONTROLS ONLY:
    //
    // - applicant/application information
    // - requested amount
    // - optional profile image
    //
    // BACKEND CONTROLS:
    //
    // - verified borrower financial information
    // - eligibility
    // - eligible amount
    // - risk
    // - underwriting score
    // - approved duration
    // - interest rate
    // - repayment amount
    // - due date
    // - approval/rejection
    // - application status
    // =========================================================

    @PostMapping(
            value = "/add-with-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> createLoanWithImage(

            @RequestParam("applicantName")
            String applicantName,

            @RequestParam("idNo")
            String idNo,

            @RequestParam("phoneNumber")
            String phoneNumber,

            @RequestParam("location")
            String location,

            @RequestParam("amount")
            Double amount,

            @RequestParam(
                    value = "email",
                    required = false
            )
            String email,

            @RequestParam(
                    value = "applicationDate",
                    required = false
            )
            String applicationDate,

            @RequestParam(
                    value = "file",
                    required = false
            )
            MultipartFile file
    ) {

        try {

            // =====================================================
            // 1. BASIC INPUT VALIDATION
            // =====================================================

            if (idNo == null || idNo.isBlank()) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                Map.of(
                                        "success",
                                        false,
                                        "message",
                                        "National ID is required."
                                )
                        );
            }

            if (amount == null
                    || amount.isNaN()
                    || amount.isInfinite()
                    || amount <= 0) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                Map.of(
                                        "success",
                                        false,
                                        "message",
                                        "Requested loan amount must be greater than zero."
                                )
                        );
            }

            // =====================================================
            // 2. LOAD VERIFIED ACCOUNT
            // =====================================================

            AccountDTO accountDTO =
                    accountService
                            .getAccountDTOByNationalId(
                                    idNo.trim()
                            );

            if (accountDTO == null) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                Map.of(
                                        "success",
                                        false,
                                        "message",
                                        "No customer account was found for the supplied National ID."
                                )
                        );
            }

            Account account =
                    accountService
                            .getByNationalId(
                                    idNo.trim()
                            );

            if (account == null) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                Map.of(
                                        "success",
                                        false,
                                        "message",
                                        "Customer account could not be loaded."
                                )
                        );
            }

            // =====================================================
            // 3. BUILD LOAN REQUEST
            //
            // IMPORTANT:
            //
            // Do NOT accept or set:
            //
            // - duration
            // - interestRate
            // - riskScore
            // - dueDate
            // - totalRepayableAmount
            // - dueAmount
            // - eligibleAmount
            // - repayment terms
            //
            // LoanService / LoanEligibilityService owns these.
            // =====================================================

            Loan loan =
                    new Loan();

            loan.setApplicantName(
                    applicantName != null
                            ? applicantName.trim()
                            : null
            );

            loan.setPhoneNumber(
                    phoneNumber != null
                            ? phoneNumber.trim()
                            : null
            );

            loan.setLocation(
                    location != null
                            ? location.trim()
                            : null
            );

            loan.setAmount(
                    amount
            );

            loan.setEmail(
                    email != null && !email.isBlank()
                            ? email.trim()
                            : account.getEmail()
            );

            loan.setAccount(
                    account
            );

            // =====================================================
            // 4. APPLICATION DATE
            // =====================================================

            LocalDate parsedApplicationDate =
                    LocalDate.now();

            if (applicationDate != null
                    && !applicationDate.isBlank()) {

                try {

                    parsedApplicationDate =
                            LocalDate.parse(
                                    applicationDate.trim()
                            );

                } catch (Exception e) {

                    return ResponseEntity
                            .badRequest()
                            .body(
                                    Map.of(
                                            "success",
                                            false,
                                            "message",
                                            "Invalid application date. Expected format: yyyy-MM-dd."
                                    )
                            );
                }
            }

            loan.setApplicationDate(
                    parsedApplicationDate
            );


            // =====================================================
            // 5. PROCESS OPTIONAL PROFILE IMAGE - CLOUDINARY
            // =====================================================

            if (file != null && !file.isEmpty()) {

                String imageUrl = cloudinaryStorage.upload(
                        file,
                        "credicore/accounts"
                );

                account.setImageUrl(imageUrl);

                accountService.save(account);
            }


            // =====================================================
            // 6. BACKEND AUTHORITATIVE UNDERWRITING
            //
            // LoanService.saveLoan() now performs:
            //
            // - account verification
            // - financial profile validation
            // - eligibility assessment
            // - affordability calculation
            // - risk calculation
            // - eligible amount
            // - backend-approved duration
            // - interest rate
            // - repayment terms
            // - decision
            // - approval workflow
            //
            // The controller does NOT duplicate underwriting logic.
            // =====================================================

            Loan savedLoan =
                    loanService.saveLoan(
                            loan
                    );

            // =====================================================
            // 7. RECORD APPLICATION TRANSACTION
            // =====================================================

            transactionService
                    .recordLoanApplication(
                            savedLoan
                    );

            // =====================================================
            // 8. RETURN BACKEND-AUTHORITATIVE RESULT
            // =====================================================

            return ResponseEntity.ok(
                    savedLoan
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "success",
                                    false,
                                    "message",
                                    e.getMessage() != null
                                            ? e.getMessage()
                                            : "Invalid loan application."
                            )
                    );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(
                            HttpStatus.INTERNAL_SERVER_ERROR
                    )
                    .body(
                            Map.of(
                                    "success",
                                    false,
                                    "message",
                                    e.getMessage() != null
                                            ? e.getMessage()
                                            : "Loan creation failed.",
                                    "exception",
                                    e.getClass()
                                            .getSimpleName()
                            )
                    );
        }
    }


    // =========================================================
    // UPDATE LOAN WITH IMAGE
    //
    // IMPORTANT:
    //
    // NO DURATION
    // NO INTEREST RATE
    // NO RISK SCORE
    // NO DUE DATE
    //
    // The backend reassesses the application.
    // =========================================================

    @PutMapping(
            value = "/{id}/update-with-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> updateLoanWithImage(

            @PathVariable Long id,

            @RequestParam("applicantName")
            String applicantName,

            @RequestParam("idNo")
            String idNo,

            @RequestParam("phoneNumber")
            String phoneNumber,

            @RequestParam("location")
            String location,

            @RequestParam("amount")
            Double amount,

            @RequestParam("email")
            String email,

            @RequestParam("monthlyIncome")
            Double monthlyIncome,

            @RequestParam(
                    value = "monthlyExpenses",
                    required = false
            )
            Double monthlyExpenses,

            @RequestParam("applicationDate")
            String applicationDate,

            @RequestParam(
                    value = "file",
                    required = false
            )
            MultipartFile file
    ) {

        try {

            if (id == null || id <= 0) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                Map.of(
                                        "success",
                                        false,
                                        "message",
                                        "A valid loan ID is required."
                                )
                        );
            }

            if (idNo == null || idNo.isBlank()) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                Map.of(
                                        "success",
                                        false,
                                        "message",
                                        "National ID is required."
                                )
                        );
            }

            if (amount == null
                    || amount.isNaN()
                    || amount.isInfinite()
                    || amount <= 0) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                Map.of(
                                        "success",
                                        false,
                                        "message",
                                        "Requested loan amount must be greater than zero."
                                )
                        );
            }

            /*
             * Monthly income and expenses are passed through to the
             * service for reassessment.
             *
             * The service remains authoritative and may replace
             * these values with verified account information.
             */
            Loan updatedLoan =
                    loanService.updateLoanWithImage(

                            id,

                            applicantName,

                            idNo,

                            phoneNumber,

                            location,

                            amount,

                            monthlyIncome,

                            monthlyExpenses,

                            applicationDate,

                            email,

                            null,

                            file
                    );

            return ResponseEntity.ok(
                    updatedLoan
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "success",
                                    false,
                                    "message",
                                    e.getMessage() != null
                                            ? e.getMessage()
                                            : "Invalid loan update."
                            )
                    );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(
                            HttpStatus.BAD_REQUEST
                    )
                    .body(
                            Map.of(
                                    "success",
                                    false,
                                    "message",
                                    e.getMessage() != null
                                            ? e.getMessage()
                                            : "Loan update failed.",
                                    "exception",
                                    e.getClass()
                                            .getSimpleName()
                            )
                    );
        }
    }


    // =========================================================
    // GET LOANS BY NATIONAL ID
    // =========================================================

    @GetMapping("/by-nationalId/{idNo}")
    public ResponseEntity<List<Loan>> getLoansByNationalId(
            @PathVariable String idNo
    ) {

        if (idNo == null || idNo.isBlank()) {

            return ResponseEntity.ok(
                    Collections.emptyList()
            );
        }

        Account account =
                accountService.getByNationalId(
                        idNo.trim()
                );

        if (account == null) {

            return ResponseEntity.ok(
                    Collections.emptyList()
            );
        }

        List<Loan> loans =
                loanService.getLoansByAccount(
                        account.getId()
                );

        List<Loan> enrichedLoans =
                loans.stream()
                        .map(repaymentService::attachLiveDueAmount)
                        .toList();

        return ResponseEntity.ok(
                enrichedLoans
        );
    }


    // =========================================================
    // DELETE LOAN
    // =========================================================

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteLoan(
            @PathVariable Long id
    ) {

        try {

            loanService.deleteLoan(id);

            return ResponseEntity.ok(
                    Map.of(
                            "success",
                            true,
                            "message",
                            "Loan deleted successfully."
                    )
            );

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                            Map.of(
                                    "success",
                                    false,
                                    "message",
                                    e.getMessage() != null
                                            ? e.getMessage()
                                            : "Loan deletion failed."
                            )
                    );
        }
    }


    // =========================================================
    // APPROVE LOAN
    // =========================================================

    @PatchMapping("/{id}/approve")
    public ResponseEntity<?> approveLoan(
            @PathVariable Long id
    ) {

        try {

            Loan loan =
                    loanService.approveLoan(id);

            transactionService
                    .recordLoanApproval(
                            loan
                    );

            return ResponseEntity.ok(
                    loan
            );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                            Map.of(
                                    "success",
                                    false,
                                    "message",
                                    e.getMessage() != null
                                            ? e.getMessage()
                                            : "Loan approval failed."
                            )
                    );
        }
    }


    // =========================================================
    // REJECT LOAN
    // =========================================================

    @PatchMapping("/{id}/reject")
    public ResponseEntity<?> rejectLoan(

            @PathVariable Long id,

            @RequestBody
            Map<String, String> payload
    ) {

        try {

            String reason =
                    payload == null
                            ? ""
                            : payload.getOrDefault(
                            "reason",
                            ""
                    );

            if (reason == null
                    || reason.isBlank()) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                Map.of(
                                        "success",
                                        false,
                                        "message",
                                        "A rejection reason is required."
                                )
                        );
            }

            Loan rejectedLoan =
                    loanService.rejectLoan(
                            id,
                            reason.trim()
                    );

            return ResponseEntity.ok(
                    Map.of(
                            "success",
                            true,
                            "message",
                            "Loan rejected successfully.",
                            "loan",
                            rejectedLoan
                    )
            );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                            Map.of(
                                    "success",
                                    false,
                                    "message",
                                    e.getMessage() != null
                                            ? e.getMessage()
                                            : "Loan rejection failed."
                            )
                    );
        }
    }


    // =========================================================
    // LOAN TRANSACTIONS
    // =========================================================

    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<RepaymentTransactionDTO>>
    getTransactions(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(
                repaymentService
                        .getLoanTransactions(id)
        );
    }


    // =========================================================
    // GET ALL STAFF
    // =========================================================

    @GetMapping("/staff")
    public List<Staff> getStaff() {

        return loanService.getAllStaff();
    }


    // =========================================================
    // CREATE STAFF
    // =========================================================

    @PostMapping("/staff")
    public ResponseEntity<Staff> createStaff(
            @RequestBody Staff staff
    ) {

        Staff savedStaff =
                staffRepository.save(
                        staff
                );

        return ResponseEntity.ok(
                savedStaff
        );
    }


    // =========================================================
    // ASSIGN STAFF
    // =========================================================

    @PutMapping(
            "/{loanId}/assign-staff/{staffId}"
    )
    public ResponseEntity<Loan> assignStaff(

            @PathVariable Long loanId,

            @PathVariable Long staffId
    ) {

        Loan loan =
                loanService.assignStaff(
                        loanId,
                        staffId
                );

        return ResponseEntity.ok(
                loan
        );
    }
}