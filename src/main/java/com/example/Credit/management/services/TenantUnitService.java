
package com.example.Credit.management.services;

import com.example.Credit.management.DTO.TenantUnitRequest;
import com.example.Credit.management.DTO.TenantUnitResponse;
import com.example.Credit.management.Model.TenantProperty;
import com.example.Credit.management.Model.TenantUnit;
import com.example.Credit.management.Model.User;
import com.example.Credit.management.Repository.TenantPropertyRepository;
import com.example.Credit.management.Repository.TenantUnitRepository;
import com.example.Credit.management.Repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class TenantUnitService {

    private final TenantUnitRepository tenantUnitRepository;
    private final TenantPropertyRepository tenantPropertyRepository;
    private final UserRepository userRepository;
    private final TenantUnitImageStorageService tenantUnitImageStorageService;

    public TenantUnitService(
            TenantUnitRepository tenantUnitRepository,
            TenantPropertyRepository tenantPropertyRepository,
            UserRepository userRepository,
            TenantUnitImageStorageService tenantUnitImageStorageService) {

        this.tenantUnitRepository = tenantUnitRepository;
        this.tenantPropertyRepository = tenantPropertyRepository;
        this.userRepository = userRepository;
        this.tenantUnitImageStorageService = tenantUnitImageStorageService;
    }

    // =========================================================
    // CREATE UNIT - WITHOUT IMAGE (BACKWARD COMPATIBILITY)
    // =========================================================

    @Transactional
    public TenantUnitResponse create(
            String email,
            Long propertyId,
            TenantUnitRequest request) {

        return create(email, propertyId, request, null);
    }

    // =========================================================
    // CREATE UNIT - WITH OPTIONAL IMAGE
    // =========================================================

    @Transactional
    public TenantUnitResponse create(
            String email,
            Long propertyId,
            TenantUnitRequest request,
            MultipartFile image) {

        User owner = getUser(email);

        TenantProperty property =
                findOwnedProperty(propertyId, owner.getId());

        String unitNumber = normalizeUnitNumber(request.unitNumber());

        validateAmounts(
                request.monthlyRent(),
                request.depositAmount()
        );

        if (tenantUnitRepository
                .existsByProperty_IdAndUnitNumberIgnoreCase(
                        propertyId, unitNumber)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This unit number already exists in the property."
            );
        }

        TenantUnit unit = new TenantUnit();

        unit.setProperty(property);
        unit.setUnitNumber(unitNumber);
        unit.setFloor(clean(request.floor()));
        unit.setUnitType(clean(request.unitType()));
        unit.setMonthlyRent(request.monthlyRent());
        unit.setDepositAmount(request.depositAmount());
        unit.setDescription(clean(request.description()));
        unit.setStatus(TenantUnit.UnitStatus.VACANT);

        // Photograph is optional.
        String imageUrl = tenantUnitImageStorageService.store(image);
        unit.setImageUrl(imageUrl);

        TenantUnit savedUnit = tenantUnitRepository.save(unit);

        return toResponse(savedUnit);
    }

    // =========================================================
    // LIST UNITS BY PROPERTY
    // =========================================================

    @Transactional(readOnly = true)
    public List<TenantUnitResponse> listByProperty(
            String email,
            Long propertyId) {

        User owner = getUser(email);

        findOwnedProperty(propertyId, owner.getId());

        return tenantUnitRepository
                .findByProperty_IdOrderByUnitNumberAsc(propertyId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // =========================================================
    // GET SINGLE UNIT
    // =========================================================

    @Transactional(readOnly = true)
    public TenantUnitResponse getById(
            String email,
            Long propertyId,
            Long unitId) {

        User owner = getUser(email);

        findOwnedProperty(propertyId, owner.getId());

        return toResponse(findUnit(unitId, propertyId));
    }

    // =========================================================
    // UPDATE UNIT - WITHOUT IMAGE (BACKWARD COMPATIBILITY)
    // =========================================================

    @Transactional
    public TenantUnitResponse update(
            String email,
            Long propertyId,
            Long unitId,
            TenantUnitRequest request) {

        return update(email, propertyId, unitId, request, null);
    }

    // =========================================================
    // UPDATE UNIT - WITH OPTIONAL IMAGE
    // =========================================================

    @Transactional
    public TenantUnitResponse update(
            String email,
            Long propertyId,
            Long unitId,
            TenantUnitRequest request,
            MultipartFile image) {

        User owner = getUser(email);

        findOwnedProperty(propertyId, owner.getId());

        TenantUnit unit = findUnit(unitId, propertyId);

        String unitNumber = normalizeUnitNumber(request.unitNumber());

        validateAmounts(
                request.monthlyRent(),
                request.depositAmount()
        );

        if (tenantUnitRepository
                .existsByProperty_IdAndUnitNumberIgnoreCaseAndIdNot(
                        propertyId, unitNumber, unitId)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Another unit in this property already uses that number."
            );
        }

        // Update normal unit information.
        unit.setUnitNumber(unitNumber);
        unit.setFloor(clean(request.floor()));
        unit.setUnitType(clean(request.unitType()));
        unit.setMonthlyRent(request.monthlyRent());
        unit.setDepositAmount(request.depositAmount());
        unit.setDescription(clean(request.description()));

        // Replace the photograph only when a new one is supplied.
        if (image != null && !image.isEmpty()) {

            String oldImageUrl = unit.getImageUrl();

            // Store the new image first.
            String newImageUrl =
                    tenantUnitImageStorageService.store(image);

            unit.setImageUrl(newImageUrl);

            TenantUnit savedUnit = tenantUnitRepository.save(unit);

            // Delete the old image only after the database save succeeds.
            // If no previous image exists, nothing needs deleting.
            if (oldImageUrl != null
                    && !oldImageUrl.isBlank()
                    && !oldImageUrl.equals(newImageUrl)) {

                try {
                    tenantUnitImageStorageService.delete(oldImageUrl);
                } catch (RuntimeException exception) {
                    // The new image and database update have succeeded.
                    // Log this for later cleanup rather than failing the update.
                    System.err.println(
                            "Unable to delete previous unit image: "
                                    + exception.getMessage()
                    );
                }
            }

            return toResponse(savedUnit);
        }

        // No replacement image: retain the existing image URL.
        return toResponse(tenantUnitRepository.save(unit));
    }

    // =========================================================
    // UPDATE UNIT STATUS
    // =========================================================

    @Transactional
    public TenantUnitResponse updateStatus(
            String email,
            Long propertyId,
            Long unitId,
            TenantUnit.UnitStatus status) {

        if (status == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unit status is required."
            );
        }

        User owner = getUser(email);

        findOwnedProperty(propertyId, owner.getId());

        TenantUnit unit = findUnit(unitId, propertyId);

        // Lease workflows control OCCUPIED status.
        if (status == TenantUnit.UnitStatus.OCCUPIED) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A unit can only be marked occupied through an active lease."
            );
        }

        if (status == TenantUnit.UnitStatus.VACANT
                && unit.getStatus() == TenantUnit.UnitStatus.OCCUPIED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "End the active lease before marking this unit vacant."
            );
        }

        unit.setStatus(status);

        return toResponse(tenantUnitRepository.save(unit));
    }

    // =========================================================
    // FIND AUTHENTICATED USER
    // =========================================================

    private User getUser(String email) {

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user was not found."
                ));
    }

    // =========================================================
    // FIND PROPERTY OWNED BY USER
    // =========================================================

    private TenantProperty findOwnedProperty(
            Long propertyId,
            Long ownerId) {

        return tenantPropertyRepository
                .findByIdAndOwner_Id(propertyId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Property was not found or does not belong to you."
                ));
    }

    // =========================================================
    // FIND UNIT UNDER PROPERTY
    // =========================================================

    private TenantUnit findUnit(
            Long unitId,
            Long propertyId) {

        return tenantUnitRepository
                .findByIdAndProperty_Id(unitId, propertyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Unit was not found in this property."
                ));
    }

    // =========================================================
    // VALIDATE RENT AND DEPOSIT
    // =========================================================

    private void validateAmounts(
            BigDecimal monthlyRent,
            BigDecimal depositAmount) {

        if (monthlyRent == null || monthlyRent.signum() <= 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Monthly rent must be greater than zero."
            );
        }

        if (depositAmount == null || depositAmount.signum() < 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Deposit amount cannot be negative."
            );
        }

        if (monthlyRent.scale() > 2 || depositAmount.scale() > 2) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rent and deposit can have at most two decimal places."
            );
        }
    }

    // =========================================================
    // NORMALIZE UNIT NUMBER
    // =========================================================

    private String normalizeUnitNumber(String unitNumber) {

        if (unitNumber == null || unitNumber.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unit number is required."
            );
        }

        return unitNumber.trim().toUpperCase(Locale.ROOT);
    }

    // =========================================================
    // CLEAN OPTIONAL TEXT FIELDS
    // =========================================================

    private String clean(String value) {

        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    // =========================================================
    // MAP ENTITY TO RESPONSE
    // =========================================================

    private TenantUnitResponse toResponse(TenantUnit unit) {

        TenantProperty property = unit.getProperty();

        return new TenantUnitResponse(
                unit.getId(),
                property.getId(),
                property.getPropertyCode(),
                property.getName(),
                unit.getUnitNumber(),
                unit.getFloor(),
                unit.getUnitType(),
                unit.getMonthlyRent(),
                unit.getDepositAmount(),
                unit.getStatus(),
                unit.getDescription(),
                unit.getImageUrl(),
                unit.getCreatedAt(),
                unit.getUpdatedAt()
        );
    }
}
