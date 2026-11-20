package com.example.Credit.management.DTO;

import com.example.Credit.management.Model.TenantRentPayment;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TenantRentPaymentRequest(

        @NotNull(message = "Lease ID is required.")
        Long leaseId,

        @NotBlank(message = "Payment reference is required.")
        @Size(max = 100, message = "Payment reference cannot exceed 100 characters.")
        String paymentReference,

        @NotNull(message = "Payment amount is required.")
        @DecimalMin(value = "0.00", inclusive = false,
                message = "Payment amount must be greater than zero.")
        BigDecimal amount,

        @NotNull(message = "Payment method is required.")
        TenantRentPayment.PaymentMethod paymentMethod,

        @Size(max = 1000)
        String notes
) {}