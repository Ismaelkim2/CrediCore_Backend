package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.TenantLease;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TenantLeaseRepository
        extends JpaRepository<TenantLease, Long> {

    List<TenantLease> findByOwner_IdOrderByCreatedAtDesc(Long ownerId);

    Optional<TenantLease> findByIdAndOwner_Id(Long id, Long ownerId);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from TenantUnit u where u.id = :unitId")
    Optional<com.example.Credit.management.Model.TenantUnit>
    findUnitForLeaseUpdate(@Param("unitId") Long unitId);

    List<TenantLease> findByStatusIn(
            List<TenantLease.LeaseStatus> statuses
    );

    long countByOwner_IdAndStatus(
            Long ownerId,
            TenantLease.LeaseStatus status
    );
}