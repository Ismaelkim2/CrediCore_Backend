package com.example.Credit.management.DTO;


import lombok.Data;

@Data
public class AccountDTO {
    private String fullName;
    private String nationalId;
    private String phoneNumber;
    private Double balance;
    private Double borrowedAmount;
    private Double netBalance;
    private String accountNumber;
    private String imageUrl;
    private String email;

    // Getters and setters
}
