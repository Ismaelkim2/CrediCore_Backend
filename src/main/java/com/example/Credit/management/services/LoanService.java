package com.example.Credit.management.services;

import com.example.Credit.management.Model.Loan;
import java.util.List;

public interface LoanService {

    List<Loan> getAllLoans();

    Loan saveLoan(Loan loan);

    Loan updateLoanStatus(Long id, String status);

    void deleteLoan(Long id);

    Loan updateLoan(Long id, Loan updatedLoan);

    Loan applyOverdueCharges(Loan loan);

    Loan getLoanById(Long id);

    Loan refundLoan(Long id);

    // ✅ REQUIRED
    Loan rejectLoan(Long loanId, String reason);
}
