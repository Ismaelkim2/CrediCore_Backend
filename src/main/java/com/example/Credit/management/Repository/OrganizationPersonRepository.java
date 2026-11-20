package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.OrganizationPerson;
import com.example.Credit.management.Model.OrganizationProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrganizationPersonRepository
        extends JpaRepository<OrganizationPerson, Long> {

    List<OrganizationPerson> findByOrganization(OrganizationProfile organization);

    List<OrganizationPerson> findByOrganization_Id(Long organizationId);



}