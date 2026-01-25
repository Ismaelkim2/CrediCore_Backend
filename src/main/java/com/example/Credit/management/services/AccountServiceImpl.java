package com.example.Credit.management.services;

import com.example.Credit.management.DTO.AccountDTO;
import com.example.Credit.management.Model.Account;
import com.example.Credit.management.Model.Loan;
import com.example.Credit.management.Repository.AccountRepository;
import com.example.Credit.management.Repository.LoanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AccountServiceImpl implements AccountService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private EmailService emailService;

    private String generateAccountNumber() {
        return "ACC-" + (1000 + new Random().nextInt(9000));
    }

    @Override
    public Account createAccount(Account dto) {
        if (accountRepository.findByNationalId(dto.getNationalId()).isPresent()) {
            throw new RuntimeException("Account already exists");
        }

        Account account = new Account();
        account.setAccountNumber(generateAccountNumber());
        account.setFullName(dto.getFullName());
        account.setNationalId(dto.getNationalId());
        account.setPhoneNumber(dto.getPhoneNumber());
        account.setEmail(dto.getEmail());
        account.setBalance(0.0);
        account.setCreatedAt(LocalDateTime.now());
        account.setImageUrl(dto.getImageUrl());

        Account saved = accountRepository.save(account);

        emailService.sendAccountCreatedEmail(
                saved.getEmail(),
                saved.getFullName(),
                saved.getAccountNumber()
        );

        return saved;
    }

    @Override
    public Account getByNationalId(String nationalId) {
        return accountRepository.findByNationalId(nationalId)
                .orElseThrow(() -> new RuntimeException("Account not found"));
    }

    @Override
    public Account getByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));
    }

    @Override
    public Account save(Account account) {
        return accountRepository.save(account);
    }

    private Map<String, List<Map<String, Object>>> statementsStore = new HashMap<>();

    @Override
    public List<Map<String, Object>> getStatements(String accountNumber) {
        return statementsStore.getOrDefault(accountNumber, new ArrayList<>());
    }

    @Override
    public void saveStatements(String accountNumber, List<Map<String, Object>> transactions) {
        statementsStore.put(accountNumber, transactions);
    }

    @Override
    public boolean transferFunds(String fromAccountNumber, String toAccountNumber, double amount) {
        if (amount <= 0) throw new IllegalArgumentException("Invalid transfer amount.");

        Optional<Account> fromOpt = accountRepository.findByAccountNumber(fromAccountNumber);
        Optional<Account> toOpt = accountRepository.findByAccountNumber(toAccountNumber);

        if (fromOpt.isEmpty() || toOpt.isEmpty()) return false;

        Account sender = fromOpt.get();
        Account receiver = toOpt.get();

        if (sender.getBalance() < amount) return false;

        // ✅ Only modify actual balances
        sender.setBalance(sender.getBalance() - amount);
        receiver.setBalance(receiver.getBalance() + amount);

        accountRepository.save(sender);
        accountRepository.save(receiver);

        // Record transactions for both
        Map<String, Object> debit = new HashMap<>();
        debit.put("date", LocalDateTime.now());
        debit.put("description", "Transfer to " + receiver.getAccountNumber());
        debit.put("debit", amount);
        debit.put("balanceAfter", sender.getBalance());

        Map<String, Object> credit = new HashMap<>();
        credit.put("date", LocalDateTime.now());
        credit.put("description", "Transfer from " + sender.getAccountNumber());
        credit.put("credit", amount);
        credit.put("balanceAfter", receiver.getBalance());

        List<Map<String, Object>> senderTxns = getStatements(fromAccountNumber);
        senderTxns.add(debit);
        saveStatements(fromAccountNumber, senderTxns);

        List<Map<String, Object>> receiverTxns = getStatements(toAccountNumber);
        receiverTxns.add(credit);
        saveStatements(toAccountNumber, receiverTxns);

        return true;
    }

    @Override
    public Double getBorrowedAmount(String accountNumber) {
        Optional<Account> optionalAccount = accountRepository.findByAccountNumber(accountNumber);
        if (optionalAccount.isEmpty()) return 0.0;

        Account account = optionalAccount.get();

        Long idNo;
        try {
            idNo = Long.valueOf(account.getNationalId());
        } catch (NumberFormatException e) {
            return 0.0;
        }

        List<Loan> allLoans = loanRepository.findByIdNo(String.valueOf(idNo));
        if (allLoans == null || allLoans.isEmpty()) return 0.0;

        // ✅ Only include loans that are not repaid/cleared
        return allLoans.stream()
                .filter(loan -> loan.getStatus() != null &&
                        !"repaid".equalsIgnoreCase(loan.getStatus()) &&
                        !"cleared".equalsIgnoreCase(loan.getStatus()))
                .mapToDouble(Loan::getAmount)
                .sum();
    }

    @Override
    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }

    @Override
    public void deleteAccountByAccountNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));
        accountRepository.delete(account);
    }

    @Override
    public Account getAccountByNationalId(Long idNo) {
        String nationalIdStr = String.valueOf(idNo);
        return accountRepository.findByNationalId(nationalIdStr)
                .orElse(null); // or throw exception if you prefer
    }



    @Override
    public AccountDTO toDTO(Account account) {
        AccountDTO dto = new AccountDTO();
        dto.setFullName(account.getFullName());
        dto.setNationalId(account.getNationalId());
        dto.setPhoneNumber(account.getPhoneNumber());
        dto.setEmail(account.getEmail());
        dto.setBalance(account.getBalance());

        // Get unpaid loans
        Double unpaidBorrowed = getBorrowedAmount(account.getAccountNumber());
        dto.setBorrowedAmount(unpaidBorrowed);

        // ✅ Calculate net balance for display ONLY (do not persist)
        double net = (account.getBalance() != null ? account.getBalance() : 0.0)
                + (unpaidBorrowed != null ? unpaidBorrowed : 0.0);
        dto.setNetBalance(net);

        dto.setAccountNumber(account.getAccountNumber());
        dto.setImageUrl(account.getImageUrl());

        // ✅ Do NOT update or save account balance here
        return dto;
    }


    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // no-op
    }

    @Override
    public List<Account> searchAccountsByNationalId(String nationalId) {
        if(nationalId == null || nationalId.isBlank()) return Collections.emptyList();
        return accountRepository.findByNationalIdContaining(nationalId);
    }
}
