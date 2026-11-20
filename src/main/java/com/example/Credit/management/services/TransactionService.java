package com.example.Credit.management.services;


import com.example.Credit.management.DTO.RepaymentDTO;
import com.example.Credit.management.DTO.TransactionDTO;
import com.example.Credit.management.Model.Loan;

import java.util.List;


public interface TransactionService {

    void recordLoanApplication(Loan loan);

    void recordLoanApproval(Loan loan);

    void recordDisbursement(Loan loan);

    void recordRepayment(RepaymentDTO dto, Loan loan);

    List<TransactionDTO> getCustomerTransactions(String nationalId);

    List<TransactionDTO> getLoanTransactions(String loanNumber);

    List<TransactionDTO> getFraudReview();

    List<TransactionDTO> getAllTransactions();

    TransactionDTO getTransactionByReference(String reference);

    TransactionDTO authorizeTransaction(
            String reference,
            String authorizedBy
    );

    TransactionDTO completeTransaction(String reference);

    TransactionDTO reverseTransaction(
            String reference,
            String reason
    );
}