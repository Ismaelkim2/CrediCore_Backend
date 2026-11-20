
package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.TenantRentPayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TenantRentPaymentRepository
        extends JpaRepository<TenantRentPayment, Long> {

    boolean existsByOwner_IdAndPaymentReferenceIgnoreCase(
            Long ownerId,
            String paymentReference
    );

    List<TenantRentPayment> findByOwner_IdOrderByReceivedAtDesc(
            Long ownerId
    );

    Optional<TenantRentPayment> findByIdAndOwner_Id(
            Long id,
            Long ownerId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select p
        from TenantRentPayment p
        where p.id = :paymentId
          and p.owner.id = :ownerId
        """)
    Optional<TenantRentPayment> findForUpdate(
            @Param("paymentId") Long paymentId,
            @Param("ownerId") Long ownerId
    );

    @Query("""
        select coalesce(sum(p.amount), 0)
        from TenantRentPayment p
        where p.owner.id = :ownerId
          and p.status = :status
          and p.receivedAt >= :start
          and p.receivedAt < :end
        """)
    BigDecimal sumPaymentsReceived(
            @Param("ownerId") Long ownerId,
            @Param("status")
            TenantRentPayment.PaymentStatus status,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

    List<TenantRentPayment>
    findByLease_IdAndStatusOrderByReceivedAtAsc(
            Long leaseId,
            TenantRentPayment.PaymentStatus status
    );
}
