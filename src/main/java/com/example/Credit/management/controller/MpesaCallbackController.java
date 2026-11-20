package com.example.Credit.management.controller;

import com.example.Credit.management.DTO.RepaymentDTO;
import com.example.Credit.management.Model.Account;
import com.example.Credit.management.Model.Loan;
import com.example.Credit.management.Model.Repayment;
import com.example.Credit.management.Repository.AccountRepository;
import com.example.Credit.management.Repository.LoanRepository;
import com.example.Credit.management.Repository.RepaymentRepository;
import com.example.Credit.management.services.MpesaStkService;
import com.example.Credit.management.services.RepaymentService;
import com.example.Credit.management.services.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mpesa")
@RequiredArgsConstructor
public class MpesaCallbackController {

    private final RepaymentService repaymentService;
    private final LoanRepository loanRepository;
    private final AccountRepository accountRepository;
    private final RepaymentRepository repaymentRepository;
    private final MpesaStkService mpesaStkService;
    private final TransactionService transactionService;

    @PostMapping("/callback")
    public Map<String, Object> callback(@RequestBody Map<String, Object> payload) {
        System.out.println("==================== [MPESA CALLBACK START] ====================");
        System.out.println("📥 Raw Callback Payload: " + payload);

        try {

            // ================= SAFE NAVIGATION =================
            Map body = (Map) payload.get("Body");
            if (body == null) {
                System.err.println("❌ ERROR: Body is NULL in callback payload");
                return Map.of("ResultCode", 1, "ResultDesc", "Invalid payload");
            }

            Map stkCallback = (Map) body.get("stkCallback");
            if (stkCallback == null) {
                System.err.println("❌ ERROR: stkCallback is NULL inside Body");
                return Map.of("ResultCode", 1, "ResultDesc", "Missing stkCallback");
            }

            // ================= EXTRACT DATA =================
            Integer resultCode = (Integer) stkCallback.get("ResultCode");
            String resultDesc = String.valueOf(stkCallback.get("ResultDesc"));
            String checkoutRequestId = String.valueOf(stkCallback.get("CheckoutRequestID"));

            System.out.println("🔍 CheckoutRequestID: " + checkoutRequestId);
            System.out.println("🔍 ResultCode: " + resultCode + " (" + resultDesc + ")");

            // ================= STATUS ROUTING =================
            if (resultCode != null && resultCode == 0) {

                // 1. Extract M-Pesa Receipt Number from callback metadata
                String mpesaReceiptNumber = extractMpesaReceiptNumber(stkCallback);
                System.out.println("🏷️ Extracted M-Pesa Receipt Number: " + mpesaReceiptNumber);

                mpesaStkService.updateTransactionStatus(checkoutRequestId, 0, "Payment successful");

                RepaymentDTO dto = repaymentService.getPendingRepayment(checkoutRequestId);

                if (dto == null) {
                    System.err.println("❌ CRITICAL ERROR: Pending RepaymentDTO NOT FOUND in cache for CheckoutRequestID: " + checkoutRequestId);
                } else {
                    System.out.println("✅ Found Cached Pending RepaymentDTO: " + dto);

                    Loan loan = loanRepository.findById(dto.getLoanId())
                            .orElseThrow(() -> new RuntimeException("Loan not found for ID: " + dto.getLoanId()));

                    Account account = accountRepository.findByAccountNumber(dto.getAccountNumber())
                            .orElseThrow(() -> new RuntimeException("Account not found for Account Number: " + dto.getAccountNumber()));

                    double amount = dto.getAmountPaid();

                    // ================= UPDATE ACCOUNT =================
                    account.setBalance(
                            (account.getBalance() == null ? 0.0 : account.getBalance()) - amount
                    );
                    accountRepository.save(account);

                    // ================= SAVE REPAYMENT =================
                    Repayment repayment = new Repayment();
                    repayment.setLoan(loan);
                    repayment.setAmountPaid(amount);
                    repayment.setRepaymentDate(LocalDate.now());
                    repayment.setAccountNumber(dto.getAccountNumber());
                    repayment.setPhoneNumber(dto.getPhoneNumber());
                    repayment.setPaymentMethod("MPESA");
                    repayment.setMpesaReceiptNumber(mpesaReceiptNumber);

                    Repayment savedRepayment = repaymentRepository.save(repayment);
                    System.out.println("💾 Saved Repayment Entity ID: " + savedRepayment.getId() + " | Receipt: " + savedRepayment.getMpesaReceiptNumber());

                    // ================= UPDATE LOAN =================
                    double newPaid = (loan.getAmountPaid() == null ? 0.0 : loan.getAmountPaid()) + amount;
                    loan.setAmountPaid(newPaid);

                    double newDue = Math.max(
                            (loan.getDueAmount() == null ? 0.0 : loan.getDueAmount()) - amount,
                            0
                    );

                    loan.setStatus(newDue <= 0 ? "PAID" : "PARTIAL");
                    loan.setDueAmount(newDue);
                    loanRepository.save(loan);

                    // ================= UPDATE DTO & RECORD LEDGER TRANSACTION =================
                    dto.setMpesaReceiptNumber(mpesaReceiptNumber);
                    dto.setPaymentMethod("MPESA");
                    dto.setPaymentReference(mpesaReceiptNumber);

                    System.out.println("🚀 Passing DTO to TransactionService.recordRepayment with Receipt: " + dto.getMpesaReceiptNumber());
                    transactionService.recordRepayment(dto, loan);

                    // ================= SEND NOTIFICATIONS =================
                    repaymentService.sendNotifications(account, loan, amount);

                    repaymentService.updateStatus(checkoutRequestId, "SUCCESS");
                }

            } else {
                System.out.println("⚠️ Payment Failed or Cancelled on M-Pesa end.");
                repaymentService.updateStatus(checkoutRequestId, "CANCELLED");
            }

            System.out.println("==================== [MPESA CALLBACK END] ====================");
            return Map.of("ResultCode", 0, "ResultDesc", "Accepted");

        } catch (Exception e) {
            System.err.println("💥 EXCEPTION IN CALLBACK:");
            e.printStackTrace();

            return Map.of("ResultCode", 1, "ResultDesc", "Error processing callback");
        }
    }

    @SuppressWarnings("unchecked")
    private String extractMpesaReceiptNumber(Map<String, Object> stkCallback) {
        try {
            Map<String, Object> metadata = (Map<String, Object>) stkCallback.get("CallbackMetadata");
            if (metadata == null) {
                System.err.println("⚠️ CallbackMetadata object is NULL");
                return null;
            }

            List<Map<String, Object>> items = (List<Map<String, Object>>) metadata.get("Item");
            if (items == null) {
                System.err.println("⚠️ Items list inside CallbackMetadata is NULL");
                return null;
            }

            for (Map<String, Object> item : items) {
                System.out.println("   --> Metadata Item: " + item.get("Name") + " = " + item.get("Value"));
                if ("MpesaReceiptNumber".equals(item.get("Name"))) {
                    return String.valueOf(item.get("Value"));
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Failed parsing CallbackMetadata:");
            e.printStackTrace();
        }
        return null;
    }
}