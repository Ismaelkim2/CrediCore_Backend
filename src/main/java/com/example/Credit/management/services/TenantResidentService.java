
package com.example.Credit.management.services;

import com.example.Credit.management.DTO.TenantRecipientResponse;
import com.example.Credit.management.DTO.TenantResidentRequest;
import com.example.Credit.management.DTO.TenantResidentResponse;
import com.example.Credit.management.Model.TenantResident;
import com.example.Credit.management.Model.User;
import com.example.Credit.management.Repository.TenantResidentRepository;
import com.example.Credit.management.Repository.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@Transactional
public class TenantResidentService {

    private static final Logger log =
            LoggerFactory.getLogger(TenantResidentService.class);

    private final TenantResidentRepository tenantResidentRepository;
    private final UserRepository userRepository;
    private final TenantResidentImageStorageService imageStorageService;

    // =========================================================
    // VALIDATION CONSTANTS
    // =========================================================

    /**
     * Kenyan National ID:
     * Exactly 8 or 9 numeric digits.
     */
    private static final Pattern KENYAN_NATIONAL_ID =
            Pattern.compile("^[0-9]{8,9}$");

    /**
     * Passport:
     * 5 to 40 uppercase letters, numbers or hyphens.
     */
    private static final Pattern PASSPORT_NUMBER =
            Pattern.compile("^[A-Z0-9-]{5,40}$");

    /**
     * Full names:
     * At least two name segments.
     * Supports Unicode letters, spaces, apostrophes and hyphens.
     */
    private static final Pattern FULL_NAME =
            Pattern.compile(
                    "^[\\p{L}]+(?:[ '\\-’][\\p{L}]+)+$"
            );

    /**
     * Kenyan mobile numbers:
     *
     * Local:
     * 0712345678
     * 0112345678
     *
     * International:
     * +254712345678
     * +254112345678
     */
    private static final Pattern KENYAN_PHONE =
            Pattern.compile(
                    "^(?:0[17][0-9]{8}|\\+254[17][0-9]{8})$"
            );

    /**
     * Basic email structure.
     * Additional checks are performed in validateEmail().
     */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile(
                    "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+"
                            + "@"
                            + "[A-Z0-9](?:[A-Z0-9-]*[A-Z0-9])?"
                            + "(?:\\.[A-Z0-9](?:[A-Z0-9-]*[A-Z0-9])?)+$",
                    Pattern.CASE_INSENSITIVE
            );

    private static final int MAX_NAME_LENGTH = 120;
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int MAX_ADDRESS_LENGTH = 250;
    private static final int MAX_NOTES_LENGTH = 2000;
    private static final int MAX_REMINDER_RECIPIENTS = 500;
    private static final int MAX_SEARCH_LENGTH = 100;

    // =========================================================
    // CONSTRUCTOR
    // =========================================================

    public TenantResidentService(
            TenantResidentRepository tenantResidentRepository,
            UserRepository userRepository,
            TenantResidentImageStorageService imageStorageService) {

        this.tenantResidentRepository = tenantResidentRepository;
        this.userRepository = userRepository;
        this.imageStorageService = imageStorageService;
    }

    // =========================================================
    // CREATE TENANT - WITHOUT PHOTOGRAPH
    // =========================================================

    public TenantResidentResponse create(
            TenantResidentRequest request,
            String authenticatedEmail) {

        return create(
                request,
                null,
                authenticatedEmail
        );
    }

    // =========================================================
    // CREATE TENANT - WITH OPTIONAL PHOTOGRAPH
    // =========================================================

