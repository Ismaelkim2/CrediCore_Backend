package com.example.Credit.management.DTO;

import lombok.Data;

import java.time.LocalDate;

@Data
public class LoanApplicationDTO {

    private Long id;

    private String applicantName;

    private Double amount;

    private String duration;

    private String status;

    private String nationalId;

    private Long idNo;

    private String phoneNumber;

    private String location;

    private Double income;

    private Double expenses;

    private Double monthlyIncome;

    private Double monthlyExpenses;

    private String accountNumber;

    private Double accountBalance;

    private String email;

    private LocalDate applicationDate;

    private LocalDate dueDate;

    private Double dueAmount;

    private Double totalRepayableAmount;
}