package com.example.Credit.management.controller;

import com.example.Credit.management.DTO.RepaymentDTO;
import com.example.Credit.management.Model.Loan;
import com.example.Credit.management.Repository.LoanRepository;
import com.example.Credit.management.services.LoanService;
import com.example.Credit.management.services.RepaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/repayments")
@RequiredArgsConstructor
public class RepaymentController {

    private final RepaymentService repaymentService;
    private final LoanRepository loanRepository;
    private final LoanService loanService;

    @GetMapping("/all")
    public ResponseEntity<List<Loan>> getAllRepayments() {
        List<Loan> loans = loanRepository.findAll();

        // Attach live due amounts using the service
        loans.forEach(repaymentService::attachLiveDueAmount);

        return ResponseEntity.ok(loans);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> makeRepayment(@RequestBody RepaymentDTO dto) {
        Map<String, Object> response = new HashMap<>();
        try {
            String message = repaymentService.saveRepayment(dto);
            Loan loan = loanService.getLoanById(dto.getLoanId());

            response.put("status", "success");
            response.put("message", message);
            response.put("remainingDue", loan.getDueAmount());
            response.put("newBalance", loan.getAccount() != null ? loan.getAccount().getBalance() : null);
            response.put("accountNumber", dto.getAccountNumber() != null ? dto.getAccountNumber() : loan.getAccountNumber());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException ex) {
            response.put("status", "error");
            response.put("message", ex.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "An unexpected error occurred: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }


    @GetMapping("/by-phone/{phone}")
    public ResponseEntity<Loan> getByPhone(@PathVariable Long phone) {
        return loanRepository.findTopByPhoneNumberOrderByIdDesc(phone)
                .map(loan -> ResponseEntity.ok(repaymentService.attachLiveDueAmount(loan)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public Map<String, String> test() {
        return Map.of("message", "✅ Repayment endpoint is live.");
    }
}
