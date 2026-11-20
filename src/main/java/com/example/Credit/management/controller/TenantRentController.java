package com.example.Credit.management.controller;

import com.example.Credit.management.DTO.*;
import com.example.Credit.management.services.TenantAccountService;
import com.example.Credit.management.services.TenantRentService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/tenant/rent")
@Validated
public class TenantRentController {

    private final TenantRentService rentService;
    private final TenantAccountService accountService;

    public TenantRentController(TenantRentService rentService, TenantAccountService accountService) {
        this.rentService = rentService;
        this.accountService = accountService;
    }

    @GetMapping("/charges")
    public List<TenantRentChargeResponse> getCharges(Principal principal) {
        return rentService.getCharges(principal.getName());
    }

    @GetMapping("/leases/{leaseId}/charges")
    public List<TenantRentChargeResponse> getChargesForLease(
            @PathVariable Long leaseId,
            Principal principal) {
        return rentService.getChargesForLease(
                leaseId, principal.getName());
    }

    @GetMapping("/payments")
    public List<TenantRentPaymentResponse> getPayments(Principal principal) {
        return rentService.getPayments(principal.getName());
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public TenantRentPaymentResponse recordPayment(
            @Valid @RequestBody TenantRentPaymentRequest request,
            Principal principal) {
        return rentService.recordPayment(
                request, principal.getName());
    }

    @PatchMapping("/payments/{paymentId}/reverse")
    public TenantRentPaymentResponse reversePayment(
            @PathVariable Long paymentId,
            @Valid @RequestBody ReversalRequest request,
            Principal principal) {
        return rentService.reversePayment(
                paymentId, request.reason(), principal.getName());
    }

    public record ReversalRequest(
            @NotBlank(message = "Reversal reason is required.")
            @Size(max = 500, message = "Reversal reason cannot exceed 500 characters.")
            String reason
    ) {}

    @GetMapping("/accounts")
    public List<TenantAccountSummaryResponse> getAccounts(
            Principal principal
    ) {
        return accountService.getAccounts(principal.getName());
    }

    @GetMapping("/leases/{leaseId}/account")
    public TenantAccountSummaryResponse getAccount(
            @PathVariable Long leaseId,
            Principal principal
    ) {
        return accountService.getAccount(leaseId, principal.getName());
    }

    @GetMapping("/leases/{leaseId}/statement")
    public TenantRentStatementResponse getStatement(
            @PathVariable Long leaseId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            Principal principal
    ) {
        return accountService.getStatement(
                leaseId, from, to, principal.getName()
        );
    }
}