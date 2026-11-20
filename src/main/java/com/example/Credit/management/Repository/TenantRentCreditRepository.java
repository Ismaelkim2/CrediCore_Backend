
package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.TenantRentCredit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TenantRentCreditRepository
        extends JpaRepository<TenantRentCredit, Long> {

    Optional<TenantRentCredit> findByLease_IdAndOwner_Id(
            Long leaseId,
            Long ownerId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select c
        from TenantRentCredit c
        where c.lease.id = :leaseId
          and c.owner.id = :ownerId
        """)
    Optional<TenantRentCredit> findForUpdate(
            @Param("leaseId") Long leaseId,
            @Param("ownerId") Long ownerId
    );
}
