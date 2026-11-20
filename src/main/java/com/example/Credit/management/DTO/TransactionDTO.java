package com.example.Credit.management.DTO;


import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;



@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDTO {


    private Long id;



    private String transactionReference;

    private String bankReference;

    private String externalReference;

    private String paymentReference;



/*
=========================
CUSTOMER
=========================
*/


    private String customerName;

    private String customerNationalId;

    private String phoneNumber;

    private String email;



/*
=========================
ACCOUNT / LOAN
=========================
*/


    private String accountNumber;

    private String loanNumber;



/*
=========================
TRANSACTION
=========================
*/


    private String transactionType;

    private String status;

    private String channel;


    private BigDecimal amount;


    private BigDecimal principalAmount;

    private BigDecimal interestAmount;

    private BigDecimal penaltyAmount;

    private BigDecimal chargesAmount;



    private BigDecimal balanceBefore;

    private BigDecimal balanceAfter;



    private BigDecimal outstandingLoanBefore;

    private BigDecimal outstandingLoanAfter;




/*
=========================
RISK
=========================
*/


    private Integer riskScore;

    private String riskLevel;

    private String fraudStatus;


    private Boolean amlPassed;

    private Boolean sanctionsPassed;

    private Boolean duplicateTransaction;



/*
=========================
AUDIT
=========================
*/


    private String initiatedBy;

    private String approvedBy;

    private String authorizedBy;



    private String branchCode;

    private String branchName;



/*
=========================
SETTLEMENT
=========================
*/


    private String settlementStatus;

    private LocalDate settlementDate;

    private LocalDate businessDate;



/*
=========================
TIME
=========================
*/


    private LocalDateTime initiatedAt;

    private LocalDateTime authorizedAt;

    private LocalDateTime completedAt;

    private LocalDateTime reversedAt;



/*
=========================
RECEIPT
=========================
*/


    private String receiptNumber;

    private Boolean receiptPrinted;

    private Boolean receiptEmailed;

    private Boolean receiptSmsSent;



    private String narration;

    private String remarks;

    private String mpesaReceiptNumber;


}