package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.Account;
import com.example.Credit.management.Model.OrganizationProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationProfileRepository
        extends JpaRepository<OrganizationProfile, Long> {

    Optional<OrganizationProfile> findByAccount_Id(
            Long accountId
    );

    Optional<OrganizationProfile> findByRegistrationNumber(
            String registrationNumber
    );

    Optional<OrganizationProfile> findByKraPin(
            String kraPin
    );

    Optional<OrganizationProfile> findByEmail(
            String email
    );

    Optional<OrganizationProfile> findByPhone(
            String phone
    );

    boolean existsByRegistrationNumber(
            String registrationNumber
    );

    boolean existsByKraPin(
            String kraPin
    );

    Optional<OrganizationProfile> findByAccount(
            Account account
    );


    // =====================================================
    // FIND PROFILE DIRECTLY BY ACCOUNT ID
    // =====================================================



}