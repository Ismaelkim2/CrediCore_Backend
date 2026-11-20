package com.example.Credit.management.DTO;

import com.example.Credit.management.Model.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CustomerProfileDTO {

    //==========================
    // BASIC
    //==========================

    private Long id;
    private String accountNumber;
    private String cifNumber;

    private CustomerCategory customerCategory;

    private String accountType;
    private String accountClass;
    private String accountStatus;
    private String approvalStatus;

    private Double balance;
    private Double borrowedAmount;
    private Double netBalance;

    //==========================
    // PERSONAL
    //==========================

    private String fullName;
    private String nationalId;
    private LocalDate dateOfBirth;
    private String gender;
    private String modeOfOperation;

    //==========================
    // CONTACT
    //==========================

    private String phoneNumber;
    private String email;

    private String residentialAddress;
    private String city;
    private String county;
    private String country;

    //==========================
    // EMPLOYMENT
    //==========================

    private String employmentStatus;
    private String employerName;
    private String businessType;
    private String businessLocation;

    private Double monthlyIncome;
    private Double monthlyExpenses;

    private String sourceOfFunds;

    //==========================
    // NEXT OF KIN
    //==========================

    private String nextOfKinName;
    private String nextOfKinPhone;
    private String nextOfKinRelationship;

    //==========================
    // ACCOUNT
    //==========================

    private String branch;
    private String branchCode;
    private String currency;

    private LocalDateTime createdAt;
    private LocalDateTime lastTransactionDate;

    private LocalDateTime closedAt;

    private String closureReason;

    //==========================
    // COMPLIANCE
    //==========================

    private String riskRating;
    private String amlStatus;
    private String kycStatus;

    private String relationshipManager;

    //==========================
    // ORGANIZATION
    //==========================

    private String organizationName;
    private String registrationNumber;
    private LocalDate registrationDate;

    private String kraPin;
    private String industry;

    private String website;

    private String postalAddress;

    private Double annualTurnover;

    private Double authorizedCapital;

    private Integer numberOfEmployees;

    //==========================
    // SETTINGS
    //==========================

    private Boolean smsAlerts;
    private Boolean emailAlerts;
    private Boolean internetBanking;
    private Boolean mobileBanking;

    private Boolean debitCard;
    private Boolean chequeBook;

    //==========================
    // IMAGE
    //==========================

    private String imageUrl;

    //==========================
    // PEOPLE
    //==========================

    private List<OrganizationPerson> officials;

    private List<OrganizationPerson> signatories;

    //==========================
    // DOCUMENTS
    //==========================

    private List<DocumentDTO> documents;

    //==========================
    // LOANS
    //==========================

    private List<Loan> loans;

    //==========================
    // STATEMENTS
    //==========================

    private List<TransactionDTO> transactions;

}