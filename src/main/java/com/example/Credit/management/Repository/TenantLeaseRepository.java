
package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.TenantLease;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TenantLeaseRepository
        extends JpaRepository<TenantLease, Long> {

    // TENANT_AGENT: list only leases in their own portfolio.
    List<TenantLease> findByOwner_IdOrderByCreatedAtDesc(Long ownerId);

    // TENANT_AGENT: retrieve a lease only when they own it.
    Optional<TenantLease> findByIdAndOwner_Id(
            Long id,
            Long ownerId
    );

    // Check whether a unit has an overlapping lease.
    boolean existsByUnit_IdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long unitId,
            List<TenantLease.LeaseStatus> statuses,
            LocalDate requestedEndDate,
            LocalDate requestedStartDate
    );

    boolean existsByUnit_IdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqualAndIdNot(
            Long unitId,
            List<TenantLease.LeaseStatus> statuses,
            LocalDate requestedEndDate,
            LocalDate requestedStartDate,
            Long excludedLeaseId
    );

    // Verify that a tenant has a lease for a specific unit under an owner.
    boolean existsByTenant_IdAndUnit_IdAndOwner_Id(
            Long tenantId,
            Long unitId,
            Long ownerId
    );

    // Verify that a tenant has a lease in a specific property under an owner.
    boolean existsByTenant_IdAndUnit_Property_IdAndOwner_Id(
            Long tenantId,
            Long propertyId,
            Long ownerId
    );

    // ADMIN: retrieve leases by status across all portfolios.
    List<TenantLease> findByStatusIn(
            List<TenantLease.LeaseStatus> statuses
    );

    // TENANT_AGENT: count leases by status in their own portfolio.
    long countByOwner_IdAndStatus(
            Long ownerId,
            TenantLease.LeaseStatus status
    );

    // ADMIN: count leases by status across all portfolios.
    @Query("""
        select count(l)
        from TenantLease l
        where l.status = :status
        """)
    long countAllByStatus(
            @Param("status") TenantLease.LeaseStatus status
    );

    // ADMIN: count every lease across all portfolios.
    @Query("""
        select count(l)
        from TenantLease l
        """)
    long countAllLeases();
}
