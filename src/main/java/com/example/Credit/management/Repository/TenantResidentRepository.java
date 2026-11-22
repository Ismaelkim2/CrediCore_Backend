
package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.TenantResident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TenantResidentRepository
        extends JpaRepository<TenantResident, Long> {

    // TENANT_AGENT: list only residents in their portfolio.
    List<TenantResident> findByOwner_IdOrderByCreatedAtDesc(Long ownerId);

    // TENANT_AGENT: retrieve a resident only if they own the record.
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

    // TENANT_AGENT: count residents by status in their own portfolio.
    long countByOwner_IdAndStatus(
            Long ownerId,
            TenantResident.TenantStatus status
    );

    // ADMIN: count residents across all tenant-agent portfolios by status.
    @Query("""
        select count(t)
        from TenantResident t
        where t.status = :status
        """)
    long countAllByStatus(
            @Param("status") TenantResident.TenantStatus status
    );

    // ADMIN: count all residents across every portfolio.
    @Query("""
        select count(t)
        from TenantResident t
        """)
    long countAllResidents();
}
