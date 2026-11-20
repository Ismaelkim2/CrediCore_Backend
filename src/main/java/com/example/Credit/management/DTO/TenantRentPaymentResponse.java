package com.example.Credit.management.DTO;

import com.example.Credit.management.Model.TenantRentPayment;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record TenantRentPaymentResponse(
        Long id,
        Long leaseId,
        String leaseCode,
        String tenantName,
        String paymentReference,
        BigDecimal amount,
        TenantRentPayment.PaymentMethod paymentMethod,
        TenantRentPayment.PaymentStatus status,
        LocalDateTime receivedAt,
        String notes,
        List<Allocation> allocations,
        LocalDateTime reversedAt,
        String reversalReason
) {
    public record Allocation(
            Long chargeId,
            String rentalPeriod,
            BigDecimal amount
    ) {}
}