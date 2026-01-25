package com.example.Credit.management.Model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String accountNumber;
    private String nationalId;
    private String fullName;
    private String phoneNumber;
    private String email;
    private Double balance;
    private String branch;
    private String imageUrl;

    // ===== BANK-GRADE RISK FIELDS =====
    private Double averageMonthlyTurnover;   // Derived from transactions
    private Double collateralValue;           // Secured lending
    private Boolean goodCreditStanding;       // Internal / CRB flag

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private String type;


    // Getters and setters
}



