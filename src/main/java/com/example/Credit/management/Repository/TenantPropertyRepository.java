package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.TenantProperty;
import com.twilio.base.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.awt.print.Pageable;
import java.util.List;
import java.util.Optional;

@Repository

public interface TenantPropertyRepository extends JpaRepository<TenantProperty, Long> {

    List<TenantProperty> findByOwner_IdOrderByCreatedAtDesc(Long ownerId);

    Optional<TenantProperty> findByIdAndOwner_Id(
            Long id,
            Long ownerId
    );

    boolean existsByOwner_IdAndPropertyCodeIgnoreCase(
            Long ownerId,
            String propertyCode
    );

    boolean existsByOwner_IdAndPropertyCodeIgnoreCaseAndIdNot(
            Long ownerId,
            String propertyCode,
            Long id
    );

    long countByOwner_Id(Long ownerId);
}
