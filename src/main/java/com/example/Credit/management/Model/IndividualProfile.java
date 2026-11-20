package com.example.Credit.management.Model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

@Data
@Entity
@Table(name="individual_profiles")
public class IndividualProfile {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name="account_id")
    private Account account;

    private String fullName;

    private String nationalId;

    private String phoneNumber;

    private String email;

    private LocalDate dateOfBirth;

    private String gender;

    private String residentialAddress;

    private String city;

    private String county;

    private String employmentStatus;

    private String employerName;

    private String businessType;

    private String businessLocation;

    private Double monthlyIncome;

    private Double monthlyExpenses;

    private String sourceOfFunds;

    private String accountType;

    private String nextOfKinName;

    private String nextOfKinPhone;

    private String nextOfKinRelationship;

}
