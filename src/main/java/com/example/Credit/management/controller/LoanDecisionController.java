package com.example.Credit.management.controller;

import com.example.Credit.management.DTO.LoanApplicationDTO;
import com.example.Credit.management.Model.LoanDecision;
import com.example.Credit.management.services.LoanEligibilityService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/loan-decision")
public class LoanDecisionController {

    private final LoanEligibilityService loanService;

    public LoanDecisionController(LoanEligibilityService loanService) {
        this.loanService = loanService;
    }

    @PostMapping(
            value = "/assess",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<LoanDecision> assess(@RequestBody LoanApplicationDTO dto) {
        LoanDecision decision = loanService.assessEligibility(dto);
        return ResponseEntity.ok(decision);
    }
}
