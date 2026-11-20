package com.example.Credit.management.controller;

import com.example.Credit.management.DTO.AccountDTO;
import com.example.Credit.management.DTO.CustomerProfileDTO;
import com.example.Credit.management.Model.*;
import com.example.Credit.management.Repository.AccountRepository;
import com.example.Credit.management.services.AccountService;
import com.example.Credit.management.services.AccountServiceImpl;
import com.example.Credit.management.services.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/accounts")
@CrossOrigin(origins = "*")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountServiceImpl accountServiceIMPL;

    @Autowired
    private AuthService authService;

    private final AccountRepository accountRepository;


    private static final Logger logger = LoggerFactory.getLogger(AccountController.class);

    public AccountController(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    // Search account by National ID
    @GetMapping("/search/{accountNumber}")
    public ResponseEntity<?> searchByNationalId(@PathVariable String accountNumber) {
        Account account = accountService.getByNationalId(accountNumber);
        if (account == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Account not found"));
        }
        return ResponseEntity.ok(accountService.toDTO(account));
    }

    @PostMapping(
            value = "/register",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> createAccount(

            // =====================================================
            // CUSTOMER CATEGORY
            // =====================================================

            @RequestParam CustomerCategory customerCategory,

            @RequestParam(required = false) String modeOfOperation,

            // =====================================================
            // INDIVIDUAL DETAILS
            // =====================================================

            @RequestParam(required = false) String fullName,

            @RequestParam(required = false) String nationalId,

            @RequestParam(required = false) String gender,

            @RequestParam(required = false)
            @DateTimeFormat(pattern="yyyy-MM-dd")
            LocalDate dateOfBirth,

            // =====================================================
            // CONTACT
            // =====================================================

            @RequestParam(required = false) String phoneNumber,

            @RequestParam(required = false) String email,



            @RequestParam(required = false) String residentialAddress,

            @RequestParam(required = false) String city,

            @RequestParam(required = false) String county,

            // =====================================================
            // EMPLOYMENT
            // =====================================================

            @RequestParam(required = false) String employmentStatus,

            @RequestParam(required = false) String employerName,

            @RequestParam(required = false) String businessType,

            @RequestParam(required = false) String businessLocation,

            @RequestParam(required = false) Double monthlyIncome,

            @RequestParam(required = false) Double monthlyExpenses,

            @RequestParam(required = false) String sourceOfFunds,

            // =====================================================
            // ACCOUNT
            // =====================================================

            @RequestParam(required = false) String accountType,

            // =====================================================
            // ORGANIZATION
            // =====================================================

            @RequestParam(required = false) String organizationName,

            @RequestParam(required = false) String registrationNumber,

            @RequestParam(required = false)
            @DateTimeFormat(pattern="yyyy-MM-dd")
            LocalDate registrationDate,

            @RequestParam(required = false) String kraPin,

            @RequestParam(required = false) String industry,

            @RequestParam(required = false) String website,

            @RequestParam(required = false) String postalAddress,

            @RequestParam(required = false) String country,

            @RequestParam(required = false) Double annualTurnover,

            @RequestParam(required = false) Double authorizedCapital,

            @RequestParam(required = false) Integer numberOfEmployees,

            @RequestParam(required = false) String operatingLicenseNumber,

            @RequestParam(required = false) String boardResolutionNumber,

            @RequestParam(required = false) String constitutionNumber,

            @RequestParam(required = false) String trustDeedNumber,

            @RequestParam(required = false) String certificateNumber,

            // =====================================================
            // NEXT OF KIN
            // =====================================================

            @RequestParam(required = false) String nextOfKinName,

            @RequestParam(required = false) String nextOfKinPhone,

            @RequestParam(required = false) String nextOfKinRelationship,

            // =====================================================
            // IMAGE
            // =====================================================

            @RequestParam(required = false)
            MultipartFile image
    ) {

        try {

            Account account = new Account();

            // =====================================================
            // CATEGORY
            // =====================================================

            account.setCustomerCategory(customerCategory);

            if(modeOfOperation != null){

                account.setModeOfOperation(
                        ModeOfOperation.valueOf(modeOfOperation)
                );

            }

            // =====================================================
            // INDIVIDUAL
            // =====================================================

            account.setFullName(fullName);

            account.setNationalId(nationalId);

            account.setDateOfBirth(dateOfBirth);

            account.setGender(gender);

            // =====================================================
            // CONTACT
            // =====================================================

            if (customerCategory == CustomerCategory.INDIVIDUAL) {

                account.setPhoneNumber(phoneNumber);
                account.setEmail(email);
                account.setResidentialAddress(residentialAddress);
                account.setCity(city);
                account.setCounty(county);

            }

            account.setResidentialAddress(residentialAddress);

            account.setCity(city);

            account.setCounty(county);

            // =====================================================
            // EMPLOYMENT
            // =====================================================

            account.setEmploymentStatus(employmentStatus);

            account.setEmployerName(employerName);

            account.setBusinessType(businessType);

            account.setBusinessLocation(businessLocation);

            account.setMonthlyIncome(monthlyIncome);

            account.setMonthlyExpenses(monthlyExpenses);

            account.setSourceOfFunds(sourceOfFunds);

            account.setAccountType(accountType);

            // =====================================================
            // ORGANIZATION
            // =====================================================

            account.setOrganizationName(organizationName);

            account.setRegistrationNumber(registrationNumber);

            account.setRegistrationDate(registrationDate);

            account.setKraPin(kraPin);

            account.setIndustry(industry);

            account.setWebsite(website);

            account.setPostalAddress(postalAddress);

            account.setCountry(country);

            account.setAnnualTurnover(annualTurnover);

            account.setAuthorizedCapital(authorizedCapital);

            account.setNumberOfEmployees(numberOfEmployees);

            account.setOperatingLicenseNumber(operatingLicenseNumber);

            account.setBoardResolutionNumber(boardResolutionNumber);

            account.setConstitutionNumber(constitutionNumber);

            account.setTrustDeedNumber(trustDeedNumber);

            account.setCertificateNumber(certificateNumber);

            // =====================================================
            // NEXT OF KIN
            // =====================================================

            account.setNextOfKinName(nextOfKinName);

            account.setNextOfKinPhone(nextOfKinPhone);

            account.setNextOfKinRelationship(nextOfKinRelationship);

            // =====================================================
            // IMAGE
            // =====================================================

            if(image != null && !image.isEmpty()){

                String uploadDir = "uploads/";

                File dir = new File(uploadDir);

                if(!dir.exists()){

                    dir.mkdirs();

                }

                String fileName =
                        UUID.randomUUID() + "_" +
                                image.getOriginalFilename();

                Path filePath =
                        Paths.get(uploadDir,fileName);

                Files.write(filePath,image.getBytes());

                account.setImageUrl(
                        filePath.toString().replace("\\","/")
                );

            }

            Account saved =
                    accountService.createAccount(account);

            return ResponseEntity.ok(Map.of(

                    "status","SUCCESS",

                    "message","Account opened successfully.",

                    "account",accountService.toDTO(saved)

            ));

        }

        catch(Exception ex){

            logger.error(ex.getMessage(),ex);

            return ResponseEntity.badRequest().body(

                    Map.of(

                            "status","FAILED",

                            "message",ex.getMessage()

                    )

            );

        }

    }

    // ============================================================
// UPLOAD ACCOUNT DOCUMENT
// ============================================================

    @PostMapping(
            value = "/{accountId}/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> uploadAccountDocument(

            @PathVariable Long accountId,

            @RequestParam("file")
            MultipartFile file,

            @RequestParam("documentType")
            String documentType,

            @RequestParam(required = false)
            String uploadedBy
    ) {

        try {

            // =====================================================
            // VALIDATE ACCOUNT ID
            // =====================================================

            if (accountId == null) {

                return ResponseEntity.badRequest().body(
                        Map.of(
                                "status", "FAILED",
                                "message", "Account ID is required."
                        )
                );
            }


            // =====================================================
            // VALIDATE FILE
            // =====================================================

            if (file == null || file.isEmpty()) {

                return ResponseEntity.badRequest().body(
                        Map.of(
                                "status", "FAILED",
                                "message", "Document file is required."
                        )
                );
            }


            // =====================================================
            // VALIDATE DOCUMENT TYPE
            // =====================================================

            if (documentType == null ||
                    documentType.isBlank()) {

                return ResponseEntity.badRequest().body(
                        Map.of(
                                "status", "FAILED",
                                "message", "Document type is required."
                        )
                );
            }


            // =====================================================
            // CONVERT DOCUMENT TYPE
            // =====================================================

            DocumentType type;

            try {

                type = DocumentType.valueOf(
                        documentType.trim().toUpperCase()
                );

            } catch (IllegalArgumentException ex) {

                return ResponseEntity.badRequest().body(
                        Map.of(
                                "status", "FAILED",
                                "message",
                                "Invalid document type: " + documentType
                        )
                );
            }


            // =====================================================
            // SAVE DOCUMENT
            // =====================================================

            Document savedDocument =
                    accountService.saveAccountDocument(
                            accountId,
                            file,
                            type,
                            uploadedBy
                    );


            // =====================================================
            // RETURN RESPONSE
            // =====================================================

            return ResponseEntity.ok(
                    Map.of(
                            "status", "SUCCESS",
                            "message", "Document uploaded successfully.",
                            "document", savedDocument
                    )
            );

        } catch (Exception ex) {

            logger.error(
                    "Failed to upload document for account {}",
                    accountId,
                    ex
            );

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                            Map.of(
                                    "status", "FAILED",
                                    "message",
                                    ex.getMessage() != null
                                            ? ex.getMessage()
                                            : "Failed to upload document."
                            )
                    );
        }
    }

    @PostMapping(
            value = "/{accountId}/organization-documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> uploadOrganizationDocument(

            @PathVariable Long accountId,

            @RequestParam("file")
            MultipartFile file,

            @RequestParam("documentType")
            String documentType
    ) {

        try {

            if (accountId == null) {

                return ResponseEntity.badRequest().body(
                        Map.of(
                                "status", "FAILED",
                                "message",
                                "Account ID is required."
                        )
                );
            }

            if (file == null || file.isEmpty()) {

                return ResponseEntity.badRequest().body(
                        Map.of(
                                "status", "FAILED",
                                "message",
                                "Document file is required."
                        )
                );
            }

            if (documentType == null ||
                    documentType.isBlank()) {

                return ResponseEntity.badRequest().body(
                        Map.of(
                                "status", "FAILED",
                                "message",
                                "Document type is required."
                        )
                );
            }

            DocumentType type;

            try {

                type = DocumentType.valueOf(
                        documentType
                                .trim()
                                .toUpperCase()
                );

            } catch (IllegalArgumentException ex) {

                return ResponseEntity.badRequest().body(
                        Map.of(
                                "status", "FAILED",
                                "message",
                                "Invalid document type: "
                                        + documentType
                        )
                );
            }

            OrganizationDocument savedDocument =
                    accountService
                            .saveOrganizationDocumentByAccountId(
                                    accountId,
                                    file,
                                    type
                            );

            return ResponseEntity.ok(
                    Map.of(
                            "status", "SUCCESS",
                            "message",
                            "Organization document uploaded successfully.",
                            "document",
                            savedDocument
                    )
            );

        } catch (Exception ex) {

            logger.error(
                    "Failed to upload organization document for account {}",
                    accountId,
                    ex
            );

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                            Map.of(
                                    "status", "FAILED",
                                    "message",
                                    ex.getMessage() != null
                                            ? ex.getMessage()
                                            : "Failed to upload organization document."
                            )
                    );
        }
    }

