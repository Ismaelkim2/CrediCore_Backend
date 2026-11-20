package com.example.Credit.management.DTO;

import com.example.Credit.management.Model.TenantLease;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TenantLeaseResponse(
        Long id,
        String leaseCode,
        Long tenantId,
        String tenantName,
        Long propertyId,
        String propertyName,
        Long unitId,
        String unitNumber,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal monthlyRent,
        BigDecimal depositAmount,
        Integer rentDueDay,
        TenantLease.LeaseStatus status,
        String terms,
        LocalDateTime terminatedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}