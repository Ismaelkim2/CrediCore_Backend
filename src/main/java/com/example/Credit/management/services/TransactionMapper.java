package com.example.Credit.management.services;

import com.example.Credit.management.DTO.TransactionDTO;
import com.example.Credit.management.Model.Transaction;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

        public TransactionDTO toDTO(Transaction tx) {

                if (tx == null) {
                        return null;
                }

                return TransactionDTO.builder()
                        .id(tx.getId())
                        .transactionReference(tx.getTransactionReference())
                        .bankReference(tx.getBankReference())
                        .externalReference(tx.getExternalReference())
                        .paymentReference(tx.getPaymentReference())

                        // ✅ CRITICAL FIX: Map mpesaReceiptNumber to DTO
                        .mpesaReceiptNumber(tx.getMpesaReceiptNumber())

                        .customerName(tx.getCustomerName())
                        .customerNationalId(tx.getCustomerNationalId())
                        .phoneNumber(tx.getPhoneNumber())
                        .email(tx.getEmail())

                        .accountNumber(tx.getAccountNumber())
                        .loanNumber(tx.getLoanNumber())

                        .transactionType(
                                tx.getTransactionType() != null ? tx.getTransactionType().name() : null
                        )

                        .status(
                                tx.getStatus() != null ? tx.getStatus().name() : null
                        )

                        .channel(
                                tx.getChannel() != null ? tx.getChannel().name() : null
                        )

                        .amount(tx.getAmount())
                        .principalAmount(tx.getPrincipalAmount())
                        .interestAmount(tx.getInterestAmount())
                        .penaltyAmount(tx.getPenaltyAmount())
                        .chargesAmount(tx.getChargesAmount())

                        .balanceBefore(tx.getBalanceBefore())
                        .balanceAfter(tx.getBalanceAfter())

                        .outstandingLoanBefore(tx.getOutstandingLoanBefore())
                        .outstandingLoanAfter(tx.getOutstandingLoanAfter())

                        .riskScore(tx.getRiskScore())

                        .riskLevel(
                                tx.getRiskLevel() != null ? tx.getRiskLevel().name() : null
                        )

                        .fraudStatus(
                                tx.getFraudStatus() != null ? tx.getFraudStatus().name() : null
                        )

                        .amlPassed(tx.getAmlPassed())
                        .sanctionsPassed(tx.getSanctionsPassed())
                        .duplicateTransaction(tx.getDuplicateTransaction())

                        .initiatedBy(tx.getInitiatedBy())
                        .approvedBy(tx.getApprovedBy())
                        .authorizedBy(tx.getAuthorizedBy())

                        .branchCode(tx.getBranchCode())
                        .branchName(tx.getBranchName())

                        .settlementStatus(
                                tx.getSettlementStatus() != null ? tx.getSettlementStatus().name() : null
                        )

                        .settlementDate(tx.getSettlementDate())
                        .businessDate(tx.getBusinessDate())

                        .initiatedAt(tx.getInitiatedAt())
                        .authorizedAt(tx.getAuthorizedAt())
                        .completedAt(tx.getCompletedAt())
                        .reversedAt(tx.getReversedAt())

                        .receiptNumber(tx.getReceiptNumber())
                        .receiptPrinted(tx.getReceiptPrinted())
                        .receiptEmailed(tx.getReceiptEmailed())
                        .receiptSmsSent(tx.getReceiptSmsSent())

                        .narration(tx.getNarration())
                        .remarks(tx.getRemarks())

                        .build();
        }
}