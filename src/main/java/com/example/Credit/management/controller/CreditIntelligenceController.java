package com.example.Credit.management.controller;

import com.example.Credit.management.DTO.LoanAssessmentRequest;
import com.example.Credit.management.Model.Account;
import com.example.Credit.management.Model.Loan;
import com.example.Credit.management.Model.LoanAssessmentResponse;
import com.example.Credit.management.Repository.AccountRepository;
import com.example.Credit.management.services.CreditIntelligenceEngine;
import com.example.Credit.management.services.LoanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/credit-intelligence")
@RequiredArgsConstructor
@CrossOrigin
public class CreditIntelligenceController {

    private final CreditIntelligenceEngine engine;
    private final LoanService loanService;
    private final AccountRepository accountRepository;

    /**
     * =====================================================
     * SINGLE CUSTOMER ANALYSIS
     * =====================================================
     */
    @PostMapping("/analyze/{nationalId}")
    public LoanAssessmentResponse analyze(
            @PathVariable String nationalId,
            @RequestBody LoanAssessmentRequest request
    ) {

        Account account = accountRepository
                .findByNationalId(nationalId.trim())
                .orElseThrow(() ->
                        new RuntimeException("Account not found"));

        List<Loan> previousLoans =
                loanService.getLoansByAccount(account.getId());

        return engine.analyze(
                account,
                previousLoans,
                request
        );
    }


    @PostMapping("/analyze/batch")
    public List<LoanAssessmentResponse> analyzeBatch(
            @RequestBody List<LoanAssessmentRequest> requests
    ) {

        return requests

                .parallelStream()

                .map(request -> {

                    Account account = accountRepository

                            .findByNationalId(
                                    request.getNationalId().trim()
                            )

                            .orElse(null);

                    if (account == null) {

                        return LoanAssessmentResponse.builder()

                                .recommendation("ACCOUNT NOT FOUND")

                                .riskScore(0)

                                .behavioralScore(0)

                                .kycScore(0)

                                .fraudFlag(false)

                                .daysPastDue(0)

                                .completedLoans(0)

                                .overdueLoans(0)

                                .parCategory("UNKNOWN")

                                .predictiveRisk("LOW")

                                .eligibleAmount(0)

                                .aiReason("Customer account not found")

                                .aiConfidence(0)

                                .build();
                    }

                    List<Loan> previousLoans =
                            loanService.getLoansByAccount(account.getId());

                    return engine.analyze(
                            account,
                            previousLoans,
                            request
                    );

                })

                .toList();
    }

}