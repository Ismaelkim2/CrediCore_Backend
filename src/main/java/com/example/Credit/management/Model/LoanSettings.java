package com.example.Credit.management.Model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class LoanSettings {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "duration_in_weeks", nullable = false)
    private Integer durationInWeeks;

    private double interestRate;
    private double penaltyRate;
    private int gracePeriodDays;
    private double maxLoanAmount;
    private boolean autoApproval;
}