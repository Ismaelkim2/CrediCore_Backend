package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.DocumentType;
import com.example.Credit.management.Model.OrganizationDocument;
import com.example.Credit.management.Model.OrganizationProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationDocumentRepository
        extends JpaRepository<OrganizationDocument, Long> {

    // =====================================================
    // ALL DOCUMENTS FOR ORGANIZATION PROFILE
    // =====================================================

    List<OrganizationDocument> findByOrganization(
            OrganizationProfile organization
    );


    // =====================================================
    // DOCUMENT BY ORGANIZATION + DOCUMENT TYPE
    // =====================================================

    Optional<OrganizationDocument> findByOrganizationAndDocumentType(
            OrganizationProfile organization,
            DocumentType documentType
    );


    // =====================================================
    // DOCUMENT BY ORGANIZATION ID + DOCUMENT TYPE
    // Optional but useful
    // =====================================================

    Optional<OrganizationDocument> findByOrganization_IdAndDocumentType(
            Long organizationId,
            DocumentType documentType
    );


    // =====================================================
    // ALL DOCUMENTS BY ORGANIZATION ID
    // Optional but useful
    // =====================================================

    List<OrganizationDocument> findByOrganization_Id(
            Long organizationId
    );
}