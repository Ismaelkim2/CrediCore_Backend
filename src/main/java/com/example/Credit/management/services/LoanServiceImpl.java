package com.example.Credit.management.services;

import com.example.Credit.management.Model.Account;
import com.example.Credit.management.Model.Loan;
import com.example.Credit.management.Model.LoanSettings;
import com.example.Credit.management.Model.Statement;
import com.example.Credit.management.Repository.AccountRepository;
import com.example.Credit.management.Repository.LoanRepository;
import com.example.Credit.management.Repository.RepaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class LoanServiceImpl implements LoanService {

    @Autowired private LoanRepository loanRepository;
    @Autowired private StatementService statementService;
    @Autowired private LoanSettingsService loanSettingsService;
    @Autowired private RepaymentRepository repaymentRepository;
    @Autowired private EmailService emailService;
    @Autowired private AccountRepository accountRepository;

    @Override
    public List<Loan> getAllLoans() { return loanRepository.findAllWithAccount(); }

    private int parseWeeks(String duration){
        String lower = duration.toLowerCase();
        int number = Integer.parseInt(duration.replaceAll("[^0-9]",""));
        return lower.contains("month") ? number*4 : number;
    }

    @Override
    public Loan saveLoan(Loan loan){
        long activeLoanCount =
                loanRepository.countByIdNoAndStatusIn(
                        loan.getIdNo(),
                        List.of("APPROVED", "PARTIAL", "AWAITING_AUTHORIZATION")
                );

        if(activeLoanCount>0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Applicant already has an active loan.");

        loan.setId(null);
        loan.setApplicationDate(LocalDate.now());

        int weeks = parseWeeks(loan.getDuration());
        LoanSettings settings = loanSettingsService.findByDurationInWeeks(weeks);

        double interest = loan.getAmount()*settings.getInterestRate()/100;
        double penalty = loan.getAmount()*settings.getPenaltyRate()/100;

        loan.setInterestRate(settings.getInterestRate());
        loan.setPenaltyRate(settings.getPenaltyRate());
        loan.setTotalRepayableAmount(loan.getAmount()+interest+penalty);

        if (settings.isAutoApproval()) {
            loan.setStatus("APPROVED");
            loan.setDisbursedDate(LocalDate.now());
            loan.setDueDate(LocalDate.now().plusWeeks(weeks));
            creditAccountBalance(loan);
        } else {
            loan.setStatus("AWAITING_AUTHORIZATION");
        }


        Loan savedLoan = loanRepository.save(loan);
        return savedLoan;

    }

    @Override
    public Loan updateLoanStatus(Long loanId, String status) {
        Loan loan = loanRepository.findByIdWithAccount(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        if ("APPROVED".equalsIgnoreCase(status)) {
            if (!"AWAITING_AUTHORIZATION".equalsIgnoreCase(loan.getStatus())) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Only loans awaiting authorization can be approved"
                );
            }

            loan.setStatus("APPROVED");
            loan.setDisbursedDate(LocalDate.now());
            loan.setDueDate(LocalDate.now().plusWeeks(parseWeeks(loan.getDuration())));
            creditAccountBalance(loan);
        } else {
            loan.setStatus(status.toUpperCase());
        }

        return loanRepository.save(loan);
    }


    @Override
    public Loan getLoanById(Long id){
        Loan loan = loanRepository.findByIdWithAccount(id).orElseThrow(()->new RuntimeException("Loan not found"));
        BigDecimal totalPaid = repaymentRepository.findTotalPaidByLoanId(id);
        double paid = totalPaid!=null?totalPaid.doubleValue():0.0;
        loan.setAmountPaid(paid);
        loan.setDueAmount(loan.getTotalRepayableAmount()-paid);
        return loan;
    }

    private void creditAccountBalance(Loan loan){
        Optional<Account> optionalAccount =
                accountRepository.findByNationalId(loan.getIdNo());

        if(optionalAccount.isEmpty()) return;
        Account account = optionalAccount.get();
        loan.setAccount(account);
        loanRepository.save(loan);

        double currentBalance = account.getBalance()!=null?account.getBalance():0.0;
        double newBalance = currentBalance+loan.getAmount();
        account.setBalance(newBalance);
        accountRepository.save(account);

        boolean exists = statementService.getByNationalId(String.valueOf(loan.getIdNo()))
                .stream().anyMatch(s -> s.getDescription()!=null && s.getDescription().contains("Loan Disbursement") && Math.abs(s.getCredit()-loan.getAmount())<0.01);
        if(!exists){
            Statement st = new Statement();
            st.setNationalId(String.valueOf(loan.getIdNo()));
            st.setDate(LocalDateTime.now());
            st.setDescription("Loan Disbursement - Loan ID "+loan.getId());
            st.setCredit(loan.getAmount());
            st.setDebit(0.0);
            st.setBalanceAfter(newBalance);
            statementService.save(st);
        }
    }

    @Override public void deleteLoan(Long id){ loanRepository.deleteById(id); }
    @Override public Loan updateLoan(Long id,Loan updatedLoan){ return null; }

    @Override
    public Loan refundLoan(Long id){
        Loan loan = loanRepository.findById(id).orElseThrow(()->new RuntimeException("Loan not found"));
        if(!"rejected".equalsIgnoreCase(loan.getStatus()))
            throw new IllegalStateException("Refund allowed only for rejected loans.");
        loan.setAmount(0.0);
        loan.setTotalRepayableAmount(0.0);
        loan.setDueDate(null);
        return loanRepository.save(loan);
    }

    @Override
    public Loan applyOverdueCharges(Loan loan){
        int weeks = parseWeeks(loan.getDuration());
        LoanSettings settings = loanSettingsService.findByDurationInWeeks(weeks);
        loan.setInterestRate(settings.getInterestRate());
        loan.setPenaltyRate(settings.getPenaltyRate());
        loan.setTotalRepayableAmount(loan.getAmount() + loan.getAmount()*settings.getInterestRate()/100 + loan.getAmount()*settings.getPenaltyRate()/100);
        loan.setStatus("Overdue");
        return loanRepository.save(loan);
    }

    @Override
    public Loan rejectLoan(Long loanId, String reason) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new RuntimeException("Loan not found"));

        loan.setStatus("REJECTED");
        loan.setRejectionReason(reason);
        loan.setRejectedAt(LocalDateTime.now());

        return loanRepository.save(loan);
    }



}