// ============================================================
// GET ORGANIZATION DOCUMENTS
// ============================================================

    @GetMapping(
            value = "/{accountId}/organization-documents",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> getOrganizationDocuments(
            @PathVariable Long accountId) {

        try {

            // ========================================================
            // VALIDATE ACCOUNT
            // ========================================================

            if (accountId == null) {

                return ResponseEntity.badRequest().body(
                        Map.of(
                                "status", "FAILED",
                                "message", "Account ID is required."
                        )
                );
            }

            // ========================================================
            // LOAD DOCUMENTS
            // ========================================================

            List<OrganizationDocument> documents =
                    accountServiceIMPL
                            .getOrganizationDocumentsByAccountId(
                                    accountId
                            );

            // ========================================================
            // CREATE UI-SAFE RESPONSE
            // ========================================================

            List<Map<String, Object>> documentList =
                    documents.stream()
                            .map(document -> {

                                Map<String, Object> item =
                                        new LinkedHashMap<>();

                                item.put(
                                        "id",
                                        document.getId()
                                );

                                item.put(
                                        "documentType",
                                        document.getDocumentType()
                                );

                                item.put(
                                        "originalName",
                                        document.getOriginalName()
                                );

                                item.put(
                                        "storedName",
                                        document.getStoredName()
                                );

                                item.put(
                                        "fileSize",
                                        document.getFileSize()
                                );

                                item.put(
                                        "uploadedAt",
                                        document.getUploadedAt()
                                );

                                // Browser-accessible URL
                                item.put(
                                        "viewUrl",
                                        "/api/accounts/" +
                                                accountId +
                                                "/organization-documents/" +
                                                document.getId() +
                                                "/view"
                                );

                                item.put(
                                        "downloadUrl",
                                        "/api/accounts/" +
                                                accountId +
                                                "/organization-documents/" +
                                                document.getId() +
                                                "/download"
                                );

                                return item;

                            })
                            .toList();

            // ========================================================
            // RESPONSE
            // ========================================================

            return ResponseEntity.ok(
                    Map.of(
                            "status", "SUCCESS",
                            "accountId", accountId,
                            "count", documentList.size(),
                            "documents", documentList
                    )
            );

        } catch (Exception ex) {

            logger.error(
                    "Failed to load organization documents " +
                            "for account {}",
                    accountId,
                    ex
            );

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(
                            Map.of(
                                    "status", "FAILED",
                                    "message",
                                    ex.getMessage() != null
                                            ? ex.getMessage()
                                            : "Failed to load organization documents."
                            )
                    );
        }
    }


    @GetMapping(
            value = "/{accountId}/organization-documents/{documentId}/view"
    )
    public ResponseEntity<Resource> viewOrganizationDocument(

            @PathVariable Long accountId,

            @PathVariable Long documentId
    ) {

        try {

            logger.info(
                    "================================================="
            );

            logger.info(
                    "VIEW ORGANIZATION DOCUMENT"
            );

            logger.info(
                    "ACCOUNT ID  = {}",
                    accountId
            );

            logger.info(
                    "DOCUMENT ID = {}",
                    documentId
            );


            // ========================================================
            // VALIDATION
            // ========================================================

            if (accountId == null || documentId == null) {

                return ResponseEntity
                        .badRequest()
                        .build();
            }


            // ========================================================
            // FIND DOCUMENT
            // ========================================================

            Optional<OrganizationDocument> optionalDocument =
                    accountService.getOrganizationDocumentById(
                            accountId,
                            documentId
                    );


            if (optionalDocument.isEmpty()) {

                logger.warn(
                        "Organization document not found. " +
                                "accountId={}, documentId={}",
                        accountId,
                        documentId
                );

                return ResponseEntity
                        .notFound()
                        .build();
            }


            // ========================================================
            // UNWRAP OPTIONAL
            // ========================================================

            OrganizationDocument document =
                    optionalDocument.get();


            // ========================================================
            // FILE PATH
            // ========================================================

            if (document.getFilePath() == null ||
                    document.getFilePath().isBlank()) {

                logger.error(
                        "Document has no file path. " +
                                "accountId={}, documentId={}",
                        accountId,
                        documentId
                );

                return ResponseEntity
                        .notFound()
                        .build();
            }


            Path path =
                    Paths.get(
                                    document.getFilePath()
                            )
                            .toAbsolutePath()
                            .normalize();


            logger.info(
                    "DOCUMENT FILE PATH = {}",
                    path
            );


            // ========================================================
            // CHECK FILE
            // ========================================================

            if (!Files.exists(path)) {

                logger.error(
                        "DOCUMENT FILE NOT FOUND: {}",
                        path
                );

                return ResponseEntity
                        .notFound()
                        .build();
            }


            // ========================================================
            // RESOURCE
            // ========================================================

            Resource resource =
                    new UrlResource(
                            path.toUri()
                    );


            if (!resource.exists() ||
                    !resource.isReadable()) {

                logger.error(
                        "DOCUMENT RESOURCE NOT READABLE: {}",
                        path
                );

                return ResponseEntity
                        .notFound()
                        .build();
            }


            // ========================================================
            // CONTENT TYPE
            // ========================================================

            String contentType =
                    Files.probeContentType(path);


            if (contentType == null) {

                contentType =
                        MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }


            // ========================================================
            // FILE NAME
            // ========================================================

            String originalName =
                    document.getOriginalName();


            if (originalName == null ||
                    originalName.isBlank()) {

                originalName =
                        document.getStoredName();
            }


            // ========================================================
            // RESPONSE
            // ========================================================

            return ResponseEntity
                    .ok()
                    .contentType(
                            MediaType.parseMediaType(
                                    contentType
                            )
                    )
                    .header(
                            "Content-Disposition",
                            "inline; filename=\"" +
                                    originalName +
                                    "\""
                    )
                    .body(resource);

        } catch (Exception ex) {

            logger.error(
                    "Failed to view organization document. " +
                            "accountId={}, documentId={}",
                    accountId,
                    documentId,
                    ex
            );

            return ResponseEntity
                    .status(
                            HttpStatus.INTERNAL_SERVER_ERROR
                    )
                    .build();
        }
    }


    @GetMapping(
            value = "/{accountId}/organization-documents/{documentId}/download"
    )
    public ResponseEntity<Resource> downloadOrganizationDocument(

            @PathVariable Long accountId,

            @PathVariable Long documentId
    ) {

        try {

            Optional<OrganizationDocument> optionalDocument =
                    accountService.getOrganizationDocumentById(
                            accountId,
                            documentId
                    );


            if (optionalDocument.isEmpty()) {

                return ResponseEntity
                        .notFound()
                        .build();
            }


            OrganizationDocument document =
                    optionalDocument.get();


            if (document.getFilePath() == null ||
                    document.getFilePath().isBlank()) {

                return ResponseEntity
                        .notFound()
                        .build();
            }


            Path path =
                    Paths.get(
                                    document.getFilePath()
                            )
                            .toAbsolutePath()
                            .normalize();


            if (!Files.exists(path)) {

                return ResponseEntity
                        .notFound()
                        .build();
            }


            Resource resource =
                    new UrlResource(
                            path.toUri()
                    );


            if (!resource.exists() ||
                    !resource.isReadable()) {

                return ResponseEntity
                        .notFound()
                        .build();
            }


            String contentType =
                    Files.probeContentType(path);


            if (contentType == null) {

                contentType =
                        MediaType.APPLICATION_OCTET_STREAM_VALUE;
            }


            String fileName =
                    document.getOriginalName();


            if (fileName == null ||
                    fileName.isBlank()) {

                fileName =
                        document.getStoredName();
            }


            return ResponseEntity
                    .ok()
                    .contentType(
                            MediaType.parseMediaType(
                                    contentType
                            )
                    )
                    .header(
                            "Content-Disposition",
                            "attachment; filename=\"" +
                                    fileName +
                                    "\""
                    )
                    .body(resource);

        } catch (Exception ex) {

            logger.error(
                    "Failed to download organization document. " +
                            "accountId={}, documentId={}",
                    accountId,
                    documentId,
                    ex
            );

            return ResponseEntity
                    .status(
                            HttpStatus.INTERNAL_SERVER_ERROR
                    )
                    .build();
        }
    }


    @PostMapping(
            value = "/{accountId}/organization-people",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> saveOrganizationPeople(

            @PathVariable Long accountId,

            @RequestBody List<OrganizationPerson> people
    ) {

        try {

            List<OrganizationPerson> savedPeople =
                    accountServiceIMPL.saveOrganizationPeople(
                            accountId,
                            people
                    );

            return ResponseEntity.ok(
                    Map.of(
                            "status", "SUCCESS",
                            "message",
                            "Organization officials saved successfully.",
                            "people",
                            savedPeople
                    )
            );

        } catch (Exception ex) {

            logger.error(
                    "Failed to save organization people for account {}",
                    accountId,
                    ex
            );

            return ResponseEntity
                    .badRequest()
                    .body(
                            Map.of(
                                    "status", "FAILED",
                                    "message",
                                    ex.getMessage() != null
                                            ? ex.getMessage()
                                            : "Failed to save organization people."
                            )
                    );
        }
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<AccountDTO>> getByStatus(
            @PathVariable String status) {

        accountService.refreshAccountStatuses();

        List<AccountDTO> accounts = accountRepository
                .findByAccountStatus(status.toUpperCase())
                .stream()
                .map(accountService::toDTO)
                .toList();

        return ResponseEntity.ok(accounts);
    }

    @GetMapping("/profile/{accountNumber}")
    public ResponseEntity<CustomerProfileDTO> profile(

            @PathVariable String accountNumber){

        Account account = accountService
                .getByAccountNumber(accountNumber);

        return ResponseEntity.ok(

                accountServiceIMPL.toCustomerProfile(account)

        );

    }

    // Get account by National ID (returns 404 if not found)
    @GetMapping("/by-id/{nationalId}")
    public ResponseEntity<?> getByNationalId(@PathVariable String nationalId) {
        Account account = accountService.getByNationalId(nationalId);
        if (account == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Account not found"));
        }
        return ResponseEntity.ok(accountService.toDTO(account));
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<?> getByEmail(@PathVariable String email) {

        Optional<Account> account = accountService.getByEmail(email);

        if (account.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Account not found"));
        }

        return ResponseEntity.ok(
                accountService.toDTO(account.get())
        );
    }

    @GetMapping("/phone/{phoneNumber}")
    public ResponseEntity<?> getByPhoneNumber(@PathVariable String phoneNumber) {

        List<AccountDTO> accounts = accountService.getAllAccounts()
                .stream()
                .filter(a -> phoneNumber.equals(a.getPhoneNumber()))
                .map(accountService::toDTO)
                .toList();

        if (accounts.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Account not found"));
        }

        return ResponseEntity.ok(accounts.get(0));
    }

    // Get all accounts
    @GetMapping
    public ResponseEntity<List<AccountDTO>> getAllAccounts() {
        List<Account> accounts = accountService.getAllAccounts();
        List<AccountDTO> dtoList = accounts.stream()
                .map(accountService::toDTO)
                .toList();
        return ResponseEntity.ok(dtoList);
    }

    @PutMapping("/{accountNumber}/close")
    public ResponseEntity<?> closeAccount(

            @PathVariable String accountNumber,

            @RequestBody Map<String,String> body) {

        accountService.closeAccount(

                accountNumber,

                body.get("reason")

        );

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Account closed successfully"
                )
        );
    }

    // Get account image
    @GetMapping("/image/{nationalId}")
    public ResponseEntity<Resource> getImage(@PathVariable String nationalId) throws IOException {
        Account account = accountService.getByNationalId(nationalId);
        if (account == null || account.getImageUrl() == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = Paths.get(account.getImageUrl());
        Resource resource = new UrlResource(path.toUri());

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(resource);
    }

    // Deposit into account
    @PostMapping("/deposit")
    public ResponseEntity<?> deposit(@RequestBody Map<String, Object> dto) {
        String accountNumber = (String) dto.get("accountNumber");
        Double amount = Double.valueOf(dto.get("amount").toString());

        Account account = accountService.getByAccountNumber(accountNumber);
        if (account == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Account not found"));
        }

        account.setBalance(account.getBalance() + amount);
        accountService.save(account);

        Map<String, Object> txn = Map.of(
                "date", LocalDateTime.now().toString(),
                "description", "Deposit",
                "credit", amount,
                "debit", 0,
                "balanceAfter", account.getBalance()
        );

        List<Map<String, Object>> statements = accountService.getStatements(accountNumber);
        statements.add(txn);
        accountService.saveStatements(accountNumber, statements);

        return ResponseEntity.ok(accountService.toDTO(account));
    }

    // Update user profile
    @PutMapping("/updateProfile")
    public ResponseEntity<?> updateProfile(@RequestBody User userRequest) {
        try {
            User updatedUser = authService.updateUserDetails(userRequest);
            return ResponseEntity.ok(updatedUser);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Failed to update profile", "message", e.getMessage()));
        }
    }
}