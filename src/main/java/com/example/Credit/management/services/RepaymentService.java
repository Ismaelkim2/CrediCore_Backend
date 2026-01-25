package com.example.Credit.management.services;

import com.example.Credit.management.DTO.RepaymentDTO;
import com.example.Credit.management.DTO.RepaymentTransactionDTO;
import com.example.Credit.management.Model.*;
import com.example.Credit.management.Repository.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RepaymentService {

    @Autowired
    private RepaymentRepository repaymentRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private StatementService statementService;

    /**
     * Save a repayment and update related entities safely.
     * Ensures the loan and account updates happen atomically.
     */
    @Transactional
    public String saveRepayment(RepaymentDTO dto) {

        System.out.println("🔍 Incoming repayment DTO: " + dto);

        // ✅ Step 1: Validate input
        if (dto.getLoanId() == null || dto.getAmountPaid() == null || dto.getAmountPaid() <= 0) {
            throw new IllegalArgumentException("❌ Invalid repayment details provided.");
        }

        // ✅ Step 2: Fetch the loan
        Loan loan = loanRepository.findById(dto.getLoanId())
                .orElseThrow(() -> new IllegalArgumentException("❌ Loan not found."));

        // ✅ Step 3: Fetch the paying account
        Account account = accountRepository.findByAccountNumber(dto.getAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException("❌ Account not found."));

        // ✅ Step 4: Check balance sufficiency
        if (account.getBalance() < dto.getAmountPaid()) {
            throw new IllegalArgumentException("❌ Insufficient account balance to complete repayment.");
        }

        // ✅ Step 5: Fetch totals
        Double totalRepayable = loan.getTotalRepayableAmount();
        if (totalRepayable == null) {
            throw new IllegalArgumentException("❌ Loan has no total repayable amount defined.");
        }

        BigDecimal alreadyPaid = repaymentRepository.findTotalPaidByLoanId(loan.getId());
        if (alreadyPaid == null) alreadyPaid = BigDecimal.ZERO;

        BigDecimal remainingDue = BigDecimal.valueOf(totalRepayable).subtract(alreadyPaid);
        BigDecimal payment = BigDecimal.valueOf(dto.getAmountPaid());

        if (remainingDue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("✅ Loan is already fully repaid.");
        }
        if (payment.compareTo(remainingDue) > 0) {
            throw new IllegalArgumentException("❌ Payment exceeds remaining balance of KES " + remainingDue);
        }

        // ✅ Step 6: Deduct payment from account
        double newBalance = account.getBalance() - dto.getAmountPaid();
        account.setBalance(newBalance);
        accountRepository.save(account);

        // ✅ Step 7: Save repayment record
        Repayment repayment = new Repayment();
        repayment.setLoan(loan);
        repayment.setAmountPaid(dto.getAmountPaid());
        repayment.setRepaymentDate(
                dto.getRepaymentDate() != null ? dto.getRepaymentDate() : LocalDate.from(LocalDateTime.now())
        );
        repayment.setPhoneNumber(dto.getPhoneNumber());
        repayment.setLocation(dto.getLocation());
        repayment.setAccountNumber(dto.getAccountNumber());

        // 🔹 Step 7a: Determine payment method based on account type
        if (account.getType() != null) {
            switch (account.
                    getType().toUpperCase()) {
                case "MPESA":
                    repayment.setPaymentMethod("MPESA");
                    break;
                case "BANK":
                    repayment.setPaymentMethod("Bank Transfer");
                    break;
                default:
                    repayment.setPaymentMethod("Cash");
            }
        } else {
            repayment.setPaymentMethod("Bank API");
        }

        repaymentRepository.save(repayment);

        // ✅ Step 8: Record in statement
        Statement statement = new Statement();
        statement.setNationalId(String.valueOf(loan.getIdNo()));
        statement.setDescription("Loan Repayment for Loan ID: " + loan.getId());
        statement.setDebit(dto.getAmountPaid());
        statement.setCredit(0.0);
        statement.setDate(LocalDateTime.now());
        statement.setBalanceAfter(account.getBalance());
        statement.setAccountNumber(dto.getAccountNumber());
        statementService.saveAll(List.of(statement));

        // ✅ Step 9: Update loan status and due amount (CANONICAL)
        BigDecimal updatedPaid = alreadyPaid.add(payment);
        BigDecimal remaining = BigDecimal.valueOf(totalRepayable).subtract(updatedPaid);

        loan.setAmountPaid(updatedPaid.doubleValue());
        loan.setDueAmount(Math.max(remaining.doubleValue(), 0.0));

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            loan.setStatus("PAID");
            loan.setDueAmount(0.0);
        } else {
            loan.setStatus("PARTIAL");
        }

        loanRepository.save(loan);

        // ✅ Step 10: Return confirmation
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return "✅ Loan fully repaid. New account balance: KES " + account.getBalance();
        } else {
            return String.format("✅ Partial repayment of KES %.2f successful. Remaining loan balance: KES %.2f. Account balance: KES %.2f.",
                    dto.getAmountPaid(), remaining.doubleValue(), account.getBalance());
        }
    }


    // 🔹 Helper: Calculate updated due amount dynamically
    public double calculateDueAmount(Loan loan) {
        if (loan == null) return 0.0;
        BigDecimal totalPaid = repaymentRepository.findTotalPaidByLoanId(loan.getId());
        if (totalPaid == null) totalPaid = BigDecimal.ZERO;

        BigDecimal due = BigDecimal.valueOf(loan.getTotalRepayableAmount()).subtract(totalPaid);
        return due.max(BigDecimal.ZERO).doubleValue();
    }

    public Loan attachLiveDueAmount(Loan loan) {
        if (loan == null) return null;

        // ✅ Do NOT touch loans that are not yet approved/disbursed
        if ("AWAITING_AUTHORIZATION".equalsIgnoreCase(loan.getStatus()) ||
                "REJECTED".equalsIgnoreCase(loan.getStatus())) {
            loan.setAmountPaid(0.0);
            loan.setDueAmount(0.0);
            return loan;
        }

        // Calculate total repaid
        BigDecimal totalRepaid = repaymentRepository.findTotalPaidByLoanId(loan.getId());
        double repaid = (totalRepaid != null) ? totalRepaid.doubleValue() : 0.0;

        loan.setAmountPaid(repaid);

        double remaining = loan.getTotalRepayableAmount() - repaid;
        loan.setDueAmount(Math.max(remaining, 0.0));  // UI uses this

        // ✅ Update status dynamically only for disbursed/approved loans
        if (remaining <= 0) {
            loan.setStatus("PAID");
        } else if (repaid > 0) {
            loan.setStatus("PARTIAL");
        } else {
            loan.setStatus("APPROVED"); // still unpaid but disbursed
        }


        return loan;
    }


    public List<RepaymentTransactionDTO> getLoanTransactions(Long loanId) {
        return repaymentRepository.findTransactionsByLoanId(loanId);
    }




}
