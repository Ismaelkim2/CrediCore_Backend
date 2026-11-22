
package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.TenantRentCharge;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TenantRentChargeRepository
        extends JpaRepository<TenantRentCharge, Long> {

    boolean existsByLease_IdAndPeriodStart(
            Long leaseId,
            LocalDate periodStart
    );

    // TENANT_AGENT: list only their own rent charges.
    List<TenantRentCharge> findByOwner_IdOrderByDueDateAsc(
            Long ownerId
    );

    List<TenantRentCharge> findByOwner_IdAndLease_IdOrderByPeriodStartAsc(
            Long ownerId,
            Long leaseId
    );

    Optional<TenantRentCharge> findByIdAndOwner_IdAndLease_Id(
            Long chargeId,
            Long ownerId,
            Long leaseId
    );

    // Lock outstanding charges belonging to a specific owner and lease.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select c
        from TenantRentCharge c
        where c.owner.id = :ownerId
          and c.lease.id = :leaseId
          and c.status in :statuses
          and c.paidAmount < c.amount
        order by c.periodStart asc, c.id asc
        """)
    List<TenantRentCharge> findOutstandingChargesForAllocation(
            @Param("ownerId") Long ownerId,
            @Param("leaseId") Long leaseId,
            @Param("statuses")
            List<TenantRentCharge.ChargeStatus> statuses
    );

    // Lock a charge only when it belongs to the specified owner.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select c
        from TenantRentCharge c
        where c.id = :chargeId
          and c.owner.id = :ownerId
        """)
    Optional<TenantRentCharge> findChargeForUpdate(
            @Param("chargeId") Long chargeId,
            @Param("ownerId") Long ownerId
    );

    List<TenantRentCharge> findByOwner_IdAndStatusInOrderByDueDateAsc(
            Long ownerId,
            List<TenantRentCharge.ChargeStatus> statuses
    );

    // TENANT_AGENT: rent charged during a selected period.
    @Query("""
        select coalesce(sum(c.amount), 0)
        from TenantRentCharge c
        where c.owner.id = :ownerId
          and c.periodStart >= :monthStart
          and c.periodStart < :nextMonth
        """)
    BigDecimal sumRentChargedForPeriod(
            @Param("ownerId") Long ownerId,
            @Param("monthStart") LocalDate monthStart,
            @Param("nextMonth") LocalDate nextMonth
    );

    // ADMIN: rent charged across all portfolios during a selected period.
    @Query("""
        select coalesce(sum(c.amount), 0)
        from TenantRentCharge c
        where c.periodStart >= :monthStart
          and c.periodStart < :nextMonth
        """)
    BigDecimal sumAllRentChargedForPeriod(
            @Param("monthStart") LocalDate monthStart,
            @Param("nextMonth") LocalDate nextMonth
    );

    // TENANT_AGENT: total outstanding rent in their portfolio.
    @Query("""
        select coalesce(sum(c.amount - c.paidAmount), 0)
        from TenantRentCharge c
        where c.owner.id = :ownerId
          and c.paidAmount < c.amount
        """)
    BigDecimal sumOutstandingRent(
            @Param("ownerId") Long ownerId
    );

    // ADMIN: total outstanding rent across all portfolios.
    @Query("""
        select coalesce(sum(c.amount - c.paidAmount), 0)
        from TenantRentCharge c
        where c.paidAmount < c.amount
        """)
    BigDecimal sumAllOutstandingRent();

    // TENANT_AGENT: overdue arrears in their portfolio.
    @Query("""
        select coalesce(sum(c.amount - c.paidAmount), 0)
        from TenantRentCharge c
        where c.owner.id = :ownerId
          and c.dueDate < :today
          and c.paidAmount < c.amount
        """)
    BigDecimal sumOverdueArrears(
            @Param("ownerId") Long ownerId,
            @Param("today") LocalDate today
    );

    // ADMIN: overdue arrears across all portfolios.
    @Query("""
        select coalesce(sum(c.amount - c.paidAmount), 0)
        from TenantRentCharge c
        where c.dueDate < :today
          and c.paidAmount < c.amount
        """)
    BigDecimal sumAllOverdueArrears(
            @Param("today") LocalDate today
    );

    // TENANT_AGENT: count overdue charges in their portfolio.
    @Query("""
        select count(c)
        from TenantRentCharge c
        where c.owner.id = :ownerId
          and c.dueDate < :today
          and c.paidAmount < c.amount
        """)
    long countOverdueCharges(
            @Param("ownerId") Long ownerId,
            @Param("today") LocalDate today
    );

    // ADMIN: count overdue charges across all portfolios.
    @Query("""
        select count(c)
        from TenantRentCharge c
        where c.dueDate < :today
          and c.paidAmount < c.amount
        """)
    long countAllOverdueCharges(
            @Param("today") LocalDate today
    );
}
