package com.example.Credit.management.controller;

import com.example.Credit.management.DTO.LoanRejectRequest;
import com.example.Credit.management.DTO.RepaymentDTO;
import com.example.Credit.management.DTO.RepaymentTransactionDTO;
import com.example.Credit.management.Model.Account;
import com.example.Credit.management.Model.Loan;
import com.example.Credit.management.Model.Notification;
import com.example.Credit.management.Repository.LoanRepository;
import com.example.Credit.management.services.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/loans")
public class LoanController {

    @Autowired private LoanService loanService;
    @Autowired private RepaymentService repaymentService;
    @Autowired private AccountServiceImpl accountService;
    @Autowired private LoanRiskService loanRiskService;
    @Autowired private NotificationService notificationService;
    @Autowired private SmsService smsService;
    @Autowired private EmailService emailService;
    @Autowired private LoanRepository loanRepository;

    @GetMapping
    public List<Loan> getAllLoans() {
        return loanService.getAllLoans()
                .stream()
                .map(repaymentService::attachLiveDueAmount)
                .toList();
    }

    @GetMapping("/search/{idNo}")
    public ResponseEntity<Account> searchAccount(@PathVariable String idNo) {
        Account account = accountService.getByNationalId(idNo);
        if (account == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(account);
    }

    @GetMapping("/by-nationalId/{idNo}")
    public ResponseEntity<Loan> getLoanByNationalId(@PathVariable String idNo) {
        Optional<Loan> loan = loanRepository.findTopByIdNoOrderByApplicationDateDesc(idNo);

        if (loan.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Loan loanWithDue = repaymentService.attachLiveDueAmount(loan.get());
        return ResponseEntity.ok(loanWithDue);
    }



    // ====================== GET ALL LOANS WITH LIVE DUE ======================
    @GetMapping("/all-with-due")
    public List<Loan> getAllLoansWithDue() {
        List<Loan> loans = loanService.getAllLoans();
        // Attach live due amounts to all loans
        List<Loan> loansWithDue = new ArrayList<>();
        for (Loan loan : loans) {
            loansWithDue.add(repaymentService.attachLiveDueAmount(loan));
        }
        return loansWithDue;
    }

    // ====================== GET LOAN BY ID WITH LIVE DUE ======================
    @GetMapping("/{id}")
    public ResponseEntity<Loan> getLoanById(@PathVariable Long id) {
        return loanRepository.findById(id)
                .map(loan -> ResponseEntity.ok(repaymentService.attachLiveDueAmount(loan)))
                .orElse(ResponseEntity.notFound().build());
    }

    // ====================== CREATE LOAN WITH IMAGE ======================
    @PostMapping(value = "/add-with-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createLoanWithImage(
            @RequestParam String applicantName,
            @RequestParam Long idNo,
            @RequestParam Long phoneNumber,
            @RequestParam String location,
            @RequestParam Double amount,
            @RequestParam String duration,
            @RequestParam(required = false) String applicationDate,
            @RequestParam(required = false) String dueDate,
            @RequestParam(required = false) MultipartFile file
    ) {
        try {
            Account account = accountService.getByNationalId(String.valueOf(idNo));
            if (account == null)
                return ResponseEntity.badRequest().body(Map.of("message", "Account not found"));

            double limit = loanRiskService.calculateLoanLimit(account);
            if (amount > limit)
                return ResponseEntity.badRequest().body(Map.of(
                        "message", "Requested amount exceeds risk-based limit",
                        "eligibleLimit", limit
                ));

            Loan loan = new Loan();
            loan.setApplicantName(applicantName);
            loan.setIdNo(String.valueOf(idNo));
            loan.setPhoneNumber(phoneNumber);
            loan.setLocation(location);
            loan.setAmount(amount);
            loan.setDuration(duration);
            loan.setAccount(account);
            loan.setEmail(account.getEmail());
            loan.setApplicationDate(applicationDate != null ? LocalDate.parse(applicationDate) : LocalDate.now());
            if (dueDate != null) loan.setDueDate(LocalDate.parse(dueDate));
            loan.setStatus("AWAITING_AUTHORIZATION");

            // Handle file upload
            if (file != null && !file.isEmpty()) {
                String filename = System.currentTimeMillis() + "_" + StringUtils.cleanPath(file.getOriginalFilename());
                Path uploadPath = Paths.get("uploads/loan_docs/");
                if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);
                Path filePath = uploadPath.resolve(filename);
                Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                loan.setUserImageUrl(filePath.toString());
            }

            Loan savedLoan = loanService.saveLoan(loan);
            return ResponseEntity.ok(savedLoan);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Loan creation failed: " + e.getMessage()));
        }
    }

    // ====================== UPDATE LOAN STATUS ======================
    @PatchMapping("/{id}/status")
    public ResponseEntity<Loan> updateStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        Loan updated = loanService.updateLoanStatus(id, request.getStatus());
        return ResponseEntity.ok(updated);
    }

    // ====================== DELETE LOAN ======================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteLoan(@PathVariable Long id) {
        loanService.deleteLoan(id);
        return ResponseEntity.ok(Map.of("message", "Loan deleted successfully"));
    }

    // ====================== GET RISK LIMIT ======================
    @GetMapping("/risk-limit/{idNo}")
    public ResponseEntity<Map<String, Object>> getRiskLimit(@PathVariable Long idNo) {
        Account account = accountService.getAccountByNationalId(idNo);
        if (account == null) return ResponseEntity.notFound().build();
        double limit = loanRiskService.calculateLoanLimit(account);
        return ResponseEntity.ok(Map.of("limit", limit));
    }

    // ====================== CHECK LOAN STATUS ======================
    @GetMapping("/check-loan-status/{idNo}")
    public ResponseEntity<Map<String, Object>> checkLoanStatus(@PathVariable Long idNo) {
        Optional<Loan> activeLoan = loanRepository.findActiveLoanByIdNo(String.valueOf(idNo));
        if (activeLoan.isPresent()) {
            Loan loan = repaymentService.attachLiveDueAmount(activeLoan.get());
            return ResponseEntity.ok(Map.of(
                    "hasActiveLoan", true,
                    "activeLoanAmount", loan.getAmount(),
                    "dueAmount", loan.getDueAmount(),
                    "status", loan.getStatus(),
                    "message", "Existing active loan found"
            ));
        }

        Account account = accountService.getAccountByNationalId(idNo);
        if (account == null)
            return ResponseEntity.ok(Map.of(
                    "hasActiveLoan", false,
                    "eligible", false,
                    "reason", "Account not found"
            ));

        double limit = loanRiskService.calculateLoanLimit(account);
        return ResponseEntity.ok(Map.of(
                "hasActiveLoan", false,
                "eligible", limit > 0,
                "eligibleAmount", limit,
                "message", "Loan eligibility determined"
        ));
    }

    // ====================== STATUS REQUEST DTO ======================
    static class StatusRequest {
        private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    @PatchMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> rejectLoan(
            @PathVariable Long id,
            @RequestBody LoanRejectRequest request
    ) {
        Loan loan = loanService.rejectLoan(id, request.getReason());

        notificationService.createNotification(
                loan.getId(),              // ✅ loanId
                "Loan Rejected",            // message
                "LOAN_REJECTION",           // type
                loan.getEmail()             // createdBy / recipient
        );

        return ResponseEntity.ok(loan);
    }

    @GetMapping("/{id}/transactions")
    public ResponseEntity<List<RepaymentTransactionDTO>> getLoanTransactions(@PathVariable Long id) {
        return ResponseEntity.ok(repaymentService.getLoanTransactions(id));
    }



}
