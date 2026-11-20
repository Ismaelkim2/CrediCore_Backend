package com.example.Credit.management.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TenantAccountSummaryResponse(
        Long leaseId,
        String leaseReference,
        String tenantName,
        String tenantImageUrl,
        BigDecimal totalRentCharged,
        BigDecimal totalValidPayments,
        BigDecimal totalOutstanding,
        BigDecimal overdueArrears,
        BigDecimal availableCredit,
        BigDecimal netAccountPosition,
        LocalDate nextRentDueDate,
        String propertyName,
        String unitName
) {}