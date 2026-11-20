package com.example.Credit.management.controller;

import com.example.Credit.management.DTO.RepaymentDTO;
import com.example.Credit.management.Model.Loan;
import com.example.Credit.management.Repository.LoanRepository;
import com.example.Credit.management.services.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repayments")
@RequiredArgsConstructor
@Slf4j
public class RepaymentController {

    private final RepaymentService repaymentService;
    private final LoanRepository loanRepository;
    private final LoanService loanService;
    private final MpesaStkService mpesaStkService;

    // =========================
    // ALL LOANS WITH LIVE BALANCE
    // =========================
    @GetMapping("/all")
    public ResponseEntity<List<Loan>> getAllRepayments() {
        List<Loan> loans = loanRepository.findAll();
        loans.forEach(repaymentService::attachLiveDueAmount);
        return ResponseEntity.ok(loans);
    }

    // =========================
    // ALL REPAYMENTS
    // =========================
    @GetMapping("/all-payments")
    public ResponseEntity<List<RepaymentDTO>> getAllRepaymentsIndividually() {
        return ResponseEntity.ok(
                repaymentService.getAllRepaymentsAsDTO()
        );
    }

    // =========================
    // MPESA STATUS
    // =========================
    @GetMapping("/mpesa/status/{id}")
    public Map<String, Object> status(@PathVariable String id) {
        return mpesaStkService.getTransactionStatus(id);
    }

    // =========================
    // MAKE REPAYMENT
    // =========================
    @PostMapping
    public ResponseEntity<Map<String, Object>> makeRepayment(
            @RequestBody RepaymentDTO dto
    ) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 1. Initiate Repayment (Mock thread or Real M-Pesa STK Push)
            Map<String, Object> serviceResponse = repaymentService.saveRepayment(dto);

            Loan loan = repaymentService.attachLiveDueAmount(
                    loanService.getLoanById(dto.getLoanId())
            );

            // =====================================
            // RESPONSE
            // =====================================
            response.putAll(serviceResponse);
            response.put("loanId", loan.getId());
            response.put("remainingDue", loan.getDueAmount());
            response.put(
                    "newBalance",
                    loan.getAccount() != null
                            ? loan.getAccount().getBalance()
                            : null
            );
            response.put(
                    "accountNumber",
                    dto.getAccountNumber() != null
                            ? dto.getAccountNumber()
                            : loan.getAccountNumber()
            );

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {

            response.put("status", "error");
            response.put("message", ex.getMessage());

            return ResponseEntity.badRequest()
                    .body(response);

        } catch (Exception ex) {

            log.error("Repayment processing initiation failed", ex);

            response.put("status", "error");
            response.put(
                    "message",
                    "An unexpected error occurred: " + ex.getMessage()
            );

            return ResponseEntity.internalServerError()
                    .body(response);
        }
    }

    // =========================
    // SEARCH BY PHONE
    // =========================
    @GetMapping("/by-phone/{phone}")
    public ResponseEntity<Loan> getByPhone(
            @PathVariable String phone
    ) {
        return loanRepository
                .findTopByPhoneNumberOrderByIdDesc(phone)
                .map(repaymentService::attachLiveDueAmount)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // =========================
    // STATUS TRACKING
    // =========================
    @GetMapping("/status/{ref}")
    public Map<String, String> getRepaymentStatus(
            @PathVariable String ref
    ) {
        return Map.of(
                "ref", ref,
                "status", repaymentService.getStatus(ref)
        );
    }

    // =========================
    // HEALTH CHECK
    // =========================
    @GetMapping
    public Map<String, String> test() {
        return Map.of(
                "message",
                "✅ Repayment endpoint is live."
        );
    }
}