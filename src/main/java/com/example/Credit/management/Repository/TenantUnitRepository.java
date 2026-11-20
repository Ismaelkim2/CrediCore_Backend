
package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.TenantUnit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TenantUnitRepository
        extends JpaRepository<TenantUnit, Long> {

    List<TenantUnit> findByProperty_IdOrderByUnitNumberAsc(
            Long propertyId);

    Optional<TenantUnit> findByIdAndProperty_Id(
            Long id,
            Long propertyId);

    boolean existsByProperty_IdAndUnitNumberIgnoreCase(
            Long propertyId,
            String unitNumber);

    boolean existsByProperty_IdAndUnitNumberIgnoreCaseAndIdNot(
            Long propertyId,
            String unitNumber,
            Long id);

    long countByProperty_Id(Long propertyId);

    long countByProperty_IdAndStatus(
            Long propertyId,
            TenantUnit.UnitStatus status);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from TenantUnit u where u.id = :unitId")
    Optional<TenantUnit> findUnitForLeaseUpdate(
            @Param("unitId") Long unitId
    );

    @Query("""
    select count(u)
    from TenantUnit u
    where u.property.owner.id = :ownerId
    """)
    long countUnitsForOwner(@Param("ownerId") Long ownerId);

    @Query("""
    select count(u)
    from TenantUnit u
    where u.property.owner.id = :ownerId
      and u.status = :status
    """)
    long countUnitsForOwnerByStatus(
            @Param("ownerId") Long ownerId,
            @Param("status") TenantUnit.UnitStatus status
    );
}
