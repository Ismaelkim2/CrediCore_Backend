
package com.example.Credit.management.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RentReminderCandidateResponse(
        Long tenantId,
        String tenantName,
        String phoneNumber,
        String email,
        Long leaseId,
        Long chargeId,
        LocalDate dueDate,
        BigDecimal outstandingAmount,
        String category
) {
}
