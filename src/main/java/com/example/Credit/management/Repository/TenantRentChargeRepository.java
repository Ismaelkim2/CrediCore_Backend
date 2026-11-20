
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

    List<TenantRentCharge> findByOwner_IdOrderByDueDateAsc(
            Long ownerId
    );

    List<TenantRentCharge> findByOwner_IdAndLease_IdOrderByPeriodStartAsc(
            Long ownerId,
            Long leaseId
    );

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

    @Query("""
        select coalesce(sum(c.amount - c.paidAmount), 0)
        from TenantRentCharge c
        where c.owner.id = :ownerId
          and c.paidAmount < c.amount
        """)
    BigDecimal sumOutstandingRent(
            @Param("ownerId") Long ownerId
    );

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
}
