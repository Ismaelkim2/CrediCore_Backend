
package com.example.Credit.management.DTO;

import com.example.Credit.management.Model.TenantReminder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record TenantReminderResponse(
        Long id,
        Long tenantId,
        String tenantName,
        String tenantPhone,
        Long propertyId,
        Long unitId,
        String title,
        String message,
        TenantReminder.ReminderType type,
        TenantReminder.ReminderStatus status,
        TenantReminder.ReminderChannel channel,
        LocalDate dueDate,
        BigDecimal amount,
        LocalDateTime scheduledAt,
        int attempts,
        LocalDateTime lastAttemptAt,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static TenantReminderResponse from(
            TenantReminder reminder,
            TenantResidentResponse tenant
    ) {
        return new TenantReminderResponse(
                reminder.getId(),
                reminder.getTenantId(),
                tenant != null ? tenant.fullName() : null,
                tenant != null ? tenant.phoneNumber() : null,
                reminder.getPropertyId(),
                reminder.getUnitId(),
                reminder.getTitle(),
                reminder.getMessage(),
                reminder.getType(),
                reminder.getStatus(),
                reminder.getChannel(),
                reminder.getDueDate(),
                reminder.getAmount(),
                reminder.getScheduledAt(),
                reminder.getAttemptCount(),
                reminder.getLastAttemptAt(),
                reminder.getLastError(),
                reminder.getCreatedAt(),
                reminder.getUpdatedAt()
        );
    }
}
