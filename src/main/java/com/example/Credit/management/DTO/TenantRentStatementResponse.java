package com.example.Credit.management.DTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TenantRentStatementResponse(
        Long leaseId,
        String leaseReference,
        String tenantName,
        LocalDate from,
        LocalDate to,
        BigDecimal openingBalance,
        BigDecimal totalCharges,
        BigDecimal totalPayments,
        BigDecimal totalReversals,
        BigDecimal closingBalance,
        List<Transaction> transactions
) {
    public record Transaction(
            LocalDate date,
            String type,
            String reference,
            String description,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal runningBalance
    ) {}
}