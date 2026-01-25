package com.example.Credit.management.DTO;


import lombok.Data;

@Data
public class LoanApplicationDTO {
    private String applicantName;
    private Long idNo;
    private Long phoneNumber;
    private double amount;
    private String duration;
    private Double monthlyIncome;
    private Double monthlyExpenses;
    private String location;
    // Getters/setters
}
