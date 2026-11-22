
package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.TenantPaymentAllocation;
import com.example.Credit.management.Model.TenantRentPayment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface TenantPaymentAllocationRepository
        extends JpaRepository<TenantPaymentAllocation, Long> {

    List<TenantPaymentAllocation> findByPayment_IdOrderByIdAsc(
            Long paymentId
    );

    List<TenantPaymentAllocation> findByPayment_IdInOrderByIdAsc(
            Collection<Long> paymentIds
    );

    // TENANT_AGENT: allocations belonging to their own lease.
    List<TenantPaymentAllocation>
    findByPayment_Owner_IdAndPayment_Lease_IdOrderByIdAsc(
            Long ownerId,
            Long leaseId
    );

    // TENANT_AGENT: allocations belonging to their own payment.
    List<TenantPaymentAllocation>
    findByPayment_IdAndPayment_Owner_IdOrderByIdAsc(
            Long paymentId,
            Long ownerId
    );

    /*
     * TENANT_AGENT:
     * Total posted allocations applied to rent charges
     * whose period starts within the selected period.
     */
    @Query("""
        select coalesce(sum(a.amount), 0)
        from TenantPaymentAllocation a
        join a.payment p
        join a.charge c
        where p.owner.id = :ownerId
          and c.owner.id = :ownerId
          and p.status = :paymentStatus
          and c.periodStart >= :periodStart
          and c.periodStart < :periodEnd
        """)
    BigDecimal sumAllocatedToChargesForPeriod(
            @Param("ownerId") Long ownerId,
            @Param("paymentStatus")
            TenantRentPayment.PaymentStatus paymentStatus,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd
    );

    /*
     * ADMIN:
     * Total posted allocations across all agent portfolios
     * applied to rent charges within the selected period.
     */
    @Query("""
        select coalesce(sum(a.amount), 0)
        from TenantPaymentAllocation a
        join a.payment p
        join a.charge c
        where p.status = :paymentStatus
          and c.periodStart >= :periodStart
          and c.periodStart < :periodEnd
        """)
    BigDecimal sumAllAllocatedToChargesForPeriod(
            @Param("paymentStatus")
            TenantRentPayment.PaymentStatus paymentStatus,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd
    );

    /*
     * TENANT_AGENT:
     * Posted allocations applied to future rent charges.
     */
    @Query("""
        select coalesce(sum(a.amount), 0)
        from TenantPaymentAllocation a
        join a.payment p
        join a.charge c
        where p.owner.id = :ownerId
          and c.owner.id = :ownerId
          and p.status = :paymentStatus
          and c.periodStart >= :fromDate
        """)
    BigDecimal sumAllocatedToFutureCharges(
            @Param("ownerId") Long ownerId,
            @Param("paymentStatus")
            TenantRentPayment.PaymentStatus paymentStatus,
            @Param("fromDate") LocalDate fromDate
    );
}
