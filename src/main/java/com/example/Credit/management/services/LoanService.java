package com.example.Credit.management.services;

import com.example.Credit.management.Model.Account;
import com.example.Credit.management.Model.Loan;
import com.example.Credit.management.Model.Staff;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface LoanService {

    // =========================================================
    // LOAN RETRIEVAL
    // =========================================================

    List<Loan> getAllLoans();

    Loan getLoanById(Long id);

    Loan getLoanByApplicationNumber(String applicationNumber);

    List<Loan> getLoansByAccount(Long accountId);

    Account getAccountById(Long id);

    // =========================================================
    // LOAN APPLICATION
    // =========================================================

    Loan saveLoan(Loan loan);

    Loan updateLoanWithImage(
            Long id,
            String applicantName,
            String idNo,
            String phoneNumber,
            String location,
            Double amount,
            Double monthlyIncome,
            Double monthlyExpenses,
            String applicationDate,
            String email,
            String dueDate,
            MultipartFile file
    );

    // =========================================================
    // LOAN STATUS / DECISION
    // =========================================================

    Loan updateLoanStatus(
            Long id,
            String status
    );

    Loan approveLoan(
            Long loanId
    );

    Loan rejectLoan(
            Long loanId,
            String reason
    );

    // =========================================================
    // LOAN MANAGEMENT
    // =========================================================

    void deleteLoan(
            Long id
    );

    Loan updateLoan(
            Long id,
            Loan updatedLoan
    );

    Loan refundLoan(
            Long id
    );

    Loan applyOverdueCharges(
            Loan loan
    );

    // =========================================================
    // STAFF
    // =========================================================

    List<Staff> getAllStaff();

    Loan assignStaff(
            Long loanId,
            Long staffId
    );
}