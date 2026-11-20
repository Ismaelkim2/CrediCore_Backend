package com.example.Credit.management.DTO;

import com.example.Credit.management.DTO.DocumentDTO;
import com.example.Credit.management.Model.OrganizationPerson;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class AccountDTO {

    private String fullName;
    private String displayName;
    private String nationalId;
    private String phoneNumber;
    private Double balance;

    private Double borrowedAmount;
    private Double netBalance;

    private String accountNumber;

    private String imageUrl;

    private String email;

    private LocalDate dateOfBirth;

    private Double monthlyIncome;

    private Long id;

    private String city;
    private String county;

    private LocalDateTime createdAt;

    private String modeOfOperation;

    private String accountStatus;

    private LocalDateTime lastTransactionDate;

    private String organizationImageUrl;

    private LocalDateTime closedAt;

    private String closureReason;

    private String organizationName;

    private String registrationNumber;

    private String customerCategory;

    private Integer totalOfficials;

    private Integer totalSignatories;

    private String approvalStatus;

    private List<OrganizationPerson> officials =
            new ArrayList<>();

    /*
     * ALL CUSTOMER DOCUMENTS
     *
     * Individual:
     *     Document
     *
     * Organization:
     *     OrganizationDocument
     */
    private List<DocumentDTO> documents =
            new ArrayList<>();

    private Double monthlyExpenses;

    public void setMonthlyExpenses(Double monthlyExpenses) {
        this.monthlyExpenses = monthlyExpenses;
    }
}

