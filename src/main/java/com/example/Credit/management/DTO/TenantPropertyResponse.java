
package com.example.Credit.management.DTO;

import com.example.Credit.management.Model.TenantProperty;

import java.time.LocalDateTime;

public record TenantPropertyResponse(
        Long id,
        String propertyCode,
        String name,
        TenantProperty.PropertyType propertyType,
        String county,
        String town,
        String address,
        String description,
        String imageUrl,
        TenantProperty.PropertyStatus status,
        Long ownerId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
