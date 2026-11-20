package com.example.Credit.management.controller;
import com.example.Credit.management.DTO.TransactionDTO;
import com.example.Credit.management.services.TransactionService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.List;



@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@CrossOrigin("*")
public class TransactionController {



    private final TransactionService transactionService;



/*
================================================
GET ALL TRANSACTIONS
================================================
*/


    @GetMapping("/all")
    public List<TransactionDTO> getAllTransactions(){


        return transactionService
                .getAllTransactions();


    }





/*
================================================
GET SINGLE TRANSACTION
================================================
*/


    @GetMapping("/{reference}")
    public TransactionDTO getTransaction(
            @PathVariable String reference
    ){


        return transactionService
                .getTransactionByReference(reference);


    }





/*
================================================
CUSTOMER TRANSACTIONS
================================================
*/


    @GetMapping("/customer/{nationalId}")
    public List<TransactionDTO> customerTransactions(
            @PathVariable String nationalId
    ){


        return transactionService
                .getCustomerTransactions(nationalId);


    }





/*
================================================
LOAN TRANSACTIONS
================================================
*/


    @GetMapping("/loan/{loanNumber}")
    public List<TransactionDTO> loanTransactions(
            @PathVariable String loanNumber
    ){


        return transactionService
                .getLoanTransactions(loanNumber);


    }





/*
================================================
FRAUD MONITORING
================================================
*/


    @GetMapping("/fraud-review")
    public List<TransactionDTO> fraudReview(){


        return transactionService
                .getFraudReview();


    }



}