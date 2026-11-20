package com.example.Credit.management.services;

import com.example.Credit.management.DTO.RepaymentDTO;
import com.example.Credit.management.DTO.RepaymentTransactionDTO;
import com.example.Credit.management.Model.*;
import com.example.Credit.management.Repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RepaymentService {

    @Value("${mpesa.mode:real}")
    private String mpesaMode;

    @Autowired
    private MpesaStkService mpesaStkService;

    @Autowired
    private RepaymentRepository repaymentRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SmsService smsService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private TransactionService transactionService;

    // =========================
    // THREAD SAFE MAPS
    // =========================
    private final Map<String, String> repaymentStatus = new ConcurrentHashMap<>();
    private final Map<String, RepaymentDTO> pendingRepayments = new ConcurrentHashMap<>();

    // =========================
    // MAIN ENTRY
    // =========================
    public Map<String, Object> saveRepayment(RepaymentDTO dto) {

        System.out.println("🔍 Incoming repayment DTO: " + dto);

        return "mock".equalsIgnoreCase(mpesaMode)
                ? mockRepayment(dto)
                : realRepayment(dto);
    }

    // =========================
    // MOCK FLOW
    // =========================
    private Map<String, Object> mockRepayment(RepaymentDTO dto) {

        String ref = "MOCK-" + UUID.randomUUID();
        repaymentStatus.put(ref, "INITIATED");

        Loan loan = loanRepository.findById(dto.getLoanId())
                .orElseThrow(() -> new IllegalArgumentException("Loan not found for ID: " + dto.getLoanId()));

        String status = loan.getStatus() == null ? "" : loan.getStatus().toUpperCase();

        if (status.equals("REJECTED")
                || status.equals("AUTO_REJECTED")
                || status.equals("AWAITING_AUTHORIZATION")) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Repayment is not allowed for " + status + " loans."
            );
        }

        Account account = accountRepository.findByAccountNumber(dto.getAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException("Account not found for Number: " + dto.getAccountNumber()));

        double amount = dto.getAmountPaid();

        new Thread(() -> {
            try {
                Thread.sleep(1500);
                repaymentStatus.put(ref, "STK_PUSH_SENT");

                Thread.sleep(2000);
                repaymentStatus.put(ref, "PROCESSING_PAYMENT");

                // Re-fetch entities fresh inside background thread session
                Loan threadLoan = loanRepository.findById(loan.getId()).orElse(loan);
                Account threadAccount = accountRepository.findByAccountNumber(account.getAccountNumber()).orElse(account);

                // 1. Update Account Balance
                threadAccount.setBalance(
                        (threadAccount.getBalance() == null ? 0.0 : threadAccount.getBalance()) - amount
                );
                accountRepository.save(threadAccount);

                // 2. Update Loan Status & Balances
                double currentPaid = threadLoan.getAmountPaid() == null ? 0.0 : threadLoan.getAmountPaid();
                double currentDue = threadLoan.getDueAmount() == null ? 0.0 : threadLoan.getDueAmount();

                threadLoan.setAmountPaid(currentPaid + amount);
                double newDue = Math.max(currentDue - amount, 0);
                threadLoan.setDueAmount(newDue);
                threadLoan.setStatus(newDue <= 0 ? "PAID" : "PARTIAL");

                if (newDue <= 0) {
                    notificationService.createNotification(
                            threadAccount,
                            "Congratulations! Loan #" + threadLoan.getId() + " has been fully settled.",
                            "LOAN_CLEARED"
                    );
                }
                loanRepository.save(threadLoan);

                // 3. Generate M-Pesa Receipt Number BEFORE logging transaction
                String mpesaReceipt = "MPE" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8).toUpperCase();
                dto.setMpesaReceiptNumber(mpesaReceipt);
                dto.setPaymentMethod("MPESA");
                dto.setPaymentReference(mpesaReceipt);

                System.out.println("✅ Generated Mock Receipt: " + mpesaReceipt + " for DTO: " + dto);

                // 4. Save Repayment Entity
                Repayment r = new Repayment();
                r.setLoan(threadLoan);
                r.setAmountPaid(amount);
                r.setRepaymentDate(LocalDate.now());
                r.setAccountNumber(dto.getAccountNumber());
                r.setPhoneNumber(dto.getPhoneNumber());
                r.setMpesaReceiptNumber(mpesaReceipt);
                r.setPaymentMethod("MPESA");
                repaymentRepository.save(r);

                // 5. Record Transaction Ledger Entry (DTO has receipt attached now)
                transactionService.recordRepayment(dto, threadLoan);

                // 6. Send Notifications
                sendNotifications(threadAccount, threadLoan, amount);

                repaymentStatus.put(ref, "SUCCESS");

            } catch (Exception e) {
                System.err.println("❌ Error processing mock repayment thread:");
                e.printStackTrace();
                repaymentStatus.put(ref, "FAILED");
            }
        }).start();

        return Map.of(
                "status", "INITIATED",
                "reference", ref,
                "message", "Mock STK simulation started"
        );
    }

    // =========================
    // REAL MPESA FLOW
    // =========================
    private Map<String, Object> realRepayment(RepaymentDTO dto) {

        Loan loan = loanRepository.findById(dto.getLoanId())
                .orElseThrow(() -> new IllegalArgumentException("Loan not found"));

        String status = loan.getStatus() == null ? "" : loan.getStatus().toUpperCase();

        if (status.equals("REJECTED")
                || status.equals("AUTO_REJECTED")
                || status.equals("AWAITING_AUTHORIZATION")) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Repayment is not allowed for " + status + " loans."
            );
        }

        accountRepository.findByAccountNumber(dto.getAccountNumber())
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        if (dto.getPhoneNumber() == null || dto.getPhoneNumber().isBlank()) {
            throw new IllegalArgumentException("Phone number is required");
        }

        Map<String, Object> stkResponse =
                mpesaStkService.initiateStkPush(
                        formatPhone(dto.getPhoneNumber()),
                        String.valueOf(dto.getAmountPaid())
                );

        Object rawId = stkResponse.get("CheckoutRequestID");

        if (rawId == null) {
            throw new IllegalStateException(
                    String.valueOf(stkResponse.getOrDefault("message", "STK failed"))
            );
        }

        String checkoutRequestId = rawId.toString();

        pendingRepayments.put(checkoutRequestId, dto);
        repaymentStatus.put(checkoutRequestId, "STK_PUSH_SENT");

        return Map.of(
                "status", "PENDING",
                "checkoutRequestId", checkoutRequestId,
                "message", "Real STK initiated"
        );
    }

    // =========================
    // NOTIFICATION HELPER
    // =========================
    public void sendNotifications(Account account, Loan loan, double amount) {

        String message = "Repayment of KES " + amount + " received successfully.";

        notificationService.createNotification(
                account,
                message,
                "REPAYMENT"
        );

        if (loan.getPhoneNumber() != null && !loan.getPhoneNumber().isBlank()) {
            smsService.sendSms(
                    loan.getPhoneNumber(),
                    message
            );
        }

        if (loan.getEmail() != null && !loan.getEmail().isBlank()) {
            emailService.sendEmail(
                    loan.getEmail(),
                    "Repayment Received",
                    message
            );
        }
    }

    // =========================
    // STATUS & CACHE MANAGEMENT
    // =========================
    public String getStatus(String ref) {
        return repaymentStatus.getOrDefault(ref, "UNKNOWN");
    }

    public void updateStatus(String ref, String status) {
        repaymentStatus.put(ref, status);
    }

    public RepaymentDTO getPendingRepayment(String ref) {
        return pendingRepayments.get(ref);
    }

    // =========================
    // LOAN CALCULATIONS
    // =========================
    public Loan attachLiveDueAmount(Loan loan) {

        if (loan == null) {
            return null;
        }

        BigDecimal totalPaid = repaymentRepository.findTotalPaidByLoanId(loan.getId());
        double paid = totalPaid == null ? 0.0 : totalPaid.doubleValue();

        loan.setAmountPaid(paid);

        double remaining = Math.max(
                loan.getTotalRepayableAmount() - paid,
                0
        );

        loan.setDueAmount(remaining);

        String currentStatus = loan.getStatus() == null ? "" : loan.getStatus().toUpperCase();

        if ("APPROVED".equals(currentStatus)
                || "PARTIAL".equals(currentStatus)
                || "PAID".equals(currentStatus)) {

            if (remaining <= 0) {
                loan.setStatus("PAID");
            } else if (paid > 0) {
                loan.setStatus("PARTIAL");
            } else {
                loan.setStatus("APPROVED");
            }
        }

        return loan;
    }

    // =========================
    // MAPPINGS & QUERIES
    // =========================
    public List<RepaymentTransactionDTO> getLoanTransactions(Long loanId) {
        return repaymentRepository.findTransactionsByLoanId(loanId);
    }

    public List<RepaymentDTO> getAllRepaymentsAsDTO() {
        return repaymentRepository.findAll().stream().map(r -> {
            RepaymentDTO dto = new RepaymentDTO();
            dto.setId(r.getId());
            dto.setAmountPaid(r.getAmountPaid());
            dto.setRepaymentDate(r.getRepaymentDate());
            dto.setPhoneNumber(r.getPhoneNumber());
            dto.setAccountNumber(r.getAccountNumber());
            dto.setPaymentMethod(r.getPaymentMethod());
            dto.setMpesaReceiptNumber(r.getMpesaReceiptNumber());
            return dto;
        }).toList();
    }

    // =========================
    // PHONE NORMALIZATION
    // =========================
    private String formatPhone(String phone) {

        if (phone == null) return null;

        phone = phone.trim();

        if (phone.startsWith("+")) phone = phone.substring(1);
        if (phone.startsWith("0")) phone = "254" + phone.substring(1);
        if (!phone.startsWith("254")) phone = "254" + phone;

        return phone;
    }
}