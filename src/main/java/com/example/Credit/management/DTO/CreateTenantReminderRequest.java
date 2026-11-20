package com.example.Credit.management.DTO;

import com.example.Credit.management.Model.TenantReminder;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record CreateTenantReminderRequest(

        @Positive
        Long tenantId,

        @Positive
        Long propertyId,

        @Positive
        Long unitId,

        @NotBlank
        @Size(max = 160)
        String title,

        @NotBlank
        @Size(max = 2000)
        String message,

        @NotNull
        TenantReminder.ReminderType type,

        TenantReminder.ReminderChannel channel,

        @NotNull
        LocalDate dueDate,

        @DecimalMin("0.00")
        @Digits(integer = 12, fraction = 2)
        BigDecimal amount,

        @FutureOrPresent
        LocalDateTime scheduledAt,

        @Size(max = 200)
        String idempotencyKey
) {}