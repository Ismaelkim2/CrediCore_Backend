package com.example.Credit.management.Model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String applicantName;

    private String idNo;

    @Column(name = "supporting_document_url")
    private String userImageUrl;

    private Long phoneNumber;
    private String location;

    // Default to 0.0 so UI can handle empty state for refunded loans
    @Column(nullable = false)
    private double amount = 0.0;

    private String duration;
    private String status;

    @Column(name = "income")
    private Double income;

    @Column(name = "expense")
    private Double expenses;

    private LocalDate applicationDate;
    private LocalDate disbursedDate;

    // Allow dueDate to be null after refund
    private LocalDate dueDate;

    @Column(nullable = false)
    private double interestRate = 0.0;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private double penaltyRate = 0.0;

    // Explicit default to 0.0
    @Column(name = "total_repayable_amount", nullable = false)
    private Double totalRepayableAmount = 0.0;

    // Loan.java
    @Column(name = "last_reminder_sent")
    private LocalDate lastReminderSent;

    @Column(length = 500)
    private String rejectionReason;

    private LocalDateTime rejectedAt;


    // Transient field used for frontend display
    @Transient
    private Double amountPaid = 0.0;

//    @Transient
@Column(nullable = false)
    private Double dueAmount = 0.0;

    @OneToMany(
            mappedBy = "loan",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @JsonManagedReference
    private List<Repayment> repayments = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "account_id")
    private Account account;

    @JsonProperty("accountNumber")
    public String getAccountNumber() {
        return account != null ? account.getAccountNumber() : null;
    }


}
