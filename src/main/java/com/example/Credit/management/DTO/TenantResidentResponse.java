package com.example.Credit.management.DTO;

import com.example.Credit.management.Model.TenantResident;

import java.time.LocalDateTime;

public record TenantResidentResponse(
        Long id,
        String fullName,
        TenantResident.IdentificationType identificationType,
        String identificationNumber,
        String phoneNumber,
        String email,
        String address,
        String emergencyContactName,
        String emergencyContactPhone,
        String notes,
        TenantResident.TenantStatus status,
        String imageUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}