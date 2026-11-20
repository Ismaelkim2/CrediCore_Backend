package com.example.Credit.management.services;

import com.example.Credit.management.DTO.RepaymentDTO;
import com.example.Credit.management.DTO.TransactionDTO;
import com.example.Credit.management.Model.*;
import com.example.Credit.management.Repository.TransactionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    /*
    =====================================================
                    BASE TRANSACTION BUILDER
    =====================================================
    */

    private Transaction buildBaseTransaction(Loan loan) {

        Transaction tx = new Transaction();

        tx.setTransactionReference(
                "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()
        );

        tx.setCustomerName(loan.getApplicantName());
        tx.setCustomerNationalId(loan.getNationalId());
        tx.setPhoneNumber(loan.getPhoneNumber());
        tx.setEmail(loan.getEmail());
        tx.setLoanNumber(loan.getAccountNumber());
        tx.setAccountNumber(loan.getAccountNumber());

        tx.setAmount(
                BigDecimal.valueOf(loan.getAmount() == null ? 0.0 : loan.getAmount())
        );

        tx.setInitiatedAt(LocalDateTime.now());
        tx.setFraudStatus(FraudStatus.NOT_CHECKED);

        tx.setRiskScore(
                loan.getRiskScore() == null ? 0 : loan.getRiskScore()
        );

        tx.setRiskLevel(
                determineRiskLevel(loan.getRiskScore() == null ? 0 : loan.getRiskScore())
        );

        tx.setDuplicateTransaction(false);
        tx.setAmlPassed(true);
        tx.setSanctionsPassed(true);

        return tx;
    }

    /*
    =====================================================
                    DETERMINE RISK LEVEL
    =====================================================
    */

    private RiskLevel determineRiskLevel(int score) {
        if (score >= 80) return RiskLevel.CRITICAL;
        if (score >= 60) return RiskLevel.HIGH;
        if (score >= 40) return RiskLevel.MEDIUM;
        return RiskLevel.LOW;
    }

    /*
    =====================================================
                    GET ALL TRANSACTIONS
    =====================================================
    */

    @Override
    public List<TransactionDTO> getAllTransactions() {
        return transactionRepository
                .findAll()
                .stream()
                .map(transactionMapper::toDTO)
                .toList();
    }

    /*
    =====================================================
                    CUSTOMER TRANSACTIONS
    =====================================================
    */

    @Override
    public List<TransactionDTO> getCustomerTransactions(String nationalId) {
        return transactionRepository
                .findByCustomerNationalId(nationalId)
                .stream()
                .map(transactionMapper::toDTO)
                .toList();
    }

    /*
    =====================================================
                    LOAN TRANSACTIONS
    =====================================================
    */

    @Override
    public List<TransactionDTO> getLoanTransactions(String loanNumber) {
        return transactionRepository
                .findByLoanNumber(loanNumber)
                .stream()
                .map(transactionMapper::toDTO)
                .toList();
    }

    /*
    =====================================================
                    FRAUD REVIEW
    =====================================================
    */

    @Override
    public List<TransactionDTO> getFraudReview() {
        return transactionRepository
                .findByFraudStatus(String.valueOf(FraudStatus.UNDER_REVIEW))
                .stream()
                .map(transactionMapper::toDTO)
                .toList();
    }

    /*
    =====================================================
                    GET TRANSACTION BY REFERENCE
    =====================================================
    */

    @Override
    public TransactionDTO getTransactionByReference(String reference) {
        Transaction transaction = transactionRepository
                .findByTransactionReference(reference)
                .orElseThrow(() -> new RuntimeException(
                        "Transaction does not exist: " + reference
                ));

        return transactionMapper.toDTO(transaction);
    }

    /*
    =====================================================
                    RECORD LOAN APPLICATION
    =====================================================
    */

    @Override
    public void recordLoanApplication(Loan loan) {

        System.out.println("Recording loan application...");

        Transaction transaction = buildBaseTransaction(loan);

        transaction.setTransactionType(TransactionType.LOAN_APPLICATION);
        transaction.setStatus(TransactionStatus.INITIATED);
        transaction.setChannel(TransactionChannel.API);
        transaction.setNarration("Loan application submitted.");
        transaction.setInitiatedBy(loan.getApplicantName());

        transactionRepository.save(transaction);

        System.out.println("Saved transaction: " + transaction.getTransactionReference());
    }

    /*
    =====================================================
                    RECORD LOAN APPROVAL
    =====================================================
    */

    @Override
    public void recordLoanApproval(Loan loan) {

        Transaction transaction = buildBaseTransaction(loan);

        transaction.setTransactionType(TransactionType.LOAN_APPROVAL);
        transaction.setStatus(TransactionStatus.AUTHORIZED);
        transaction.setAuthorizedAt(LocalDateTime.now());
        transaction.setAuthorizedBy(loan.getApplicantName());
        transaction.setChannel(TransactionChannel.BRANCH);
        transaction.setNarration("Loan approved successfully.");

        transactionRepository.save(transaction);
    }

    /*
    =====================================================
                    RECORD LOAN DISBURSEMENT
    =====================================================
    */

    @Override
    public void recordDisbursement(Loan loan) {

        Transaction transaction = buildBaseTransaction(loan);

        transaction.setTransactionType(TransactionType.LOAN_DISBURSEMENT);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transaction.setChannel(TransactionChannel.BRANCH);
        transaction.setNarration("Loan disbursed successfully.");
        transaction.setSettlementStatus(SettlementStatus.SETTLED);
        transaction.setSettlementDate(LocalDate.now());

        transactionRepository.save(transaction);
    }

    /*
    =====================================================
                    RECORD REPAYMENT
    =====================================================
    */

