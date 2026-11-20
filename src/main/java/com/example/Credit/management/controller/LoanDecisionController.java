package com.example.Credit.management.controller;
import com.example.Credit.management.DTO.LoanApplicationDTO;
import com.example.Credit.management.DTO.LoanAssessmentRequest;
import com.example.Credit.management.Model.Account;
import com.example.Credit.management.Model.Loan;
import com.example.Credit.management.Model.LoanAssessmentResponse;
import com.example.Credit.management.Model.LoanDecision;
import com.example.Credit.management.Repository.LoanRepository;
import com.example.Credit.management.services.AccountServiceImpl;
import com.example.Credit.management.services.CreditIntelligenceEngine;
import com.example.Credit.management.services.LoanEligibilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/loan-decision")
@RequiredArgsConstructor
public class LoanDecisionController {

    private final CreditIntelligenceEngine intelligenceEngine;
    private final AccountServiceImpl accountService;
    private final LoanRepository loanRepository;

    @PostMapping("/assess")
    public ResponseEntity<?> assess(@RequestBody LoanAssessmentRequest request) {

        if (request.getNationalId() == null || request.getNationalId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "National ID is required"));
        }

        Account account = accountService.getByNationalId(request.getNationalId());

        if (account == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Account not found"));
        }

        List<Loan> loans =
                loanRepository.findByAccount_NationalId(request.getNationalId());

        LoanAssessmentResponse response =
                intelligenceEngine.analyze(account, loans, request);

        return ResponseEntity.ok(response);
    }
}
