package com.example.Credit.management.Model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =========================

    @Column(columnDefinition = "TEXT")
    private String message;

    private LocalDateTime createdAt;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    // LOAN CONTEXT (optional only)
    private Long loanId;

    // TYPE: LOAN_CREATED, LOAN_APPROVED, SMS, EMAIL, etc
    private String type;

    // Who triggered notification (admin/system)
    private String createdBy;

    @ManyToOne
    @JoinColumn(name = "account_id")
    private Account account;
}