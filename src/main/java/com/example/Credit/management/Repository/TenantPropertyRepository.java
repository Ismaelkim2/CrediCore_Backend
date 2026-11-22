
package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.TenantProperty;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantPropertyRepository
        extends JpaRepository<TenantProperty, Long> {

    // TENANT_AGENT: list only properties belonging to the authenticated agent.
    List<TenantProperty> findByOwner_IdOrderByCreatedAtDesc(Long ownerId);

    // TENANT_AGENT: retrieve a property only when it belongs to that agent.
    Optional<TenantProperty> findByIdAndOwner_Id(
            Long id,
            Long ownerId
    );

    // TENANT_AGENT: check property-code uniqueness within their portfolio.
    boolean existsByOwner_IdAndPropertyCodeIgnoreCase(
            Long ownerId,
            String propertyCode
    );

    boolean existsByOwner_IdAndPropertyCodeIgnoreCaseAndIdNot(
            Long ownerId,
            String propertyCode,
            Long id
    );

    // TENANT_AGENT: count only their own properties.
    long countByOwner_Id(Long ownerId);

    // ADMIN: count properties across all tenant agents.
    long count();
}
