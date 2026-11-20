package com.example.Credit.management.Model;

import jakarta.persistence.*;
import lombok.*;
import com.example.Credit.management.Model.TransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "transactions",
        indexes = {

                @Index(name = "idx_transaction_reference", columnList = "transactionReference", unique = true),
                @Index(name = "idx_bank_reference", columnList = "bankReference"),
                @Index(name = "idx_external_reference", columnList = "externalReference"),
                @Index(name = "idx_payment_reference", columnList = "paymentReference"),
                @Index(name = "idx_account_number", columnList = "accountNumber"),
                @Index(name = "idx_loan_number", columnList = "loanNumber"),
                @Index(name = "idx_customer_id", columnList = "customerNationalId"),
                @Index(name = "idx_business_date", columnList = "businessDate"),
                @Index(name = "idx_status", columnList = "status")

        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
    ============================================================
                    REFERENCES
    ============================================================
     */

    @Column(nullable = false, unique = true, length = 40)
    private String transactionReference;

    @Column(length = 40)
    private String bankReference;

    @Column(length = 80)
    private String externalReference;

    @Column(length = 80)
    private String paymentReference;

    @Column(length = 120)
    private String sessionId;

    /*
    ============================================================
                    CUSTOMER
    ============================================================
     */

    private String customerName;

    private String customerNationalId;

    private String phoneNumber;

    private String email;

    /*
    ============================================================
                    ACCOUNT
    ============================================================
     */

    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    /*
    ============================================================
                    LOAN
    ============================================================
     */

    private String loanNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_id")
    private Loan loan;

    /*
    ============================================================
                    TRANSACTION DETAILS
    ============================================================
     */

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    private TransactionChannel channel;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(precision = 18, scale = 2)
    private BigDecimal principalAmount;

    @Column(precision = 18, scale = 2)
    private BigDecimal interestAmount;

    @Column(precision = 18, scale = 2)
    private BigDecimal penaltyAmount;

    @Column(precision = 18, scale = 2)
    private BigDecimal chargesAmount;

    @Column(precision = 18, scale = 2)
    private BigDecimal balanceBefore;

    @Column(precision = 18, scale = 2)
    private BigDecimal balanceAfter;

    @Column(precision = 18, scale = 2)
    private BigDecimal outstandingLoanBefore;

    @Column(precision = 18, scale = 2)
    private BigDecimal outstandingLoanAfter;

    /*
    ============================================================
                    PAYMENT DETAILS
    ============================================================
     */

    private String paymentMethod;

    private String provider;

    private String merchantName;

    private String merchantCode;

    private String tillNumber;

    private String paybillNumber;

    private String terminalId;

    private String cardLastFourDigits;

    /*
    ============================================================
                    RISK & COMPLIANCE
    ============================================================
     */

    private Integer riskScore;

    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    private FraudStatus fraudStatus;

    private Boolean amlPassed;

    private Boolean sanctionsPassed;

    private Boolean duplicateTransaction;

    /*
    ============================================================
                    AUDIT
    ============================================================
     */

    private String initiatedBy;

    private String approvedBy;

    private String authorizedBy;

    private String branchCode;

    private String branchName;

    private String deviceName;

    private String ipAddress;

    private String userAgent;

    /*
    ============================================================
                    SETTLEMENT
    ============================================================
     */

    private String settlementBatch;

    private String settlementReference;

    @Enumerated(EnumType.STRING)
    private SettlementStatus settlementStatus;

    private LocalDate settlementDate;

    private LocalDate businessDate;

    /*
    ============================================================
                    TIMESTAMPS
    ============================================================
     */

    private LocalDateTime initiatedAt;

    private LocalDateTime authorizedAt;

    private LocalDateTime completedAt;

    private LocalDateTime reversedAt;

    /*
    ============================================================
                    RECEIPT
    ============================================================
     */

    private String receiptNumber;

    private Boolean receiptPrinted;

    private Boolean receiptEmailed;

    private Boolean receiptSmsSent;

    /*
    ============================================================
                    REMARKS
    ============================================================
     */

    @Column(length = 1000)
    private String narration;

    @Column(length = 1000)
    private String remarks;

    @Column(name = "mpesa_receipt_number")
    private String mpesaReceiptNumber;
}