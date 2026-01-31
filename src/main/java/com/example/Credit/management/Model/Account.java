package com.example.Credit.management.Model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
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

    private Double averageMonthlyTurnover;
    private Double collateralValue;
    private Boolean goodCreditStanding;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private String type;

    // Explicit getter for controller
    public String getEmail() { return email; }

    public Double getBalance() { return balance; }
    public void setBalance(Double balance) { this.balance = balance; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

}
