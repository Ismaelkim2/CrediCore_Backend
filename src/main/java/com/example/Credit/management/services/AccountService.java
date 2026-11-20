package com.example.Credit.management.services;

import com.example.Credit.management.DTO.AccountDTO;
import com.example.Credit.management.DTO.CustomerProfileDTO;
import com.example.Credit.management.Model.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface AccountService {

    // =====================================================
    // ACCOUNT CREATION
    // =====================================================

    Account createAccount(Account account);


    // =====================================================
    // ACCOUNT LOOKUPS
    // =====================================================

    Account getByNationalId(String nationalId);

    Account getByAccountNumber(String accountNumber);

    Account getAccountByNationalId(Long idNo);

    Optional<Account> getByEmail(String email);


    // =====================================================
    // ORGANIZATION DOCUMENT LOOKUPS
    // =====================================================

    /**
     * Get all organization documents belonging to an account.
     *
     * Account
     *    ↓
     * OrganizationProfile
     *    ↓
     * OrganizationDocument
     */
    List<OrganizationDocument> getOrganizationDocumentsByAccountId(
            Long accountId
    );


    /**
     * Get one organization document by account and document type.
     *
     * Example:
     *
     * getOrganizationDocument(
     *      10L,
     *      DocumentType.CERTIFICATE_OF_INCORPORATION
     * );
     */
    Optional<OrganizationDocument> getOrganizationDocument(
            Long accountId,
            DocumentType documentType
    );


    // =====================================================
    // ACCOUNT SAVE
    // =====================================================

    Account save(Account account);


    // =====================================================
    // ACCOUNT LIST / SEARCH
    // =====================================================

    List<Account> getAllAccounts();

    List<Account> searchAccountsByNationalId(
            String nationalId
    );

    AccountDTO getAccountDTOByNationalId(
            String nationalId
    );


    // =====================================================
    // PHONE
    // =====================================================

    boolean existsByPhoneNumber(
            String phoneNumber
    );


    // =====================================================
    // ACCOUNT STATUS
    // =====================================================

    void refreshAccountStatuses();

    void closeAccount(
            String accountNumber,
            String reason
    );


    // =====================================================
    // STATEMENTS
    // =====================================================

    List<Map<String, Object>> getStatements(
            String accountNumber
    );

    void saveStatements(
            String accountNumber,
            List<Map<String, Object>> transactions
    );


    // =====================================================
    // LOANS
    // =====================================================

    Double getBorrowedAmount(
            String accountNumber
    );


    // =====================================================
    // TRANSFERS
    // =====================================================

    boolean transferFunds(
            String fromAccountNumber,
            String toAccountNumber,
            double amount
    );


    // =====================================================
    // CUSTOMER PROFILE
    // =====================================================

    CustomerProfileDTO toCustomerProfile(
            Account account
    );


    // =====================================================
    // ACCOUNT DTO
    // =====================================================

    AccountDTO toDTO(
            Account account
    );


    // =====================================================
    // ACCOUNT DOCUMENTS
    // =====================================================

    /**
     * Save an individual account document.
     */
    Document saveAccountDocument(
            Long accountId,
            MultipartFile file,
            DocumentType documentType,
            String uploadedBy
    );


    /**
     * Save an organization document using
     * the OrganizationProfile ID.
     */
    OrganizationDocument saveOrganizationDocument(
            Long organizationId,
            MultipartFile file,
            DocumentType documentType
    );


    /**
     * Save an organization document using
     * the Account ID.
     *
     * Account ID
     *     ↓
     * Account
     *     ↓
     * OrganizationProfile
     *     ↓
     * OrganizationDocument
     */
    OrganizationDocument saveOrganizationDocumentByAccountId(
            Long accountId,
            MultipartFile file,
            DocumentType documentType
    );

    Optional<OrganizationDocument> getOrganizationDocumentById(
            Long accountId,
            Long documentId
    );

    List<OrganizationPerson> saveOrganizationPeople(
            Long accountId,
            List<OrganizationPerson> people
    );

    Optional<OrganizationDocument> getOrganizationDocument(
            Long accountId,
            Long documentId
    );
}

