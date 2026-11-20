package com.example.Credit.management.Repository;


import com.example.Credit.management.DTO.TransactionDTO;
import com.example.Credit.management.Model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


import java.util.List;
import java.util.Optional;


@Repository
public interface TransactionRepository
        extends JpaRepository<Transaction, Long> {



    Optional<Transaction>
    findByTransactionReference(String transactionReference);



    List<Transaction>
    findByCustomerNationalId(String customerNationalId);



    List<Transaction>
    findByLoanNumber(String loanNumber);



    List<Transaction>
    findByAccountNumber(String accountNumber);



    List<Transaction>
    findByFraudStatus(String fraudStatus);



    List<Transaction>
    findByStatus(String status);

    Optional<Transaction>
    findByPaymentReference(
            String paymentReference
    );



    List<Transaction> findByAccountNumberOrderByInitiatedAtDesc(
            String accountNumber
    );

}