    public TenantResidentResponse create(
            TenantResidentRequest request,
            MultipartFile image,
            String authenticatedEmail) {

        User owner = getAuthenticatedOwner(authenticatedEmail);

        // Always validate on the backend.
        NormalizedInput input = normalizeAndValidate(request);

        // Prevent duplicate identification records.
        validateNewIdentification(
                owner.getId(),
                input
        );

        TenantResident tenant = new TenantResident();

        applyInput(tenant, input);

        tenant.setOwner(owner);
        tenant.setStatus(TenantResident.TenantStatus.ACTIVE);

        String newImageUrl = null;

        try {

            // Photograph remains optional.
            if (image != null && !image.isEmpty()) {

                newImageUrl = imageStorageService.store(image);

                tenant.setImageUrl(newImageUrl);

                // Clean up uploaded image if transaction rolls back.
                scheduleDeleteIfTransactionRollsBack(newImageUrl);
            }

            TenantResident saved =
                    tenantResidentRepository.saveAndFlush(tenant);

            return toResponse(saved);

        } catch (DataIntegrityViolationException ex) {

            log.warn(
                    "Tenant creation failed because of a database integrity constraint. Owner ID: {}",
                    owner.getId()
            );

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tenant registration conflicts with an existing record. Check the identification details."
            );
        }
    }

    // =========================================================
    // GET ALL TENANTS FOR AUTHENTICATED OWNER
    // =========================================================

    @Transactional(readOnly = true)
    public List<TenantResidentResponse> getAll(
            String authenticatedEmail) {

        User owner = getAuthenticatedOwner(authenticatedEmail);

        return tenantResidentRepository
                .findByOwner_IdOrderByCreatedAtDesc(owner.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // =========================================================
    // GET SINGLE TENANT
    // =========================================================

    @Transactional(readOnly = true)
    public TenantResidentResponse getById(
            Long tenantId,
            String authenticatedEmail) {

        User owner = getAuthenticatedOwner(authenticatedEmail);

        TenantResident tenant =
                findOwnedTenant(tenantId, owner.getId());

        return toResponse(tenant);
    }

    // =========================================================
    // UPDATE TENANT - RETAIN EXISTING PHOTOGRAPH
    // =========================================================

    public TenantResidentResponse update(
            Long tenantId,
            TenantResidentRequest request,
            String authenticatedEmail) {

        return update(
                tenantId,
                request,
                null,
                authenticatedEmail
        );
    }

    // =========================================================
    // UPDATE TENANT - WITH OPTIONAL PHOTOGRAPH REPLACEMENT
    // =========================================================

    public TenantResidentResponse update(
            Long tenantId,
            TenantResidentRequest request,
            MultipartFile image,
            String authenticatedEmail) {

        User owner = getAuthenticatedOwner(authenticatedEmail);

        // Ensure the tenant belongs to the authenticated owner.
        TenantResident tenant =
                findOwnedTenant(tenantId, owner.getId());

        // Revalidate all submitted fields during editing.
        NormalizedInput input = normalizeAndValidate(request);

        validateUpdatedIdentification(
                owner.getId(),
                tenantId,
                input
        );

        String previousImageUrl = tenant.getImageUrl();

        String newImageUrl = null;

        applyInput(tenant, input);

        try {

            // Only replace the photograph if a new one is supplied.
            if (image != null && !image.isEmpty()) {

                newImageUrl = imageStorageService.store(image);

                tenant.setImageUrl(newImageUrl);

                // Delete new upload if database transaction rolls back.
                scheduleDeleteIfTransactionRollsBack(newImageUrl);
            }

            TenantResident saved =
                    tenantResidentRepository.saveAndFlush(tenant);

            // Delete previous photograph only after successful commit.
            if (newImageUrl != null
                    && previousImageUrl != null
                    && !previousImageUrl.equals(newImageUrl)) {

                scheduleDeleteAfterTransactionCommits(
                        previousImageUrl
                );
            }

            return toResponse(saved);

        } catch (DataIntegrityViolationException ex) {

            log.warn(
                    "Tenant update failed because of a database integrity constraint. Tenant ID: {}",
                    tenantId
            );

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Tenant update conflicts with an existing record. Check the identification details."
            );
        }
    }

    // =========================================================
    // UPDATE TENANT STATUS
    // =========================================================

    public TenantResidentResponse updateStatus(
            Long tenantId,
            TenantResident.TenantStatus status,
            String authenticatedEmail) {

        if (status == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tenant status is required."
            );
        }

        User owner = getAuthenticatedOwner(authenticatedEmail);

        TenantResident tenant =
                findOwnedTenant(tenantId, owner.getId());

        tenant.setStatus(status);

        return toResponse(
                tenantResidentRepository.save(tenant)
        );
    }

    // =========================================================
    // VALIDATE NEW IDENTIFICATION
    // =========================================================

    private void validateNewIdentification(
            Long ownerId,
            NormalizedInput input) {

        boolean duplicate =
                tenantResidentRepository
                        .existsByOwner_IdAndIdentificationTypeAndIdentificationNumber(
                                ownerId,
                                input.identificationType(),
                                input.identificationNumber()
                        );

        if (duplicate) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A tenant with this identification record already exists in your portfolio."
            );
        }
    }

    // =========================================================
    // VALIDATE UPDATED IDENTIFICATION
    // =========================================================

    private void validateUpdatedIdentification(
            Long ownerId,
            Long tenantId,
            NormalizedInput input) {

        boolean duplicate =
                tenantResidentRepository
                        .existsByOwner_IdAndIdentificationTypeAndIdentificationNumberAndIdNot(
                                ownerId,
                                input.identificationType(),
                                input.identificationNumber(),
                                tenantId
                        );

        if (duplicate) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Another tenant in your portfolio already uses this identification record."
            );
        }
    }

    // =========================================================
    // NORMALIZE AND VALIDATE ALL TENANT INPUT
    // =========================================================

    private NormalizedInput normalizeAndValidate(
            TenantResidentRequest request) {

        if (request == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tenant registration details are required."
            );
        }

        // =====================================================
        // IDENTIFICATION TYPE
        // =====================================================

        if (request.identificationType() == null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Identification type is required."
            );
        }

        // =====================================================
        // TENANT FULL NAME
        // =====================================================

        String fullName = requiredText(
                request.fullName(),
                "Tenant full name"
        ).replaceAll("\\s+", " ");

        validateFullName(
                fullName,
                "Tenant full name"
        );

        // =====================================================
        // IDENTIFICATION NUMBER
        // =====================================================

        String identificationNumber = requiredText(
                request.identificationNumber(),
                "Identification number"
        ).toUpperCase(Locale.ROOT);

        identificationNumber =
                identificationNumber.replaceAll("\\s+", "");

        if (request.identificationType()
                == TenantResident.IdentificationType.NATIONAL_ID) {

            if (!KENYAN_NATIONAL_ID.matcher(
                    identificationNumber).matches()) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Kenyan National ID must contain exactly 8 or 9 digits."
                );
            }

        } else if (request.identificationType()
                == TenantResident.IdentificationType.PASSPORT) {

            if (!PASSPORT_NUMBER.matcher(
                    identificationNumber).matches()) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Passport number must contain 5 to 40 letters, digits or hyphens."
                );
            }

        } else {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported identification type."
            );
        }

        // =====================================================
        // TENANT PHONE NUMBER
        // =====================================================

        String phoneNumber = normalizeKenyanPhone(
                request.phoneNumber(),
                "Tenant phone number"
        );

        // =====================================================
        // TENANT EMAIL ADDRESS
        // =====================================================

        String email = validateEmail(request.email());

        // =====================================================
        // EMERGENCY CONTACT NAME
        // =====================================================

        String emergencyContactName = requiredText(
                request.emergencyContactName(),
                "Emergency contact name"
        ).replaceAll("\\s+", " ");

        validateFullName(
                emergencyContactName,
                "Emergency contact name"
        );

        // =====================================================
        // EMERGENCY CONTACT PHONE
        // =====================================================

        String emergencyContactPhone = normalizeKenyanPhone(
                request.emergencyContactPhone(),
                "Emergency contact phone number"
        );

        // =====================================================
        // ADDRESS
        // =====================================================

        String address = optionalText(request.address());

        if (address != null
                && address.length() > MAX_ADDRESS_LENGTH) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Address cannot exceed 250 characters."
            );
        }

        // =====================================================
        // NOTES
        // =====================================================

        String notes = optionalText(request.notes());

        if (notes != null
                && notes.length() > MAX_NOTES_LENGTH) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Notes cannot exceed 2000 characters."
            );
        }

        return new NormalizedInput(
                fullName,
                request.identificationType(),
                identificationNumber,
                phoneNumber,
                email,
                address,
                emergencyContactName,
                emergencyContactPhone,
                notes
        );
    }

    // =========================================================
    // VALIDATE FULL NAME
    // =========================================================

    private void validateFullName(
            String fullName,
            String fieldName) {

        if (fullName == null
                || fullName.length() > MAX_NAME_LENGTH
                || !FULL_NAME.matcher(fullName).matches()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName
                            + " must contain at least two valid names. "
                            + "Use letters, spaces, apostrophes or hyphens only."
            );
        }
    }

    // =========================================================
    // VALIDATE AND NORMALIZE KENYAN PHONE NUMBER
    // =========================================================

    private String normalizeKenyanPhone(
            String value,
            String fieldName) {

        String phone = requiredText(value, fieldName);

        /*
         * Reject letters and other invalid characters.
         * Only digits, spaces, hyphens and a leading + are accepted.
         */
        if (!phone.matches("^[+]?[0-9\\s-]+$")) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName
                            + " must contain digits only, "
                            + "with an optional +254 country code."
            );
        }

        // Remove formatting characters.
        phone = phone.replaceAll("[\\s-]", "");

        // Convert local Kenyan mobile number into international format.
        if (phone.startsWith("0")) {

            phone = "+254" + phone.substring(1);
        }

        // Final strict validation.
        if (!KENYAN_PHONE.matcher(phone).matches()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName
                            + " must be a valid Kenyan mobile number. "
                            + "Examples: 0712345678 or +254712345678."
            );
        }

        return phone;
    }

    // =========================================================
    // VALIDATE EMAIL
    // =========================================================

    private String validateEmail(String value) {

        String email = optionalText(value);

        // Tenant email is optional.
        if (email == null) {
            return null;
        }

        email = email.toLowerCase(Locale.ROOT);

        if (email.length() > MAX_EMAIL_LENGTH
                || !EMAIL_PATTERN.matcher(email).matches()
                || email.contains("..")) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Enter a valid email address."
            );
        }

        String[] parts = email.split("@", -1);

        if (parts.length != 2
                || parts[0].startsWith(".")
                || parts[0].endsWith(".")) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Enter a valid email address."
            );
        }

        return email;
    }

    // =========================================================
    // REQUIRED TEXT
    // =========================================================

    private String requiredText(
            String value,
            String fieldName) {

        if (value == null || value.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " is required."
            );
        }

        return value.trim();
    }

    // =========================================================
    // OPTIONAL TEXT
    // =========================================================

    private String optionalText(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    // =========================================================
    // APPLY VALIDATED INPUT TO TENANT ENTITY
    // =========================================================

    private void applyInput(
            TenantResident tenant,
            NormalizedInput input) {

        tenant.setFullName(input.fullName());

        tenant.setIdentificationType(
                input.identificationType()
        );

        tenant.setIdentificationNumber(
                input.identificationNumber()
        );

        tenant.setPhoneNumber(
                input.phoneNumber()
        );

        tenant.setEmail(
                input.email()
        );

        tenant.setAddress(
                input.address()
        );

        tenant.setEmergencyContactName(
                input.emergencyContactName()
        );

        tenant.setEmergencyContactPhone(
                input.emergencyContactPhone()
        );

        tenant.setNotes(
                input.notes()
        );
    }

    // =========================================================
    // TENANT ENTITY TO RESPONSE DTO
    // =========================================================

    private TenantResidentResponse toResponse(
            TenantResident tenant) {

        return new TenantResidentResponse(
                tenant.getId(),
                tenant.getFullName(),
                tenant.getIdentificationType(),
                tenant.getIdentificationNumber(),
                tenant.getPhoneNumber(),
                tenant.getEmail(),
                tenant.getAddress(),
                tenant.getEmergencyContactName(),
                tenant.getEmergencyContactPhone(),
                tenant.getNotes(),
                tenant.getStatus(),
                tenant.getImageUrl(),
                tenant.getCreatedAt(),
                tenant.getUpdatedAt()
        );
    }

    // =========================================================
    // NORMALIZED INPUT RECORD
    // =========================================================

    private record NormalizedInput(

            String fullName,

            TenantResident.IdentificationType identificationType,

            String identificationNumber,

            String phoneNumber,

            String email,

            String address,

            String emergencyContactName,

            String emergencyContactPhone,

            String notes

    ) {}

    // =========================================================
    // IMAGE CLEANUP AFTER TRANSACTION ROLLBACK
    // =========================================================

    private void scheduleDeleteIfTransactionRollsBack(
            String imageUrl) {

        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {

            log.warn(
                    "Transaction synchronization unavailable. "
                            + "Unable to schedule cleanup for {}",
                    imageUrl
            );

            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCompletion(int status) {

                        if (status != STATUS_COMMITTED) {

                            safelyDeleteImage(
                                    imageUrl,
                                    "rollback cleanup"
                            );
                        }
                    }
                }
        );
    }

    // =========================================================
    // DELETE OLD IMAGE AFTER SUCCESSFUL TRANSACTION COMMIT
    // =========================================================

    private void scheduleDeleteAfterTransactionCommits(
            String oldImageUrl) {

        if (oldImageUrl == null || oldImageUrl.isBlank()) {
            return;
        }

        if (!TransactionSynchronizationManager
                .isSynchronizationActive()) {

            log.warn(
                    "Transaction synchronization unavailable. "
                            + "Old tenant image was retained: {}",
                    oldImageUrl
            );

            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {

                        safelyDeleteImage(
                                oldImageUrl,
                                "old image replacement"
                        );
                    }
                }
        );
    }

    // =========================================================
    // SAFE IMAGE DELETION
    // =========================================================

    private void safelyDeleteImage(
            String imageUrl,
            String operation) {

        try {

            imageStorageService.delete(imageUrl);

        } catch (RuntimeException exception) {

            // A cleanup failure must not undo a committed transaction.
            log.error(
                    "Tenant image {} failed for {}: {}",
                    operation,
                    imageUrl,
                    exception.getMessage()
            );
        }
    }

    // =========================================================
    // VALIDATE BULK REMINDER RECIPIENTS
    // =========================================================

    /**
     * Validates every selected tenant:
     *
     * 1. Tenant ID must be valid.
     * 2. Duplicate selections are prohibited.
     * 3. Tenant must belong to the authenticated owner.
     * 4. Tenant must be active.
     * 5. Maximum 500 recipients per request.
     *
     * Returns validated IDs in their original order.
     */

    @Transactional(readOnly = true)
    public List<Long> validateActiveOwnedTenantIds(
            List<Long> tenantIds,
            String authenticatedEmail) {

        User owner = getAuthenticatedOwner(authenticatedEmail);

        if (tenantIds == null || tenantIds.isEmpty()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Select at least one tenant."
            );
        }

        if (tenantIds.size() > MAX_REMINDER_RECIPIENTS) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A maximum of 500 tenants can be selected."
            );
        }

        Set<Long> uniqueIds = new HashSet<>();

        for (Long tenantId : tenantIds) {

            if (tenantId == null || tenantId <= 0) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Every selected tenant must have a valid positive ID."
                );
            }

            if (!uniqueIds.add(tenantId)) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Duplicate tenant selections are not allowed."
                );
            }

            TenantResident tenant =
                    findOwnedTenant(tenantId, owner.getId());

            if (tenant.getStatus()
                    != TenantResident.TenantStatus.ACTIVE) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Tenant ID " + tenantId
                                + " is inactive and cannot receive a new reminder."
                );
            }
        }

        return List.copyOf(tenantIds);
    }

    // =========================================================
    // GET AUTHENTICATED OWNER
    // =========================================================

    private User getAuthenticatedOwner(String email) {

        if (email == null || email.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user could not be identified."
            );
        }

        return userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user was not found."
                ));
    }

    // =========================================================
    // FIND TENANT WITH OWNERSHIP VALIDATION
    // =========================================================

    private TenantResident findOwnedTenant(
            Long tenantId,
            Long ownerId) {

        if (tenantId == null || tenantId <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid tenant ID is required."
            );
        }

        return tenantResidentRepository
                .findByIdAndOwner_Id(tenantId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Tenant was not found in your portfolio."
                ));
    }

    // =========================================================
    // SEARCH ELIGIBLE TENANTS FOR REMINDERS
    // =========================================================

    @Transactional(readOnly = true)
    public List<TenantRecipientResponse> searchEligibleTenants(
            String query,
            String authenticatedEmail) {

        String search = query == null
                ? ""
                : query.trim().toLowerCase(Locale.ROOT);

        if (search.length() > MAX_SEARCH_LENGTH) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Search query cannot exceed 100 characters."
            );
        }

        return getAll(authenticatedEmail)
                .stream()

                // Only active tenants can receive reminders.
                .filter(tenant ->
                        tenant.status()
                                == TenantResident.TenantStatus.ACTIVE
                )

                // Apply search criteria.
                .filter(tenant -> search.isEmpty()
                        || containsIgnoreCase(
                        tenant.fullName(), search)
                        || containsIgnoreCase(
                        tenant.phoneNumber(), search)
                        || containsIgnoreCase(
                        tenant.email(), search)
                        || containsIgnoreCase(
                        tenant.identificationNumber(), search)
                )

                .limit(50)

                .map(tenant -> new TenantRecipientResponse(
                        tenant.id(),
                        tenant.fullName(),
                        tenant.phoneNumber(),
                        tenant.email()
                ))

                .toList();
    }

    // =========================================================
    // CASE-INSENSITIVE SEARCH HELPER
    // =========================================================

    private boolean containsIgnoreCase(
            String value,
            String query) {

        return value != null
                && value.toLowerCase(Locale.ROOT).contains(query);
    }
}
