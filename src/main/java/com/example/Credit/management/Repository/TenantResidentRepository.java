package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.TenantResident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantResidentRepository
        extends JpaRepository<TenantResident, Long> {

    List<TenantResident> findByOwner_IdOrderByCreatedAtDesc(Long ownerId);

    Optional<TenantResident> findByIdAndOwner_Id(
            Long id,
            Long ownerId
    );

    boolean existsByOwner_IdAndIdentificationTypeAndIdentificationNumber(
            Long ownerId,
            TenantResident.IdentificationType identificationType,
            String identificationNumber
    );

    boolean existsByOwner_IdAndIdentificationTypeAndIdentificationNumberAndIdNot(
            Long ownerId,
            TenantResident.IdentificationType identificationType,
            String identificationNumber,
            Long id
    );

    long countByOwner_IdAndStatus(
            Long ownerId,
            TenantResident.TenantStatus status
    );
}