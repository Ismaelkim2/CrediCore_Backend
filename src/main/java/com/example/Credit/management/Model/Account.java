package com.example.Credit.management.Model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonBackReference;

@Data
@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* ============================================================
       BASIC ACCOUNT INFORMATION
       ============================================================ */

    @Column(unique = true)
    private String accountNumber;

    @Column(unique = true)
    private String phoneNumber;

    @Column(unique = true)
    private String email;

    @Column(unique = true, updatable = false)
    private String nationalId;

    private String fullName;

    private Double balance = 0.0;

    private String branch;

    private String imageUrl;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private String accountStatus = "ACTIVE";

    private LocalDateTime lastTransactionDate;

    private LocalDateTime closedAt;

    @Column(length = 500)
    private String closureReason;

    @Enumerated(EnumType.STRING)
    private CustomerCategory customerCategory;

    @Enumerated(EnumType.STRING)
    private ModeOfOperation modeOfOperation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;


    /* ============================================================
       PERSONAL INFORMATION
       ============================================================ */

    private LocalDate dateOfBirth;

    private String gender;


    /* ============================================================
       RESIDENTIAL INFORMATION
       ============================================================ */

    private String residentialAddress;

    private String city;

    private String county;

    private String location;


    /* ============================================================
       EMPLOYMENT / BUSINESS INFORMATION
       ============================================================ */

    private String employmentStatus;

    private String employerName;

    private String businessType;

    private String businessLocation;

    private Double monthlyIncome;

    private Double monthlyExpenses;

    private String sourceOfFunds;


    /* ============================================================
       ACCOUNT DETAILS
       ============================================================ */

    private String accountType;

    private String organizationName;

    private String registrationNumber;

    private LocalDate registrationDate;

    private String kraPin;

    private String industry;

    private String website;

    private String postalAddress;

    private String country;

    private Double annualTurnover;

    private Double authorizedCapital;

    private Integer numberOfEmployees;

    private String operatingLicenseNumber;

    private String taxComplianceCertificate;

    private String boardResolutionNumber;

    private String constitutionNumber;

    private String trustDeedNumber;

    private String certificateNumber;

    private String primarySignatory;

    private Integer totalOfficials;

    private Integer totalSignatories;

    private String approvalStatus = "PENDING";

    private String approvedBy;

    @Column(length = 1000)
    private String rejectionReason;

    private String riskRating;

    private String amlStatus;

    private String kycStatus;

    @Column(unique = true)
    private String cifNumber;

    private String relationshipManager;

    private String branchCode;

    private String currency = "KES";

    private String accountClass;

    private Integer dormancyMonths = 6;

    private Boolean smsAlerts = true;

    private Boolean emailAlerts = true;

    private Boolean mobileBanking = true;

    private Boolean internetBanking = true;

    private Boolean debitCard = false;

    private Boolean chequeBook = false;

    private Integer profileCompletion = 0;


    /* ============================================================
       NEXT OF KIN
       ============================================================ */

    private String nextOfKinName;

    private String nextOfKinPhone;

    private String nextOfKinRelationship;


    /* ============================================================
       CREDIT & RISK PROFILE
       ============================================================ */

    private Double averageMonthlyTurnover;

    private Double collateralValue;

    private Boolean goodCreditStanding;

    @Column(nullable = false)
    private Boolean notificationSoundEnabled = true;


    /* ============================================================
       ORGANIZATION PROFILE

       Account owns OrganizationProfile.

       Account
          |
          | 1 : 1
          v
       OrganizationProfile
       ============================================================ */

    @OneToOne(
            mappedBy = "account",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @JsonManagedReference("account-organization")
    private OrganizationProfile organizationProfile;
}