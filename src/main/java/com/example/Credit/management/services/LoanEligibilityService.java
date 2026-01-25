package com.example.Credit.management.services;
import com.example.Credit.management.DTO.LoanApplicationDTO;
import com.example.Credit.management.Model.LoanDecision;

public interface LoanEligibilityService {
    LoanDecision assessEligibility(LoanApplicationDTO application);
}
