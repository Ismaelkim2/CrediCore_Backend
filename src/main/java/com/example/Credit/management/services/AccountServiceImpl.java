package com.example.Credit.management.services;

import com.example.Credit.management.DTO.AccountDTO;
import com.example.Credit.management.DTO.CustomerProfileDTO;
import com.example.Credit.management.DTO.DocumentDTO;
import com.example.Credit.management.DTO.TransactionDTO;

import com.example.Credit.management.Model.Account;
import com.example.Credit.management.Model.CustomerCategory;
import com.example.Credit.management.Model.Document;
import com.example.Credit.management.Model.DocumentType;
import com.example.Credit.management.Model.Loan;
import com.example.Credit.management.Model.OrganizationDocument;
import com.example.Credit.management.Model.OrganizationPerson;
import com.example.Credit.management.Model.OrganizationProfile;
import com.example.Credit.management.Model.Transaction;

import com.example.Credit.management.Repository.AccountRepository;
import com.example.Credit.management.Repository.DocumentRepository;
import com.example.Credit.management.Repository.LoanRepository;
import com.example.Credit.management.Repository.OrganizationDocumentRepository;
import com.example.Credit.management.Repository.OrganizationPersonRepository;
import com.example.Credit.management.Repository.OrganizationProfileRepository;
import com.example.Credit.management.Repository.TransactionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AccountServiceImpl implements AccountService {

    private static final Logger logger =
            LoggerFactory.getLogger(AccountServiceImpl.class);

    // ============================================================
    // CONSTANTS
    // ============================================================

    private static final String ACTIVE = "ACTIVE";
    private static final String DORMANT = "DORMANT";
    private static final String CLOSED = "CLOSED";
    private static final String PENDING = "PENDING";

    private static final String DEFAULT_CURRENCY = "KES";

    private static final int DEFAULT_DORMANCY_MONTHS = 6;

    private static final String DOCUMENT_UPLOAD_DIRECTORY =
            "uploads/documents";

    private static final String DOCUMENT_BASE_URL =
            "http://localhost:8080/uploads/documents/";

    private static final long MAX_DOCUMENT_SIZE =
            10L * 1024L * 1024L;

    // ============================================================
    // REPOSITORIES
    // ============================================================

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OrganizationProfileRepository organizationProfileRepository;

    @Autowired
    private OrganizationPersonRepository personRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired(required = false)
    private EmailService emailService;

    @Autowired(required = false)
    private TransactionRepository transactionRepository;

    @Autowired(required = false)
    private DocumentRepository documentRepository;

    @Autowired(required = false)
    private OrganizationDocumentRepository
            organizationDocumentRepository;

    // ============================================================
    // TEMPORARY STATEMENT STORE
    // ============================================================

    private final Map<String, List<Map<String, Object>>>
            statementsStore = new ConcurrentHashMap<>();

    // ============================================================
    // ACCOUNT NUMBER GENERATION
    // ============================================================

    private String generateAccountNumber() {

        Random random = new Random();

        String accountNumber;

        do {

            accountNumber =
                    "ACC-" +
                            (1000 + random.nextInt(9000));

        } while (
                accountRepository
                        .findByAccountNumber(accountNumber)
                        .isPresent()
        );

        return accountNumber;
    }

    // ============================================================
    // CREATE ACCOUNT
    // ============================================================

    @Override
    @Transactional
    public Account createAccount(Account dto) {

        if (dto == null) {

            throw new IllegalArgumentException(
                    "Account information cannot be null."
            );
        }

        if (dto.getCustomerCategory() == null) {

            throw new IllegalArgumentException(
                    "Customer category is required."
            );
        }

        logger.info(
                "Opening {} account",
                dto.getCustomerCategory()
        );

        // ========================================================
        // VALIDATION
        // ========================================================

        if (dto.getCustomerCategory() ==
                CustomerCategory.INDIVIDUAL) {

            validateIndividualAccount(dto);

        } else {

            validateOrganizationAccount(dto);
        }

        validateCategorySpecificData(dto);

        // ========================================================
        // ACCOUNT NUMBER
        // ========================================================

        dto.setAccountNumber(
                generateAccountNumber()
        );

        // ========================================================
        // DEFAULT VALUES
        // ========================================================

        if (dto.getBalance() == null) {
            dto.setBalance(0.0);
        }

        if (dto.getCreatedAt() == null) {
            dto.setCreatedAt(LocalDateTime.now());
        }

        if (dto.getAccountStatus() == null ||
                dto.getAccountStatus().isBlank()) {

            dto.setAccountStatus(ACTIVE);
        }

        if (dto.getApprovalStatus() == null ||
                dto.getApprovalStatus().isBlank()) {

            dto.setApprovalStatus(PENDING);
        }

        if (dto.getCurrency() == null ||
                dto.getCurrency().isBlank()) {

            dto.setCurrency(DEFAULT_CURRENCY);
        }

        if (dto.getDormancyMonths() == null ||
                dto.getDormancyMonths() <= 0) {

            dto.setDormancyMonths(
                    DEFAULT_DORMANCY_MONTHS
            );
        }

        if (dto.getRelationshipManager() == null ||
                dto.getRelationshipManager().isBlank()) {

            dto.setRelationshipManager("UNASSIGNED");
        }

        if (dto.getRiskRating() == null ||
                dto.getRiskRating().isBlank()) {

            dto.setRiskRating("LOW");
        }

        if (dto.getAmlStatus() == null ||
                dto.getAmlStatus().isBlank()) {

            dto.setAmlStatus(PENDING);
        }

        if (dto.getKycStatus() == null ||
                dto.getKycStatus().isBlank()) {

            dto.setKycStatus(PENDING);
        }

        if (dto.getCifNumber() == null ||
                dto.getCifNumber().isBlank()) {

            dto.setCifNumber(
                    "CIF-" + System.currentTimeMillis()
            );
        }

        if (dto.getProfileCompletion() == null) {

            dto.setProfileCompletion(0);
        }

        // ========================================================
        // NOTIFICATION DEFAULTS
        // ========================================================

        if (dto.getNotificationSoundEnabled() == null) {
            dto.setNotificationSoundEnabled(true);
        }

        if (dto.getSmsAlerts() == null) {
            dto.setSmsAlerts(true);
        }

        if (dto.getEmailAlerts() == null) {
            dto.setEmailAlerts(true);
        }

        if (dto.getInternetBanking() == null) {
            dto.setInternetBanking(true);
        }

        if (dto.getMobileBanking() == null) {
            dto.setMobileBanking(true);
        }

        // ========================================================
        // SAVE ACCOUNT FIRST
        // ========================================================

        Account saved =
                accountRepository.save(dto);

        accountRepository.flush();

        // ========================================================
        // ORGANIZATION PROFILE
        // ========================================================

        if (saved.getCustomerCategory() !=
                CustomerCategory.INDIVIDUAL) {

            OrganizationProfile profile =
                    createOrganizationProfile(saved);

            OrganizationProfile savedProfile =
                    organizationProfileRepository.save(profile);

            organizationProfileRepository.flush();

            saved.setOrganizationProfile(
                    savedProfile
            );

            saved =
                    accountRepository.save(saved);

            accountRepository.flush();

            logger.info(
                    "OrganizationProfile created successfully: " +
                            "profileId={}, accountId={}, accountNumber={}",
                    savedProfile.getId(),
                    saved.getId(),
                    saved.getAccountNumber()
            );
        }

        // ========================================================
        // EMAIL
        // ========================================================

        try {

            if (emailService != null &&
                    saved.getEmail() != null &&
                    !saved.getEmail().isBlank()) {

                emailService.sendAccountCreatedEmail(
                        saved.getEmail(),
                        saved.getCustomerCategory().name(),
                        saved.getAccountNumber()
                );
            }

        } catch (Exception ex) {

            logger.warn(
                    "Email sending failed for account {}",
                    saved.getAccountNumber(),
                    ex
            );
        }

        // ========================================================
        // FINAL LOG
        // ========================================================

        logger.info("=================================================");
        logger.info("ACCOUNT CREATED SUCCESSFULLY");
        logger.info("Account ID     = {}", saved.getId());
        logger.info(
                "Account Number = {}",
                saved.getAccountNumber()
        );
        logger.info(
                "Customer       = {}",
                saved.getFullName() != null
                        ? saved.getFullName()
                        : saved.getOrganizationName()
        );
        logger.info(
                "Category       = {}",
                saved.getCustomerCategory()
        );
        logger.info(
                "Organization Profile = {}",
                saved.getOrganizationProfile() != null
                        ? saved.getOrganizationProfile().getId()
                        : null
        );
        logger.info("=================================================");

        return saved;
    }

    // ============================================================
    // CREATE ORGANIZATION PROFILE
    // ============================================================

    private OrganizationProfile createOrganizationProfile(
            Account account) {

        if (account == null) {

            throw new IllegalArgumentException(
                    "Account cannot be null."
            );
        }

        if (account.getCustomerCategory() ==
                CustomerCategory.INDIVIDUAL) {

            return null;
        }

        OrganizationProfile profile =
                new OrganizationProfile();

        profile.setAccount(account);

        profile.setOrganizationType(
                account.getCustomerCategory()
        );

        profile.setOrganizationName(
                account.getOrganizationName()
        );

        profile.setRegistrationNumber(
                account.getRegistrationNumber()
        );

        profile.setKraPin(
                account.getKraPin()
        );

        profile.setEmail(
                account.getEmail()
        );

        profile.setPhone(
                account.getPhoneNumber()
        );

        profile.setAddress(
                account.getResidentialAddress()
        );

        profile.setCity(
                account.getCity()
        );

        profile.setCounty(
                account.getCounty()
        );

        profile.setImageUrl(
                account.getImageUrl()
        );

        if (profile.getPeople() == null) {

            profile.setPeople(
                    new ArrayList<>()
            );
        }

        logger.info("=================================================");
        logger.info("ORGANIZATION PROFILE CREATED");
        logger.info(
                "Organization Type = {}",
                profile.getOrganizationType()
        );
        logger.info(
                "Organization Name = {}",
                profile.getOrganizationName()
        );
        logger.info(
                "Registration No   = {}",
                profile.getRegistrationNumber()
        );
        logger.info("=================================================");

        return profile;
    }

    // ============================================================
    // SAVE ORGANIZATION PEOPLE
    // ============================================================

    @Transactional
    public List<OrganizationPerson> saveOrganizationPeople(
            Long accountId,
            List<OrganizationPerson> people) {

        if (accountId == null) {

            throw new IllegalArgumentException(
                    "Account ID is required."
            );
        }

        Account account =
                accountRepository.findById(accountId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Account not found: " + accountId
                                )
                        );

        if (account.getCustomerCategory() ==
                CustomerCategory.INDIVIDUAL) {

            throw new IllegalArgumentException(
                    "Individual accounts cannot have organization people."
            );
        }

        OrganizationProfile profile =
                organizationProfileRepository
                        .findByAccount_Id(accountId)
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "OrganizationProfile does not exist " +
                                                "for account " +
                                                accountId
                                )
                        );

        if (people == null ||
                people.isEmpty()) {

            account.setTotalOfficials(0);
            account.setTotalSignatories(0);
            account.setPrimarySignatory(null);

            accountRepository.save(account);

            return Collections.emptyList();
        }

        List<OrganizationPerson> savedPeople =
                new ArrayList<>();

        // ========================================================
        // PRIMARY SIGNATORY VALIDATION
        // ========================================================

        int primarySignatories = 0;

        for (OrganizationPerson person : people) {

            if (person == null) {
                continue;
            }

            if (Boolean.TRUE.equals(
                    person.getPrimarySignatory()
            )) {

                primarySignatories++;
            }
        }

        if (primarySignatories > 1) {

            throw new IllegalArgumentException(
                    "Only one primary signatory is allowed."
            );
        }

        // ========================================================
        // SAVE PEOPLE
        // ========================================================

        for (OrganizationPerson person : people) {

            if (person == null) {
                continue;
            }

            person.setId(null);

            person.setOrganization(profile);

            if (person.getIsOfficial() == null) {
                person.setIsOfficial(false);
            }

            if (person.getIsDirector() == null) {
                person.setIsDirector(false);
            }

            if (person.getIsTrustee() == null) {
                person.setIsTrustee(false);
            }

            if (person.getIsBeneficialOwner() == null) {
                person.setIsBeneficialOwner(false);
            }

            if (person.getIsSignatory() == null) {
                person.setIsSignatory(false);
            }

            if (person.getPrimarySignatory() == null) {
                person.setPrimarySignatory(false);
            }

            OrganizationPerson savedPerson =
                    personRepository.save(person);

            savedPeople.add(savedPerson);
        }

        personRepository.flush();

        // ========================================================
        // COUNTS
        // ========================================================

        long officialCount =
                savedPeople.stream()
                        .filter(Objects::nonNull)
                        .filter(p ->
                                Boolean.TRUE.equals(
                                        p.getIsOfficial()
                                )
                        )
                        .count();

        long signatoryCount =
                savedPeople.stream()
                        .filter(Objects::nonNull)
                        .filter(p ->
                                Boolean.TRUE.equals(
                                        p.getIsSignatory()
                                )
                        )
                        .count();

        account.setTotalOfficials(
                (int) officialCount
        );

        account.setTotalSignatories(
                (int) signatoryCount
        );

        // ========================================================
        // PRIMARY SIGNATORY
        // ========================================================

        Optional<OrganizationPerson> primary =
                savedPeople.stream()
                        .filter(Objects::nonNull)
                        .filter(p ->
                                Boolean.TRUE.equals(
                                        p.getPrimarySignatory()
                                )
                        )
                        .findFirst();

        if (primary.isPresent()) {

            account.setPrimarySignatory(
                    primary.get().getFullName()
            );

        } else {

            account.setPrimarySignatory(null);
        }

        accountRepository.save(account);

        logger.info(
                "Organization people saved: account={}, profile={}, " +
                        "officials={}, signatories={}, primary={}",
                account.getAccountNumber(),
                profile.getId(),
                officialCount,
                signatoryCount,
                account.getPrimarySignatory()
        );

        return savedPeople;
    }

    // ============================================================
    // INDIVIDUAL VALIDATION
    // ============================================================

    private void validateIndividualAccount(
            Account dto) {

        if (dto.getNationalId() == null ||
                dto.getNationalId().isBlank()) {

            throw new IllegalArgumentException(
                    "National ID is required."
            );
        }

        dto.setNationalId(
                dto.getNationalId().trim()
        );

        if (accountRepository
                .findByNationalId(
                        dto.getNationalId()
                )
                .isPresent()) {

            throw new IllegalArgumentException(
                    "National ID already exists."
            );
        }

        if (dto.getEmail() == null ||
                dto.getEmail().isBlank()) {

            throw new IllegalArgumentException(
                    "Email is required."
            );
        }

        dto.setEmail(
                dto.getEmail().trim()
        );

        if (accountRepository
                .findByEmail(
                        dto.getEmail()
                )
                .isPresent()) {

            throw new IllegalArgumentException(
                    "Email already exists."
            );
        }

        if (dto.getPhoneNumber() == null ||
                dto.getPhoneNumber().isBlank()) {

            throw new IllegalArgumentException(
                    "Phone number is required."
            );
        }

        dto.setPhoneNumber(
                dto.getPhoneNumber().trim()
        );

        validatePhoneNumber(
                dto.getPhoneNumber()
        );

        if (accountRepository
                .existsByPhoneNumber(
                        dto.getPhoneNumber()
                )) {

            throw new IllegalArgumentException(
                    "Phone number already exists."
            );
        }

        if (dto.getDateOfBirth() == null) {

            throw new IllegalArgumentException(
                    "Date of Birth is required."
            );
        }

        LocalDate today =
                LocalDate.now();

        if (dto.getDateOfBirth().isAfter(today)) {

            throw new IllegalArgumentException(
                    "Date of Birth cannot be in the future."
            );
        }

        int age =
                Period.between(
                        dto.getDateOfBirth(),
                        today
                ).getYears();

        if (age < 18) {

            throw new IllegalArgumentException(
                    "Customer must be at least 18 years old."
            );
        }

        if (dto.getNextOfKinPhone() != null &&
                !dto.getNextOfKinPhone().isBlank()) {

            dto.setNextOfKinPhone(
                    dto.getNextOfKinPhone().trim()
            );

            validatePhoneNumber(
                    dto.getNextOfKinPhone()
            );

            if (dto.getPhoneNumber()
                    .equals(dto.getNextOfKinPhone())) {

                throw new IllegalArgumentException(
                        "Applicant phone and Next of Kin phone cannot be the same."
                );
            }
        }
    }

    // ============================================================
    // ORGANIZATION VALIDATION
    // ============================================================

    private void validateOrganizationAccount(
            Account dto) {

        if (dto.getOrganizationName() == null ||
                dto.getOrganizationName().isBlank()) {

            throw new IllegalArgumentException(
                    "Organization name is required."
            );
        }

        dto.setOrganizationName(
                dto.getOrganizationName().trim()
        );

        if (dto.getRegistrationNumber() == null ||
                dto.getRegistrationNumber().isBlank()) {

            throw new IllegalArgumentException(
                    "Registration number is required."
            );
        }

        dto.setRegistrationNumber(
                dto.getRegistrationNumber().trim()
        );

        if (dto.getPhoneNumber() != null &&
                !dto.getPhoneNumber().isBlank()) {

            dto.setPhoneNumber(
                    dto.getPhoneNumber().trim()
            );

            validatePhoneNumber(
                    dto.getPhoneNumber()
            );
        }

        if (dto.getEmail() != null &&
                !dto.getEmail().isBlank()) {

            dto.setEmail(
                    dto.getEmail().trim()
            );
        }
    }

    // ============================================================
    // CATEGORY-SPECIFIC VALIDATION
    // ============================================================

    private void validateCategorySpecificData(
            Account dto) {

        switch (dto.getCustomerCategory()) {

            case INDIVIDUAL:

                break;

            case GROUP:

                requireOrganizationName(
                        dto,
                        "Group Name is required."
                );

                requireRegistrationNumber(
                        dto,
                        "Group Registration Number is required."
                );

                break;

            case COMPANY:

                requireOrganizationName(
                        dto,
                        "Company Name is required."
                );

                requireRegistrationNumber(
                        dto,
                        "Company Certificate Number is required."
                );

                break;

            case INSTITUTION:

                requireOrganizationName(
                        dto,
                        "Institution Name is required."
                );

                requireRegistrationNumber(
                        dto,
                        "Institution Registration Number is required."
                );

                break;

            default:

                throw new IllegalArgumentException(
                        "Unknown Customer Category."
                );
        }
    }

    private void requireOrganizationName(
            Account account,
            String message) {

        if (account.getOrganizationName() == null ||
                account.getOrganizationName().isBlank()) {

            throw new IllegalArgumentException(message);
        }
    }

    private void requireRegistrationNumber(
            Account account,
            String message) {

        if (account.getRegistrationNumber() == null ||
                account.getRegistrationNumber().isBlank()) {

            throw new IllegalArgumentException(message);
        }
    }

    // ============================================================
    // SAVE ACCOUNT DOCUMENT
    // ============================================================

    @Override
    @Transactional
    public Document saveAccountDocument(
            Long accountId,
            MultipartFile file,
            DocumentType documentType,
            String uploadedBy) {

        validateDocumentUpload(
                accountId,
                file,
                documentType
        );

        if (documentRepository == null) {

            throw new IllegalStateException(
                    "DocumentRepository is not configured."
            );
        }

        Account account =
                accountRepository
                        .findById(accountId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Account not found with ID: "
                                                + accountId
                                )
                        );

        FileStorageInfo storageInfo =
                storeDocumentFile(file);

        Document document =
                new Document();

        document.setAccount(account);

        document.setDocumentType(
                documentType
        );

        document.setFileName(
                storageInfo.storedFileName
        );

        document.setOriginalFileName(
                storageInfo.originalFileName
        );

        document.setContentType(
                storageInfo.contentType
        );

        document.setFileSize(
                storageInfo.fileSize
        );

        document.setFilePath(
                storageInfo.filePath
        );

        document.setFileUrl(
                storageInfo.fileUrl
        );

        document.setVerified(false);

        document.setActive(true);

        document.setUploadedAt(
                LocalDateTime.now()
        );

        if (uploadedBy != null &&
                !uploadedBy.isBlank()) {

            document.setUploadedBy(
                    uploadedBy.trim()
            );
        }

        Document saved =
                documentRepository.save(document);

        documentRepository.flush();

        logAccountDocument(saved);

        return saved;
    }

    // ============================================================
    // SAVE ORGANIZATION DOCUMENT BY PROFILE ID
    // ============================================================

    @Transactional
    public OrganizationDocument saveOrganizationDocument(
            Long organizationId,
            MultipartFile file,
            DocumentType documentType) {

        if (organizationId == null) {

            throw new IllegalArgumentException(
                    "Organization profile ID is required."
            );
        }

        validateFileAndType(
                file,
                documentType
        );

        if (organizationDocumentRepository == null) {

            throw new IllegalStateException(
                    "OrganizationDocumentRepository is not configured."
            );
        }

        OrganizationProfile organization =
                organizationProfileRepository
                        .findById(organizationId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Organization profile not found: "
                                                + organizationId
                                )
                        );

        FileStorageInfo storageInfo =
                storeDocumentFile(file);

        OrganizationDocument document =
                new OrganizationDocument();

        document.setOrganization(
                organization
        );

        document.setDocumentType(
                documentType
        );

        document.setOriginalName(
                storageInfo.originalFileName
        );

        document.setStoredName(
                storageInfo.storedFileName
        );

        document.setFilePath(
                storageInfo.filePath
        );

        document.setFileSize(
                storageInfo.fileSize
        );

        document.setUploadedAt(
                LocalDateTime.now()
        );

        OrganizationDocument saved =
                organizationDocumentRepository.save(
                        document
                );

        organizationDocumentRepository.flush();

        logOrganizationDocument(saved);

        return saved;
    }

    // ============================================================
    // SAVE ORGANIZATION DOCUMENT BY ACCOUNT ID
    //
    // ACCOUNT
    //    ↓
    // ORGANIZATION PROFILE
    //    ↓
    // ORGANIZATION DOCUMENT
    //
    // This is the preferred method for controller usage.
    // ============================================================

    @Transactional
    public OrganizationDocument saveOrganizationDocumentByAccountId(
            Long accountId,
            MultipartFile file,
            DocumentType documentType) {

        logger.info("=================================================");
        logger.info("ORGANIZATION DOCUMENT UPLOAD");
        logger.info("ACCOUNT ID = {}", accountId);
        logger.info("DOCUMENT TYPE = {}", documentType);

        if (accountId == null) {

            throw new IllegalArgumentException(
                    "Account ID is required."
            );
        }

        validateFileAndType(
                file,
                documentType
        );

        if (organizationDocumentRepository == null) {

            throw new IllegalStateException(
                    "OrganizationDocumentRepository is not configured."
            );
        }

        // ========================================================
        // 1. FIND ACCOUNT
        // ========================================================

        Account account =
                accountRepository
                        .findById(accountId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Account not found: " + accountId
                                )
                        );

        logger.info(
                "ACCOUNT FOUND: id={}, number={}, category={}",
                account.getId(),
                account.getAccountNumber(),
                account.getCustomerCategory()
        );

        // ========================================================
        // 2. MUST BE ORGANIZATION
        // ========================================================

        if (account.getCustomerCategory() ==
                CustomerCategory.INDIVIDUAL) {

            throw new IllegalArgumentException(
                    "Individual accounts cannot have organization documents."
            );
        }

        // ========================================================
        // 3. FIND ORGANIZATION PROFILE
        // ========================================================

        OrganizationProfile profile =
                organizationProfileRepository
                        .findByAccount_Id(accountId)
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Organization profile not found " +
                                                "for account ID: " +
                                                accountId
                                )
                        );

        logger.info(
                "ORGANIZATION PROFILE FOUND: profileId={}, accountId={}",
                profile.getId(),
                accountId
        );

        // ========================================================
        // 4. STORE FILE
        // ========================================================

        FileStorageInfo storageInfo =
                storeDocumentFile(file);

        // ========================================================
        // 5. CHECK FOR EXISTING DOCUMENT OF SAME TYPE
        //
        // We deliberately use findByOrganization(profile) here
        // instead of requiring an additional repository method.
        // ========================================================

        OrganizationDocument document = null;

        List<OrganizationDocument> existingDocuments =
                organizationDocumentRepository
                        .findByOrganization(profile);

        if (existingDocuments != null) {

            for (OrganizationDocument existing :
                    existingDocuments) {

                if (existing == null) {
                    continue;
                }

                if (existing.getDocumentType() ==
                        documentType) {

                    document = existing;
                    break;
                }
            }
        }

        // ========================================================
        // 6. REPLACE OR CREATE
        // ========================================================

        if (document == null) {

            document =
                    new OrganizationDocument();

            document.setOrganization(
                    profile
            );

            document.setDocumentType(
                    documentType
            );
        }

        document.setOriginalName(
                storageInfo.originalFileName
        );

        document.setStoredName(
                storageInfo.storedFileName
        );

        document.setFilePath(
                storageInfo.filePath
        );

        document.setFileSize(
                storageInfo.fileSize
        );

        document.setUploadedAt(
                LocalDateTime.now()
        );

        // ========================================================
        // 7. SAVE
        // ========================================================

        OrganizationDocument saved =
                organizationDocumentRepository.save(
                        document
                );

        organizationDocumentRepository.flush();

        // ========================================================
        // 8. LOG
        // ========================================================

        logger.info("=================================================");
        logger.info("ORGANIZATION DOCUMENT SAVED SUCCESSFULLY");
        logger.info("ACCOUNT ID      = {}", account.getId());
        logger.info(
                "ACCOUNT NUMBER  = {}",
                account.getAccountNumber()
        );
        logger.info(
                "ORGANIZATION ID = {}",
                profile.getId()
        );
        logger.info(
                "DOCUMENT ID     = {}",
                saved.getId()
        );
        logger.info(
                "DOCUMENT TYPE   = {}",
                saved.getDocumentType()
        );
        logger.info(
                "ORIGINAL NAME   = {}",
                saved.getOriginalName()
        );
        logger.info(
                "STORED NAME     = {}",
                saved.getStoredName()
        );
        logger.info(
                "DATABASE PATH   = {}",
                saved.getFilePath()
        );
        logger.info(
                "BROWSER URL     = {}",
                buildDocumentUrl(
                        saved.getFilePath()
                )
        );
        logger.info("=================================================");

        return saved;
    }

    // ============================================================
    // DOCUMENT UPLOAD VALIDATION
    // ============================================================

    private void validateDocumentUpload(
            Long accountId,
            MultipartFile file,
            DocumentType documentType) {

        if (accountId == null) {

            throw new IllegalArgumentException(
                    "Account ID is required."
            );
        }

        validateFileAndType(
                file,
                documentType
        );
    }

    private void validateFileAndType(
            MultipartFile file,
            DocumentType documentType) {

        if (file == null ||
                file.isEmpty()) {

            throw new IllegalArgumentException(
                    "Document file is required."
            );
        }

        if (documentType == null) {

            throw new IllegalArgumentException(
                    "Document type is required."
            );
        }

        if (file.getSize() > MAX_DOCUMENT_SIZE) {

            throw new IllegalArgumentException(
                    "Document size cannot exceed 10 MB."
            );
        }
    }

    // ============================================================
    // FILE STORAGE
    // ============================================================

    private FileStorageInfo storeDocumentFile(
            MultipartFile file) {

        String originalFileName =
                file.getOriginalFilename();

        if (originalFileName == null ||
                originalFileName.isBlank()) {

            originalFileName = "document";
        }

        // ========================================================
        // SECURITY
        // ========================================================

        originalFileName =
                Paths.get(originalFileName)
                        .getFileName()
                        .toString();

        String contentType =
                file.getContentType();

        long fileSize =
                file.getSize();

        // ========================================================
        // EXTENSION
        // ========================================================

        String extension = "";

        int dotIndex =
                originalFileName.lastIndexOf('.');

        if (dotIndex > 0 &&
                dotIndex < originalFileName.length() - 1) {

            extension =
                    originalFileName
                            .substring(dotIndex)
                            .toLowerCase();
        }

        // ========================================================
        // UNIQUE FILE NAME
        // ========================================================

        String storedFileName =
                UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        + extension;

        // ========================================================
        // DIRECTORY
        // ========================================================

        Path uploadDirectory =
                Paths.get(
                        DOCUMENT_UPLOAD_DIRECTORY
                );

        try {

            Files.createDirectories(
                    uploadDirectory
            );

        } catch (IOException ex) {

            logger.error(
                    "Unable to create document directory",
                    ex
            );

            throw new RuntimeException(
                    "Unable to create document upload directory.",
                    ex
            );
        }

        // ========================================================
        // TARGET
        // ========================================================

        Path targetPath =
                uploadDirectory.resolve(
                        storedFileName
                );

        // ========================================================
        // SAVE
        // ========================================================

        try {

            Files.copy(
                    file.getInputStream(),
                    targetPath,
                    StandardCopyOption.REPLACE_EXISTING
            );

        } catch (IOException ex) {

            logger.error(
                    "Failed to save document {}",
                    originalFileName,
                    ex
            );

            throw new RuntimeException(
                    "Failed to save document file.",
                    ex
            );
        }

        // ========================================================
        // VERIFY
        // ========================================================

        if (!Files.exists(targetPath)) {

            throw new RuntimeException(
                    "Document was not physically saved: "
                            + targetPath.toAbsolutePath()
            );
        }

        long actualSize;

        try {

            actualSize =
                    Files.size(targetPath);

        } catch (IOException ex) {

            actualSize = fileSize;
        }

        String filePath =
                DOCUMENT_UPLOAD_DIRECTORY +
                        "/" +
                        storedFileName;

        String fileUrl =
                buildDocumentUrl(
                        filePath
                );

        logger.info("=================================================");
        logger.info("DOCUMENT FILE STORED");
        logger.info(
                "Original Name = {}",
                originalFileName
        );
        logger.info(
                "Stored Name   = {}",
                storedFileName
        );
        logger.info(
                "Physical Path = {}",
                targetPath.toAbsolutePath()
        );
        logger.info(
                "Database Path = {}",
                filePath
        );
        logger.info(
                "Browser URL   = {}",
                fileUrl
        );
        logger.info(
                "File Exists   = {}",
                Files.exists(targetPath)
        );
        logger.info(
                "File Size     = {}",
                actualSize
        );
        logger.info("=================================================");

        return new FileStorageInfo(
                originalFileName,
                storedFileName,
                contentType,
                actualSize,
                filePath,
                fileUrl
        );
    }

    // ============================================================
    // DOCUMENT URL
    // ============================================================

    private String buildDocumentUrl(
            String filePath) {

        if (filePath == null ||
                filePath.isBlank()) {

            return null;
        }

        String cleanPath =
                filePath.trim()
                        .replace("\\", "/")
                        .replaceFirst("^/+", "");

        if (cleanPath.startsWith("http://") ||
                cleanPath.startsWith("https://")) {

            return cleanPath;
        }

        if (cleanPath.startsWith(
                "uploads/documents/"
        )) {

            String fileName =
                    cleanPath.substring(
                            "uploads/documents/".length()
                    );

            return DOCUMENT_BASE_URL +
                    fileName;
        }

        return "http://localhost:8080/" +
                cleanPath;
    }

    // ============================================================
    // IMAGE URL
    // ============================================================

    private String buildImageUrl(
            String imagePath) {

        if (imagePath == null ||
                imagePath.isBlank()) {

            return null;
        }

        String cleanPath =
                imagePath.trim()
                        .replace("\\", "/")
                        .replaceFirst("^/+", "");

        if (cleanPath.startsWith("http://") ||
                cleanPath.startsWith("https://")) {

            return cleanPath;
        }

        return "http://localhost:8080/" +
                cleanPath;
    }

    // ============================================================
    // LOAD ALL DOCUMENTS
    // ============================================================

    private List<DocumentDTO> loadAllDocuments(
            Account account) {

        List<DocumentDTO> allDocuments =
                new ArrayList<>();

        if (account == null) {

            return allDocuments;
        }

        logger.info("=================================================");
        logger.info("LOADING ALL ACCOUNT DOCUMENTS");
        logger.info(
                "Account ID     = {}",
                account.getId()
        );
        logger.info(
                "Account Number = {}",
                account.getAccountNumber()
        );
        logger.info(
                "Customer Type  = {}",
                account.getCustomerCategory()
        );

        Set<String> documentKeys =
                new HashSet<>();

        // ========================================================
        // ACCOUNT DOCUMENTS
        // ========================================================

        if (documentRepository != null &&
                account.getId() != null) {

            try {

                List<Document> documents =
                        documentRepository
                                .findByAccount_Id(
                                        account.getId()
                                );

                if (documents != null) {

                    logger.info(
                            "ACCOUNT {} -> ACCOUNT DOCUMENTS FOUND = {}",
                            account.getAccountNumber(),
                            documents.size()
                    );

                    for (Document document :
                            documents) {

                        if (document == null) {
                            continue;
                        }

                        DocumentDTO dto =
                                toDocumentDTO(document);

                        if (dto == null) {
                            continue;
                        }

                        String key =
                                createDocumentKey(dto);

                        if (documentKeys.add(key)) {

                            allDocuments.add(dto);
                        }
                    }
                }

            } catch (Exception ex) {

                logger.error(
                        "Failed to retrieve account documents for account {}",
                        account.getAccountNumber(),
                        ex
                );
            }
        }

        // ========================================================
        // ORGANIZATION DOCUMENTS
        // ========================================================

        if (account.getCustomerCategory() !=
                CustomerCategory.INDIVIDUAL &&
                account.getId() != null) {

            try {

                OrganizationProfile profile =
                        organizationProfileRepository
                                .findByAccount_Id(
                                        account.getId()
                                )
                                .orElse(null);

                if (profile == null) {

                    logger.warn(
                            "ACCOUNT {} -> ORGANIZATION PROFILE NOT FOUND",
                            account.getAccountNumber()
                    );

                } else if (
                        organizationDocumentRepository != null
                ) {

                    List<OrganizationDocument>
                            organizationDocuments =
                            organizationDocumentRepository
                                    .findByOrganization(
                                            profile
                                    );

                    if (organizationDocuments != null) {

                        logger.info(
                                "ACCOUNT {} -> ORGANIZATION DOCUMENTS FOUND = {}",
                                account.getAccountNumber(),
                                organizationDocuments.size()
                        );

                        for (
                                OrganizationDocument document :
                                organizationDocuments
                        ) {

                            if (document == null) {
                                continue;
                            }

                            DocumentDTO dto =
                                    toDocumentDTO(document);

                            if (dto == null) {
                                continue;
                            }

                            String key =
                                    createDocumentKey(dto);

                            if (documentKeys.add(key)) {

                                allDocuments.add(dto);
                            }
                        }
                    }
                }

            } catch (Exception ex) {

                logger.error(
                        "Failed to retrieve organization documents " +
                                "for account {}",
                        account.getAccountNumber(),
                        ex
                );
            }
        }

        logger.info(
                "ACCOUNT {} TOTAL DOCUMENTS = {}",
                account.getAccountNumber(),
                allDocuments.size()
        );

        return allDocuments;
    }

    // ============================================================
    // DOCUMENT UNIQUE KEY
    // ============================================================

    private String createDocumentKey(
            DocumentDTO document) {

        if (document == null) {

            return UUID.randomUUID()
                    .toString();
        }

        if (document.getSource() != null &&
                document.getId() != null) {

            return document.getSource()
                    + "-"
                    + document.getId();
        }

        if (document.getFilePath() != null) {

            return document.getFilePath();
        }

        return UUID.randomUUID()
                .toString();
    }

    // ============================================================
    // ACCOUNT DOCUMENT -> DTO
    // ============================================================

    private DocumentDTO toDocumentDTO(
            Document document) {

        if (document == null) {

            return null;
        }

        DocumentDTO dto =
                new DocumentDTO();

        dto.setId(
                document.getId()
        );

        if (document.getDocumentType() != null) {

            dto.setDocumentType(
                    document.getDocumentType().name()
            );
        }

        dto.setOriginalName(
                document.getOriginalFileName()
        );

        dto.setStoredName(
                document.getFileName()
        );

        dto.setFilePath(
                document.getFilePath()
        );

        dto.setFileSize(
                document.getFileSize()
        );

        dto.setUploadedAt(
                document.getUploadedAt()
        );

        String fileUrl =
                document.getFileUrl();

        if (fileUrl == null ||
                fileUrl.isBlank()) {

            fileUrl =
                    buildDocumentUrl(
                            document.getFilePath()
                    );

        } else {

            fileUrl =
                    buildDocumentUrl(
                            fileUrl
                    );
        }

        dto.setFileUrl(
                fileUrl
        );

        dto.setSource(
                "ACCOUNT"
        );

        return dto;
    }

    // ============================================================
    // ORGANIZATION DOCUMENT -> DTO
    // ============================================================

    private DocumentDTO toDocumentDTO(
            OrganizationDocument document) {

        if (document == null) {

            return null;
        }

        DocumentDTO dto =
                new DocumentDTO();

        dto.setId(
                document.getId()
        );

        if (document.getDocumentType() != null) {

            dto.setDocumentType(
                    document.getDocumentType().name()
            );
        }

        dto.setOriginalName(
                document.getOriginalName()
        );

        dto.setStoredName(
                document.getStoredName()
        );

        dto.setFilePath(
                document.getFilePath()
        );

        dto.setFileSize(
                document.getFileSize()
        );

        dto.setUploadedAt(
                document.getUploadedAt()
        );

        dto.setFileUrl(
                buildDocumentUrl(
                        document.getFilePath()
                )
        );

        dto.setSource(
                "ORGANIZATION"
        );

        return dto;
    }

    // ============================================================
    // GET ORGANIZATION DOCUMENTS BY ACCOUNT ID
    // ============================================================

    @Transactional(readOnly = true)
    public List<OrganizationDocument>
    getOrganizationDocumentsByAccountId(
            Long accountId) {

        logger.info("=================================================");
        logger.info("GET ORGANIZATION DOCUMENTS");
        logger.info("ACCOUNT ID = {}", accountId);

        if (accountId == null) {

            throw new IllegalArgumentException(
                    "Account ID is required."
            );
        }

        Account account =
                accountRepository
                        .findById(accountId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Account not found: "
                                                + accountId
                                )
                        );

        if (account.getCustomerCategory() ==
                CustomerCategory.INDIVIDUAL) {

            logger.info(
                    "Account {} is INDIVIDUAL. " +
                            "No organization documents.",
                    accountId
            );

            return Collections.emptyList();
        }

        OrganizationProfile profile =
                organizationProfileRepository
                        .findByAccount_Id(accountId)
                        .orElse(null);

        if (profile == null) {

            logger.warn(
                    "OrganizationProfile not found for account {}",
                    accountId
            );

            return Collections.emptyList();
        }

        if (organizationDocumentRepository == null) {

            throw new IllegalStateException(
                    "OrganizationDocumentRepository is not configured."
            );
        }

        List<OrganizationDocument> documents =
                organizationDocumentRepository
                        .findByOrganization(profile);

        if (documents == null) {

            return Collections.emptyList();
        }

        logger.info(
                "ACCOUNT {} -> ORGANIZATION DOCUMENTS FOUND = {}",
                accountId,
                documents.size()
        );

        return documents;
    }



    @Override
    public Optional<OrganizationDocument> getOrganizationDocument(
            Long accountId,
            Long documentId) {

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() ->
                        new RuntimeException(
                                "Account not found with ID: " + accountId
                        )
                );

        OrganizationProfile organization =
                organizationProfileRepository
                        .findByAccount(account)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Organization profile not found for account ID: "
                                                + accountId
                                )
                        );

        return organizationDocumentRepository
                .findById(documentId)
                .filter(document ->
                        document.getOrganization() != null
                                && document.getOrganization().getId()
                                .equals(organization.getId())
                );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrganizationDocument> getOrganizationDocument(
            Long accountId,
            DocumentType documentType) {

        logger.info("=================================================");
        logger.info("GET ORGANIZATION DOCUMENT");
        logger.info("ACCOUNT ID   = {}", accountId);
        logger.info("DOCUMENT TYPE = {}", documentType);

        // ========================================================
        // VALIDATION
        // ========================================================

        if (accountId == null) {
            throw new IllegalArgumentException(
                    "Account ID is required."
            );
        }

        if (documentType == null) {
            throw new IllegalArgumentException(
                    "Document type is required."
            );
        }

        // ========================================================
        // FIND ACCOUNT
        // ========================================================

        Account account =
                accountRepository
                        .findById(accountId)
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Account not found: " + accountId
                                )
                        );

        // ========================================================
        // INDIVIDUAL ACCOUNT
        // ========================================================

        if (account.getCustomerCategory() ==
                CustomerCategory.INDIVIDUAL) {

            logger.info(
                    "Account {} is INDIVIDUAL. " +
                            "No organization document exists.",
                    accountId
            );

            return Optional.empty();
        }

        // ========================================================
        // FIND ORGANIZATION PROFILE
        // ========================================================

        Optional<OrganizationProfile> profileOptional =
                organizationProfileRepository
                        .findByAccount_Id(accountId);

        if (profileOptional.isEmpty()) {

            logger.warn(
                    "OrganizationProfile not found for account {}",
                    accountId
            );

            return Optional.empty();
        }

        OrganizationProfile profile =
                profileOptional.get();

        // ========================================================
        // CHECK REPOSITORY
        // ========================================================

        if (organizationDocumentRepository == null) {

            throw new IllegalStateException(
                    "OrganizationDocumentRepository is not configured."
            );
        }

        // ========================================================
        // FIND DOCUMENT
        // ========================================================

        Optional<OrganizationDocument> document =
                organizationDocumentRepository
                        .findByOrganizationAndDocumentType(
                                profile,
                                documentType
                        );

        // ========================================================
        // LOG RESULT
        // ========================================================

        if (document.isPresent()) {

            OrganizationDocument found =
                    document.get();

            logger.info(
                    "ORGANIZATION DOCUMENT FOUND: " +
                            "id={}, profileId={}, type={}, file={}",
                    found.getId(),
                    profile.getId(),
                    found.getDocumentType(),
                    found.getOriginalName()
            );

        } else {

            logger.info(
                    "No organization document found: " +
                            "accountId={}, profileId={}, type={}",
                    accountId,
                    profile.getId(),
                    documentType
            );
        }

        logger.info("=================================================");

        return document;
    }


    @Override
    @Transactional(readOnly = true)
    public Optional<OrganizationDocument> getOrganizationDocumentById(
            Long accountId,
            Long documentId) {

        if (accountId == null) {
            throw new IllegalArgumentException(
                    "Account ID is required."
            );
        }

        if (documentId == null) {
            throw new IllegalArgumentException(
                    "Document ID is required."
            );
        }

        if (organizationDocumentRepository == null) {
            throw new IllegalStateException(
                    "OrganizationDocumentRepository is not configured."
            );
        }

        // ========================================================
        // FIND ACCOUNT
        // ========================================================

        Account account =
                accountRepository.findById(accountId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Account not found: " + accountId
                                )
                        );

        // ========================================================
        // ORGANIZATION CHECK
        // ========================================================

        if (account.getCustomerCategory() ==
                CustomerCategory.INDIVIDUAL) {

            return Optional.empty();
        }

        // ========================================================
        // FIND ORGANIZATION PROFILE
        // ========================================================

        OrganizationProfile profile =
                organizationProfileRepository
                        .findByAccount_Id(accountId)
                        .orElse(null);

        if (profile == null) {

            logger.warn(
                    "Organization profile not found for account {}",
                    accountId
            );

            return Optional.empty();
        }

        // ========================================================
        // FIND DOCUMENT
        // ========================================================

        Optional<OrganizationDocument> document =
                organizationDocumentRepository.findById(
                        documentId
                );

        // ========================================================
        // SECURITY CHECK
        //
        // Make sure this document actually belongs to
        // the requested account.
        // ========================================================

        if (document.isEmpty()) {
            return Optional.empty();
        }

        OrganizationDocument organizationDocument =
                document.get();

        if (organizationDocument.getOrganization() == null ||
                organizationDocument.getOrganization().getId() == null ||
                !organizationDocument.getOrganization()
                        .getId()
                        .equals(profile.getId())) {

            logger.warn(
                    "Document {} does not belong to account {}",
                    documentId,
                    accountId
            );

            return Optional.empty();
        }

        return Optional.of(organizationDocument);
    }

    // ============================================================
    // CUSTOMER PROFILE
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public CustomerProfileDTO toCustomerProfile(
            Account account) {

        if (account == null) {

            return null;
        }

        CustomerProfileDTO dto =
                new CustomerProfileDTO();

        BeanUtils.copyProperties(
                account,
                dto
        );

        // ========================================================
        // MODE
        // ========================================================

        dto.setModeOfOperation(
                account.getModeOfOperation() != null
                        ? account.getModeOfOperation().name()
                        : null
        );

        // ========================================================
        // IMAGE
        // ========================================================

        dto.setImageUrl(
                buildImageUrl(
                        account.getImageUrl()
                )
        );

        // ========================================================
        // BORROWED
        // ========================================================

        Double borrowedAmount =
                getBorrowedAmount(
                        account.getAccountNumber()
                );

        if (borrowedAmount == null) {

            borrowedAmount = 0.0;
        }

        dto.setBorrowedAmount(
                borrowedAmount
        );

        // ========================================================
        // NET BALANCE
        // ========================================================

        double balance =
                account.getBalance() != null
                        ? account.getBalance()
                        : 0.0;

        dto.setNetBalance(
                balance + borrowedAmount
        );

        // ========================================================
        // ORGANIZATION PEOPLE
        // ========================================================

        if (account.getCustomerCategory() !=
                CustomerCategory.INDIVIDUAL) {

            OrganizationProfile profile =
                    organizationProfileRepository
                            .findByAccount_Id(
                                    account.getId()
                            )
                            .orElse(null);

            if (profile != null &&
                    profile.getPeople() != null) {

                List<OrganizationPerson> people =
                        profile.getPeople();

                dto.setOfficials(
                        people
                );

                dto.setSignatories(
                        people.stream()
                                .filter(Objects::nonNull)
                                .filter(person ->
                                        Boolean.TRUE.equals(
                                                person.getIsSignatory()
                                        )
                                )
                                .toList()
                );

            } else {

                dto.setOfficials(
                        Collections.emptyList()
                );

                dto.setSignatories(
                        Collections.emptyList()
                );
            }

        } else {

            dto.setOfficials(
                    Collections.emptyList()
            );

            dto.setSignatories(
                    Collections.emptyList()
            );
        }

        // ========================================================
        // LOANS
        // ========================================================

        if (loanRepository != null) {

            List<Loan> loans =
                    loanRepository.findByAccount(
                            account
                    );

            dto.setLoans(
                    loans != null
                            ? loans
                            : Collections.emptyList()
            );

        } else {

            dto.setLoans(
                    Collections.emptyList()
            );
        }

        // ========================================================
        // TRANSACTIONS
        // ========================================================

        if (transactionRepository != null) {

            List<TransactionDTO> transactions =
                    transactionRepository
                            .findByAccountNumberOrderByInitiatedAtDesc(
                                    account.getAccountNumber()
                            )
                            .stream()
                            .filter(Objects::nonNull)
                            .map(this::toTransactionDTO)
                            .filter(Objects::nonNull)
                            .toList();

            dto.setTransactions(
                    transactions
            );

        } else {

            dto.setTransactions(
                    Collections.emptyList()
            );
        }

        // ========================================================
        // DOCUMENTS
        // ========================================================

        List<DocumentDTO> documents =
                loadAllDocuments(account);

        dto.setDocuments(
                documents
        );

        logger.info(
                "ACCOUNT {} -> PROFILE RETURNS {} DOCUMENTS",
                account.getAccountNumber(),
                documents.size()
        );

        return dto;
    }

    // ============================================================
    // ACCOUNT -> DTO
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public AccountDTO toDTO(
            Account account) {

        if (account == null) {

            return null;
        }

        AccountDTO dto =
                new AccountDTO();

        // ========================================================
        // DISPLAY NAME
        // ========================================================

        if (account.getCustomerCategory() ==
                CustomerCategory.INDIVIDUAL) {

            dto.setDisplayName(
                    account.getFullName()
            );

        } else {

            dto.setDisplayName(
                    account.getOrganizationName()
            );
        }

        // ========================================================
        // BASIC
        // ========================================================

        dto.setFullName(
                account.getFullName()
        );

        dto.setOrganizationName(
                account.getOrganizationName()
        );

        dto.setRegistrationNumber(
                account.getRegistrationNumber()
        );

        dto.setNationalId(
                account.getNationalId()
        );

        dto.setPhoneNumber(
                account.getPhoneNumber()
        );

        dto.setEmail(
                account.getEmail()
        );

        // ========================================================
        // CATEGORY
        // ========================================================

        dto.setCustomerCategory(
                account.getCustomerCategory() != null
                        ? account.getCustomerCategory().name()
                        : "UNKNOWN"
        );

        // ========================================================
        // MODE
        // ========================================================

        dto.setModeOfOperation(
                account.getModeOfOperation() != null
                        ? account.getModeOfOperation().name()
                        : ""
        );

        // ========================================================
        // ORGANIZATION PROFILE
        // ========================================================

        if (account.getCustomerCategory() !=
                CustomerCategory.INDIVIDUAL) {

            OrganizationProfile profile =
                    organizationProfileRepository
                            .findByAccount_Id(
                                    account.getId()
                            )
                            .orElse(null);

            if (profile != null) {

                if (profile.getPeople() != null) {

                    dto.setOfficials(
                            profile.getPeople()
                    );

                } else {

                    dto.setOfficials(
                            Collections.emptyList()
                    );
                }

                dto.setOrganizationImageUrl(
                        buildImageUrl(
                                profile.getImageUrl()
                        )
                );

            } else {

                dto.setOfficials(
                        Collections.emptyList()
                );

                dto.setOrganizationImageUrl(
                        null
                );
            }

        } else {

            dto.setOfficials(
                    Collections.emptyList()
            );

            dto.setOrganizationImageUrl(
                    null
            );
        }

        // ========================================================
        // ACCOUNT INFORMATION
        // ========================================================

        dto.setTotalOfficials(
                account.getTotalOfficials()
        );

        dto.setTotalSignatories(
                account.getTotalSignatories()
        );

        dto.setApprovalStatus(
                account.getApprovalStatus()
        );

        dto.setAccountStatus(
                account.getAccountStatus()
        );

        dto.setCreatedAt(
                account.getCreatedAt()
        );

        dto.setLastTransactionDate(
                account.getLastTransactionDate()
        );

        dto.setClosedAt(
                account.getClosedAt()
        );

        dto.setClosureReason(
                account.getClosureReason()
        );

        dto.setBalance(
                account.getBalance()
        );

        dto.setAccountNumber(
                account.getAccountNumber()
        );

        dto.setDateOfBirth(
                account.getDateOfBirth()
        );

        dto.setMonthlyIncome(
                account.getMonthlyIncome()
        );

        dto.setMonthlyExpenses(
                account.getMonthlyExpenses()
        );

        dto.setCity(
                account.getCity()
        );

        dto.setCounty(
                account.getCounty()
        );

        dto.setId(
                account.getId()
        );

        // ========================================================
        // IMAGE
        // ========================================================

        dto.setImageUrl(
                buildImageUrl(
                        account.getImageUrl()
                )
        );

        // ========================================================
        // DOCUMENTS
        // ========================================================

        List<DocumentDTO> allDocuments =
                loadAllDocuments(account);

        dto.setDocuments(
                allDocuments
        );

        // ========================================================
        // LOAN SUMMARY
        // ========================================================

        Double borrowed =
                getBorrowedAmount(
                        account.getAccountNumber()
                );

        if (borrowed == null) {

            borrowed = 0.0;
        }

        dto.setBorrowedAmount(
                borrowed
        );

        double balance =
                account.getBalance() != null
                        ? account.getBalance()
                        : 0.0;

        dto.setNetBalance(
                balance + borrowed
        );

        logger.info("=================================================");
        logger.info("ACCOUNT DTO CREATED");
        logger.info(
                "Account Number = {}",
                account.getAccountNumber()
        );
        logger.info(
                "Category       = {}",
                account.getCustomerCategory()
        );
        logger.info(
                "Documents      = {}",
                allDocuments.size()
        );
        logger.info("=================================================");

        return dto;
    }

    // ============================================================
    // TRANSACTION -> DTO
    // ============================================================

    private TransactionDTO toTransactionDTO(
            Transaction tx) {

        if (tx == null) {

            return null;
        }

        TransactionDTO dto =
                new TransactionDTO();

        BeanUtils.copyProperties(
                tx,
                dto
        );

        if (tx.getTransactionType() != null) {

            dto.setTransactionType(
                    tx.getTransactionType().name()
            );
        }

        if (tx.getStatus() != null) {

            dto.setStatus(
                    tx.getStatus().name()
            );
        }

        if (tx.getChannel() != null) {

            dto.setChannel(
                    tx.getChannel().name()
            );
        }

        if (tx.getRiskLevel() != null) {

            dto.setRiskLevel(
                    tx.getRiskLevel().name()
            );
        }

        if (tx.getFraudStatus() != null) {

            dto.setFraudStatus(
                    tx.getFraudStatus().name()
            );
        }

        if (tx.getSettlementStatus() != null) {

            dto.setSettlementStatus(
                    tx.getSettlementStatus().name()
            );
        }

        return dto;
    }

    // ============================================================
    // GET BY NATIONAL ID
    // ============================================================

    @Override
    public Account getByNationalId(
            String nationalId) {

        if (nationalId == null ||
                nationalId.isBlank()) {

            return null;
        }

        return accountRepository
                .findByNationalId(
                        nationalId.trim()
                )
                .orElse(null);
    }

    // ============================================================
    // GET BY ACCOUNT NUMBER
    // ============================================================

    @Override
    public Account getByAccountNumber(
            String accountNumber) {

        if (accountNumber == null ||
                accountNumber.isBlank()) {

            throw new IllegalArgumentException(
                    "Account number is required."
            );
        }

        return accountRepository
                .findByAccountNumber(
                        accountNumber.trim()
                )
                .orElseThrow(
                        () -> new RuntimeException(
                                "Account not found."
                        )
                );
    }

    // ============================================================
    // GET ACCOUNT BY NATIONAL ID NUMBER
    // ============================================================

    @Override
    public Account getAccountByNationalId(
            Long idNo) {

        if (idNo == null) {

            return null;
        }

        return accountRepository
                .findByNationalId(
                        String.valueOf(idNo)
                )
                .orElse(null);
    }

    // ============================================================
    // GET BY EMAIL
    // ============================================================

    @Override
    public Optional<Account> getByEmail(
            String email) {

        if (email == null ||
                email.isBlank()) {

            return Optional.empty();
        }

        return accountRepository
                .findByEmail(
                        email.trim()
                );
    }

    // ============================================================
    // SAVE
    // ============================================================

    @Override
    @Transactional
    public Account save(
            Account account) {

        if (account == null) {

            throw new IllegalArgumentException(
                    "Account cannot be null."
            );
        }

        return accountRepository.save(account);
    }

    // ============================================================
    // ALL ACCOUNTS
    // ============================================================

    @Override
    @Transactional
    public List<Account> getAllAccounts() {

        refreshAccountStatuses();

        return accountRepository.findAll();
    }

    // ============================================================
    // SEARCH NATIONAL ID
    // ============================================================

    @Override
    public List<Account> searchAccountsByNationalId(
            String nationalId) {

        if (nationalId == null ||
                nationalId.isBlank()) {

            return Collections.emptyList();
        }

        List<Account> accounts =
                accountRepository
                        .findByNationalIdContaining(
                                nationalId.trim()
                        );

        return accounts != null
                ? accounts
                : Collections.emptyList();
    }

    // ============================================================
    // ACCOUNT DTO BY NATIONAL ID
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public AccountDTO getAccountDTOByNationalId(
            String nationalId) {

        if (nationalId == null ||
                nationalId.isBlank()) {

            return null;
        }

        Account account =
                accountRepository
                        .findByNationalId(
                                nationalId.trim()
                        )
                        .orElse(null);

        return toDTO(account);
    }

    // ============================================================
    // PHONE EXISTS
    // ============================================================

    @Override
    public boolean existsByPhoneNumber(
            String phoneNumber) {

        if (phoneNumber == null ||
                phoneNumber.isBlank()) {

            return false;
        }

        return accountRepository
                .existsByPhoneNumber(
                        phoneNumber.trim()
                );
    }

    // ============================================================
    // PHONE VALIDATION
    // ============================================================

    private void validatePhoneNumber(
            String phoneNumber) {

        if (phoneNumber == null ||
                phoneNumber.trim().isEmpty()) {

            return;
        }

        phoneNumber =
                phoneNumber.trim();

        if (phoneNumber.matches(
                "^07\\d{8}$"
        )) {

            return;
        }

        if (phoneNumber.matches(
                "^2547\\d{8}$"
        )) {

            return;
        }

        throw new IllegalArgumentException(
                "Phone number must be in the format " +
                        "07XXXXXXXX or 2547XXXXXXXX."
        );
    }

    // ============================================================
    // BORROWED AMOUNT
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public Double getBorrowedAmount(
            String accountNumber) {

        if (accountNumber == null ||
                accountNumber.isBlank()) {

            return 0.0;
        }

        Optional<Account> optAccount =
                accountRepository
                        .findByAccountNumber(
                                accountNumber.trim()
                        );

        if (optAccount.isEmpty()) {

            return 0.0;
        }

        Account account =
                optAccount.get();

        if (loanRepository == null) {

            return 0.0;
        }

        List<Loan> allLoans =
                loanRepository.findByAccount(
                        account
                );

        if (allLoans == null ||
                allLoans.isEmpty()) {

            return 0.0;
        }

        return allLoans.stream()

                .filter(Objects::nonNull)

                .filter(loan ->
                        loan.getStatus() != null
                )

                .filter(loan ->
                        !"repaid".equalsIgnoreCase(
                                loan.getStatus()
                        )
                                &&
                                !"cleared".equalsIgnoreCase(
                                        loan.getStatus()
                                )
                )

                .mapToDouble(
                        loan ->
                                loan.getAmount() != null
                                        ? loan.getAmount()
                                        : 0.0
                )

                .sum();
    }

    // ============================================================
    // REFRESH ACCOUNT STATUS
    // ============================================================

    @Override
    @Transactional
    public void refreshAccountStatuses() {

        LocalDateTime now =
                LocalDateTime.now();

        List<Account> accounts =
                accountRepository.findAll();

        if (accounts == null ||
                accounts.isEmpty()) {

            return;
        }

        List<Account> changedAccounts =
                new ArrayList<>();

        for (Account account : accounts) {

            if (account == null) {
                continue;
            }

            if (CLOSED.equalsIgnoreCase(
                    account.getAccountStatus()
            )) {

                continue;
            }

            LocalDateTime referenceDate =
                    account.getLastTransactionDate() != null
                            ? account.getLastTransactionDate()
                            : account.getCreatedAt();

            if (referenceDate == null) {
                continue;
            }

            long months =
                    ChronoUnit.MONTHS.between(
                            referenceDate,
                            now
                    );

            int dormancyMonths =
                    account.getDormancyMonths() != null &&
                            account.getDormancyMonths() > 0
                            ? account.getDormancyMonths()
                            : DEFAULT_DORMANCY_MONTHS;

            String newStatus =
                    months >= dormancyMonths
                            ? DORMANT
                            : ACTIVE;

            if (!newStatus.equalsIgnoreCase(
                    account.getAccountStatus()
            )) {

                account.setAccountStatus(
                        newStatus
                );

                changedAccounts.add(
                        account
                );
            }
        }

        if (!changedAccounts.isEmpty()) {

            accountRepository.saveAll(
                    changedAccounts
            );
        }
    }

    // ============================================================
    // CLOSE ACCOUNT
    // ============================================================

    @Override
    @Transactional
    public void closeAccount(
            String accountNumber,
            String reason) {

        Account account =
                getByAccountNumber(
                        accountNumber
                );

        if (CLOSED.equalsIgnoreCase(
                account.getAccountStatus()
        )) {

            throw new IllegalStateException(
                    "Account is already closed."
            );
        }

        account.setAccountStatus(
                CLOSED
        );

        account.setClosedAt(
                LocalDateTime.now()
        );

        if (reason != null &&
                !reason.isBlank()) {

            account.setClosureReason(
                    reason.trim()
            );
        }

        accountRepository.save(account);

        logger.info(
                "Account {} closed.",
                account.getAccountNumber()
        );
    }

    // ============================================================
    // STATEMENTS
    // ============================================================

    @Override
    public List<Map<String, Object>> getStatements(
            String accountNumber) {

        if (accountNumber == null ||
                accountNumber.isBlank()) {

            return Collections.emptyList();
        }

        List<Map<String, Object>> statements =
                statementsStore.get(
                        accountNumber.trim()
                );

        if (statements == null) {

            return Collections.emptyList();
        }

        return new ArrayList<>(
                statements
        );
    }

    // ============================================================
    // SAVE STATEMENTS
    // ============================================================

    @Override
    public void saveStatements(
            String accountNumber,
            List<Map<String, Object>> transactions) {

        if (accountNumber == null ||
                accountNumber.isBlank() ||
                transactions == null) {

            return;
        }

        statementsStore.put(
                accountNumber.trim(),
                new ArrayList<>(transactions)
        );
    }

    // ============================================================
    // TRANSFER FUNDS
    // ============================================================

    @Override
    @Transactional
    public boolean transferFunds(
            String fromAccountNumber,
            String toAccountNumber,
            double amount) {

        if (amount <= 0) {

            throw new IllegalArgumentException(
                    "Invalid transfer amount."
            );
        }

        if (fromAccountNumber == null ||
                fromAccountNumber.isBlank() ||
                toAccountNumber == null ||
                toAccountNumber.isBlank()) {

            throw new IllegalArgumentException(
                    "Source and destination accounts are required."
            );
        }

        String sourceNumber =
                fromAccountNumber.trim();

        String destinationNumber =
                toAccountNumber.trim();

        Optional<Account> fromOpt =
                accountRepository
                        .findByAccountNumber(
                                sourceNumber
                        );

        Optional<Account> toOpt =
                accountRepository
                        .findByAccountNumber(
                                destinationNumber
                        );

        if (fromOpt.isEmpty() ||
                toOpt.isEmpty()) {

            return false;
        }

        Account sender =
                fromOpt.get();

        Account receiver =
                toOpt.get();

        if (sender.getId() != null &&
                sender.getId().equals(
                        receiver.getId()
                )) {

            throw new IllegalArgumentException(
                    "Source and destination accounts cannot be the same."
            );
        }

        if (CLOSED.equalsIgnoreCase(
                sender.getAccountStatus()
        )) {

            throw new IllegalStateException(
                    "Source account is closed."
            );
        }

        if (DORMANT.equalsIgnoreCase(
                sender.getAccountStatus()
        )) {

            throw new IllegalStateException(
                    "Source account is dormant."
            );
        }

        if (CLOSED.equalsIgnoreCase(
                receiver.getAccountStatus()
        )) {

            throw new IllegalStateException(
                    "Destination account is closed."
            );
        }

        if (DORMANT.equalsIgnoreCase(
                receiver.getAccountStatus()
        )) {

            throw new IllegalStateException(
                    "Destination account is dormant."
            );
        }

        double senderBalance =
                sender.getBalance() != null
                        ? sender.getBalance()
                        : 0.0;

        double receiverBalance =
                receiver.getBalance() != null
                        ? receiver.getBalance()
                        : 0.0;

        if (senderBalance < amount) {

            return false;
        }

        LocalDateTime now =
                LocalDateTime.now();

        sender.setBalance(
                senderBalance - amount
        );

        receiver.setBalance(
                receiverBalance + amount
        );

        sender.setLastTransactionDate(
                now
        );

        receiver.setLastTransactionDate(
                now
        );

        sender.setAccountStatus(
                ACTIVE
        );

        receiver.setAccountStatus(
                ACTIVE
        );

        accountRepository.save(sender);

        accountRepository.save(receiver);

        logger.info(
                "Transfer completed: {} -> {} amount={}",
                sourceNumber,
                destinationNumber,
                amount
        );

        return true;
    }

    // ============================================================
    // LOG ACCOUNT DOCUMENT
    // ============================================================

    private void logAccountDocument(
            Document document) {

        if (document == null) {
            return;
        }

        logger.info("=================================================");
        logger.info("ACCOUNT DOCUMENT SAVED");
        logger.info(
                "Document ID     = {}",
                document.getId()
        );
        logger.info(
                "Account ID      = {}",
                document.getAccount() != null
                        ? document.getAccount().getId()
                        : null
        );
        logger.info(
                "Account Number  = {}",
                document.getAccount() != null
                        ? document.getAccount().getAccountNumber()
                        : null
        );
        logger.info(
                "Document Type   = {}",
                document.getDocumentType()
        );
        logger.info(
                "Original Name   = {}",
                document.getOriginalFileName()
        );
        logger.info(
                "Stored Name     = {}",
                document.getFileName()
        );
        logger.info(
                "Database Path   = {}",
                document.getFilePath()
        );
        logger.info(
                "Database URL    = {}",
                document.getFileUrl()
        );
        logger.info(
                "Browser URL     = {}",
                buildDocumentUrl(
                        document.getFilePath()
                )
        );
        logger.info(
                "File Size       = {} bytes",
                document.getFileSize()
        );
        logger.info(
                "Active          = {}",
                document.getActive()
        );
        logger.info(
                "Verified        = {}",
                document.getVerified()
        );
        logger.info("=================================================");
    }

    // ============================================================
    // LOG ORGANIZATION DOCUMENT
    // ============================================================

    private void logOrganizationDocument(
            OrganizationDocument document) {

        if (document == null) {
            return;
        }

        logger.info("=================================================");
        logger.info("ORGANIZATION DOCUMENT SAVED");
        logger.info(
                "Document ID     = {}",
                document.getId()
        );
        logger.info(
                "Organization ID = {}",
                document.getOrganization() != null
                        ? document.getOrganization().getId()
                        : null
        );
        logger.info(
                "Document Type   = {}",
                document.getDocumentType()
        );
        logger.info(
                "Original Name   = {}",
                document.getOriginalName()
        );
        logger.info(
                "Stored Name     = {}",
                document.getStoredName()
        );
        logger.info(
                "Database Path   = {}",
                document.getFilePath()
        );
        logger.info(
                "Browser URL     = {}",
                buildDocumentUrl(
                        document.getFilePath()
                )
        );
        logger.info(
                "File Size       = {} bytes",
                document.getFileSize()
        );
        logger.info("=================================================");
    }

    // ============================================================
    // FILE STORAGE INFO
    // ============================================================

    private static class FileStorageInfo {

        private final String originalFileName;

        private final String storedFileName;

        private final String contentType;

        private final long fileSize;

        private final String filePath;

        private final String fileUrl;

        private FileStorageInfo(
                String originalFileName,
                String storedFileName,
                String contentType,
                long fileSize,
                String filePath,
                String fileUrl) {

            this.originalFileName =
                    originalFileName;

            this.storedFileName =
                    storedFileName;

            this.contentType =
                    contentType;

            this.fileSize =
                    fileSize;

            this.filePath =
                    filePath;

            this.fileUrl =
                    fileUrl;
        }
    }
}