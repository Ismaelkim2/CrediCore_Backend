package com.example.Credit.management.DTO;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TenantLeaseRequest(

        @NotNull(message = "Tenant ID is required.")
        Long tenantId,

        @NotNull(message = "Unit ID is required.")
        Long unitId,

        @NotNull(message = "Lease start date is required.")
        LocalDate startDate,

        @NotNull(message = "Lease end date is required.")
        LocalDate endDate,

        @NotNull(message = "Monthly rent is required.")
        @DecimalMin(value = "0.00", inclusive = false,
                message = "Monthly rent must be greater than zero.")
        BigDecimal monthlyRent,

        @NotNull(message = "Deposit amount is required.")
        @DecimalMin(value = "0.00",
                message = "Deposit cannot be negative.")
        BigDecimal depositAmount,

        @NotNull(message = "Rent due day is required.")
        @Min(value = 1, message = "Rent due day must be at least 1.")
        @Max(value = 28, message = "Rent due day must not exceed 28.")
        Integer rentDueDay,

        @Size(max = 10000, message = "Lease terms cannot exceed 10,000 characters.")
        String terms
) {}