
package com.example.Credit.management.DTO;

import java.math.BigDecimal;

public record TenantDashboardResponse(
        long totalProperties,
        long totalUnits,
        long occupiedUnits,
        long vacantUnits,
        long activeTenants,
        long activeLeases,
        BigDecimal occupancyRate,
        BigDecimal rentChargedThisMonth,
        BigDecimal rentCollectedThisMonth,
        BigDecimal outstandingRent,
        BigDecimal overdueArrears,
        long overdueCharges,
        BigDecimal collectionRate
) {}
