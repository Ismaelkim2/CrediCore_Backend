package com.example.Credit.management.DTO;

import com.example.Credit.management.Model.TenantRentCharge;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TenantRentChargeResponse(
        Long id,
        Long leaseId,
        String leaseCode,
        Long tenantId,
        String tenantName,
        Long propertyId,
        String propertyName,
        Long unitId,
        String unitNumber,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate dueDate,
        BigDecimal amount,
        BigDecimal paidAmount,
        BigDecimal balance,
        TenantRentCharge.ChargeStatus status
) {}