// Replace recordRepayment inside TransactionServiceImpl.java with this version:

    @Override
    public void recordRepayment(RepaymentDTO dto, Loan loan) {

        System.out.println("---------------- [RECORD REPAYMENT START] ----------------");
        System.out.println("📥 DTO received in recordRepayment: " + dto);
        System.out.println("🏷️ DTO mpesaReceiptNumber value: " + (dto != null ? dto.getMpesaReceiptNumber() : "DTO IS NULL"));

        Transaction transaction = buildBaseTransaction(loan);

        transaction.setTransactionType(TransactionType.LOAN_REPAYMENT);
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transaction.setChannel(determineChannel(dto.getPaymentMethod()));
        transaction.setPaymentMethod(dto.getPaymentMethod());
        transaction.setPaymentReference(dto.getPaymentReference());

        // Assign receipt
        transaction.setMpesaReceiptNumber(dto.getMpesaReceiptNumber());

        transaction.setAmount(BigDecimal.valueOf(dto.getAmountPaid()));
        transaction.setNarration("Loan repayment received.");
        transaction.setSettlementStatus(SettlementStatus.SETTLED);
        transaction.setSettlementDate(LocalDate.now());

        Transaction savedTx = transactionRepository.save(transaction);

        System.out.println("💾 Saved Transaction Entity Reference: " + savedTx.getTransactionReference());
        System.out.println("🏷️ Saved Transaction mpesaReceiptNumber: " + savedTx.getMpesaReceiptNumber());
        System.out.println("---------------- [RECORD REPAYMENT END] ----------------");
    }

    /*
    =====================================================
                    PAYMENT CHANNEL HELPER
    =====================================================
    */

    private TransactionChannel determineChannel(String method) {

        if (method == null) {
            return TransactionChannel.BRANCH;
        }

        switch (method.trim().toUpperCase()) {
            case "MPESA":
                return TransactionChannel.MPESA;
            case "BANK":
            case "BRANCH":
                return TransactionChannel.BRANCH;
            case "ATM":
                return TransactionChannel.ATM;
            case "ONLINE":
                return TransactionChannel.API;
            default:
                return TransactionChannel.BRANCH;
        }
    }

    /*
    =====================================================
                    AUTHORIZE TRANSACTION
    =====================================================
    */

    @Override
    public TransactionDTO authorizeTransaction(String reference, String authorizedBy) {

        Transaction transaction = transactionRepository
                .findByTransactionReference(reference)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + reference));

        transaction.setStatus(TransactionStatus.AUTHORIZED);
        transaction.setAuthorizedBy(authorizedBy);
        transaction.setAuthorizedAt(LocalDateTime.now());

        Transaction saved = transactionRepository.save(transaction);
        return transactionMapper.toDTO(saved);
    }

    /*
    =====================================================
                    COMPLETE TRANSACTION
    =====================================================
    */

    @Override
    public TransactionDTO completeTransaction(String reference) {

        Transaction transaction = transactionRepository
                .findByTransactionReference(reference)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + reference));

        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transaction.setSettlementStatus(SettlementStatus.SETTLED);
        transaction.setSettlementDate(LocalDate.now());

        Transaction saved = transactionRepository.save(transaction);
        return transactionMapper.toDTO(saved);
    }

    /*
    =====================================================
                    REVERSE TRANSACTION
    =====================================================
    */

    @Override
    public TransactionDTO reverseTransaction(String reference, String reason) {

        Transaction transaction = transactionRepository
                .findByTransactionReference(reference)
                .orElseThrow(() -> new RuntimeException("Transaction not found: " + reference));

        transaction.setStatus(TransactionStatus.REVERSED);
        transaction.setReversedAt(LocalDateTime.now());
        transaction.setSettlementStatus(SettlementStatus.REVERSED);
        transaction.setRemarks(reason);

        Transaction saved = transactionRepository.save(transaction);
        return transactionMapper.toDTO(saved);
    }

    /*
    =====================================================
                    RISK SCORE ENGINE
    =====================================================
    */

    private Integer calculateRiskScore(Loan loan) {

        int score = 0;

        if (loan == null) return 0;

        if (loan.getAmount() != null && loan.getAmount() >= 100000) {
            score += 30;
        }

        if (loan.getRiskScore() != null) {
            score += loan.getRiskScore() / 2;
        }

        if (loan.getEmail() == null || loan.getEmail().isBlank()) {
            score += 10;
        }

        if (loan.getPhoneNumber() == null || loan.getPhoneNumber().isBlank()) {
            score += 10;
        }

        return Math.min(score, 100);
    }

    /*
    =====================================================
                    AML CHECK
    =====================================================
    */

    private boolean performAMLCheck(Loan loan) {
        return true;
    }
}