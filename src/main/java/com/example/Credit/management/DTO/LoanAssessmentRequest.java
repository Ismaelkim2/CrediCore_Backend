package com.example.Credit.management.DTO;

import lombok.Data;

@Data
public class LoanAssessmentRequest {

    private String nationalId;

    private Double requestedAmount;

    private Double monthlyIncome;

    private String phoneNumber;
}