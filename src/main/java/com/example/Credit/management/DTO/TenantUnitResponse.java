
package com.example.Credit.management.DTO;

import com.example.Credit.management.Model.TenantUnit;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TenantUnitResponse(
        Long id,
        Long propertyId,
        String propertyCode,
        String propertyName,
        String unitNumber,
        String floor,
        String unitType,
        BigDecimal monthlyRent,
        BigDecimal depositAmount,
        TenantUnit.UnitStatus status,
        String description,
        String imageUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
