package com.example.Credit.management.Repository;

import com.example.Credit.management.Model.Account;
import com.example.Credit.management.Model.Document;
import com.example.Credit.management.Model.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository
        extends JpaRepository<Document, Long> {

    // =====================================================
    // ALL DOCUMENTS FOR ACCOUNT
    // =====================================================

    List<Document> findByAccount(Account account);


    // =====================================================
    // ALL DOCUMENTS BY ACCOUNT ID
    // =====================================================

    List<Document> findByAccount_Id(Long accountId);


    // =====================================================
    // DOCUMENT BY ACCOUNT + TYPE
    // =====================================================

    Optional<Document> findByAccountAndDocumentType(
            Account account,
            DocumentType documentType
    );


    // =====================================================
    // DOCUMENT BY ACCOUNT ID + TYPE
    // =====================================================

    Optional<Document> findByAccount_IdAndDocumentType(
            Long accountId,
            DocumentType documentType
    );


    // =====================================================
    // ALL DOCUMENTS OF TYPE
    // =====================================================

    List<Document> findByDocumentType(
            DocumentType documentType
    );


    // =====================================================
    // ACTIVE DOCUMENTS
    // =====================================================

    List<Document> findByAccountAndActiveTrue(
            Account account
    );


    // =====================================================
    // VERIFIED DOCUMENTS
    // =====================================================

    List<Document> findByAccountAndVerifiedTrue(
            Account account
    );


    // =====================================================
    // ACTIVE + VERIFIED DOCUMENTS
    // =====================================================

    List<Document> findByAccountAndActiveTrueAndVerifiedTrue(
            Account account
    );
}

