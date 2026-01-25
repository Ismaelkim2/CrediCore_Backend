package com.example.Credit.management.services;

import com.example.Credit.management.DTO.AccountDTO;
import com.example.Credit.management.Model.Account;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import java.util.List;
import java.util.Map;

public interface AccountService {
    Account createAccount(Account accountDTO);
    Account getByNationalId(String nationalId);
    Account getByAccountNumber(String accountNumber);
    boolean transferFunds(String fromAccountNumber, String toAccountNumber, double amount);

    Account save(Account account);
    List<Account> getAllAccounts();
    void deleteAccountByAccountNumber(String accountNumber);

    Account getAccountByNationalId(Long idNo);



    List<Map<String, Object>> getStatements(String accountNumber);
    void saveStatements(String accountNumber, List<Map<String, Object>> transactions);

    Double getBorrowedAmount(String accountNumber);
    AccountDTO toDTO(Account account);

    void addResourceHandlers(ResourceHandlerRegistry registry);

    List<Account> searchAccountsByNationalId(String nationalId);
}
