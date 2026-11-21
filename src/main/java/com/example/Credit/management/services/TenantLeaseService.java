
package com.example.Credit.management.services;

import com.example.Credit.management.DTO.TenantLeaseRequest;
import com.example.Credit.management.DTO.TenantLeaseResponse;
import com.example.Credit.management.Model.TenantLease;
import com.example.Credit.management.Model.TenantProperty;
import com.example.Credit.management.Model.TenantResident;
import com.example.Credit.management.Model.TenantUnit;
import com.example.Credit.management.Model.User;
import com.example.Credit.management.Repository.TenantLeaseRepository;
import com.example.Credit.management.Repository.TenantPropertyRepository;
import com.example.Credit.management.Repository.TenantResidentRepository;
import com.example.Credit.management.Repository.TenantUnitRepository;
import com.example.Credit.management.Repository.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class TenantLeaseService {

    private static final List<TenantLease.LeaseStatus> BLOCKING_STATUSES =
            List.of(
                    TenantLease.LeaseStatus.DRAFT,
                    TenantLease.LeaseStatus.ACTIVE
            );

    private final TenantLeaseRepository leaseRepository;
    private final TenantResidentRepository tenantRepository;
    private final TenantUnitRepository unitRepository;
    private final TenantPropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final TenantRentService rentService;

    public TenantLeaseService(
            TenantLeaseRepository leaseRepository,
            TenantResidentRepository tenantRepository,
            TenantUnitRepository unitRepository,
            TenantPropertyRepository propertyRepository,
            UserRepository userRepository,
            TenantRentService rentService) {

        this.leaseRepository = leaseRepository;
        this.tenantRepository = tenantRepository;
        this.unitRepository = unitRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.rentService = rentService;
    }

    public TenantLeaseResponse create(
            TenantLeaseRequest request,
            String authenticatedEmail) {

        User owner = getOwner(authenticatedEmail);
        validateRequest(request);

        TenantResident tenant = tenantRepository
                .findByIdAndOwner_Id(request.tenantId(), owner.getId())
                .orElseThrow(() ->
                        notFound("Tenant not found in your portfolio."));

        if (tenant.getStatus() != TenantResident.TenantStatus.ACTIVE) {
            throw badRequest(
                    "An inactive tenant cannot be assigned a new lease.");
        }

        TenantUnit unit = unitRepository
                .findUnitForLeaseUpdate(request.unitId())
                .orElseThrow(() -> notFound("Unit not found."));

        propertyRepository
                .findByIdAndOwner_Id(
                        unit.getProperty().getId(),
                        owner.getId())
                .orElseThrow(() ->
                        notFound("Unit not found in your portfolio."));

        if (unit.getStatus() != TenantUnit.UnitStatus.VACANT) {
            throw conflict("Only vacant units can be assigned a new lease.");
        }

        ensureNoOverlappingLease(
                unit.getId(),
                request.startDate(),
                request.endDate(),
                null
        );

        TenantLease lease = new TenantLease();

        lease.setLeaseCode(
                "LEASE-" + UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase()
        );

        lease.setTenant(tenant);
        lease.setUnit(unit);
        lease.setOwner(owner);
        lease.setStartDate(request.startDate());
        lease.setEndDate(request.endDate());
        lease.setMonthlyRent(request.monthlyRent());
        lease.setDepositAmount(request.depositAmount());
        lease.setRentDueDay(request.rentDueDay());
        lease.setTerms(clean(request.terms()));
        lease.setStatus(TenantLease.LeaseStatus.DRAFT);
        lease.setActiveUnitId(null);

        try {
            return toResponse(leaseRepository.saveAndFlush(lease));
        } catch (DataIntegrityViolationException ex) {
            throw conflict(
                    "The lease could not be saved because of a duplicate or conflicting record.");
        }
    }

    @Transactional(readOnly = true)
    public List<TenantLeaseResponse> getAll(String authenticatedEmail) {

        User owner = getOwner(authenticatedEmail);

        return leaseRepository
                .findByOwner_IdOrderByCreatedAtDesc(owner.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TenantLeaseResponse getById(
            Long leaseId,
            String authenticatedEmail) {

        User owner = getOwner(authenticatedEmail);

        return toResponse(findOwnedLease(leaseId, owner.getId()));
    }

    public TenantLeaseResponse activate(
            Long leaseId,
            String authenticatedEmail) {

        User owner = getOwner(authenticatedEmail);
        TenantLease lease = findOwnedLease(leaseId, owner.getId());

        if (lease.getStatus() != TenantLease.LeaseStatus.DRAFT) {
            throw conflict("Only draft leases can be activated.");
        }

        LocalDate today = LocalDate.now();

        if (lease.getStartDate().isAfter(today)) {
            throw badRequest(
                    "A future lease cannot be activated before its start date.");
        }

        if (lease.getEndDate().isBefore(today)) {
            throw badRequest("This lease has already passed its end date.");
        }

        // Lock the unit row to serialize concurrent activation requests.
        TenantUnit unit = unitRepository
                .findUnitForLeaseUpdate(lease.getUnit().getId())
                .orElseThrow(() -> notFound("Unit not found."));

        if (unit.getStatus() != TenantUnit.UnitStatus.VACANT) {
            throw conflict("This unit is no longer vacant.");
        }

        ensureNoOverlappingLease(
                unit.getId(),
                lease.getStartDate(),
                lease.getEndDate(),
                lease.getId()
        );

        lease.setStatus(TenantLease.LeaseStatus.ACTIVE);
        lease.setActiveUnitId(unit.getId());

        unit.setStatus(TenantUnit.UnitStatus.OCCUPIED);

        try {
            leaseRepository.saveAndFlush(lease);
            unitRepository.saveAndFlush(unit);

        } catch (DataIntegrityViolationException ex) {
            throw conflict("This unit already has an active lease.");
        }

        /*
         * Generate rent charges immediately after successful activation.
         *
         * This runs in the same transaction.
         * Existing charges are protected by the lease-period
         * duplicate check in TenantRentService.
         *
         * Available advance payments are also applied to
         * newly generated rent charges.
         */
        rentService.generateRentCharges();

        return toResponse(lease);
    }

    public TenantLeaseResponse terminate(
            Long leaseId,
            String authenticatedEmail) {

        User owner = getOwner(authenticatedEmail);
        TenantLease lease = findOwnedLease(leaseId, owner.getId());

        if (lease.getStatus() != TenantLease.LeaseStatus.ACTIVE) {
            throw conflict("Only active leases can be terminated.");
        }

        TenantUnit unit = unitRepository
                .findUnitForLeaseUpdate(lease.getUnit().getId())
                .orElseThrow(() -> notFound("Unit not found."));

        lease.setStatus(TenantLease.LeaseStatus.TERMINATED);
        lease.setActiveUnitId(null);
        lease.setTerminatedAt(java.time.LocalDateTime.now());

        // Do not silently override a maintenance/inactive status.
        if (unit.getStatus() == TenantUnit.UnitStatus.OCCUPIED) {
            unit.setStatus(TenantUnit.UnitStatus.VACANT);
        }

        leaseRepository.saveAndFlush(lease);
        unitRepository.saveAndFlush(unit);

        return toResponse(lease);
    }

    private void validateRequest(TenantLeaseRequest request) {

        if (request == null) {
            throw badRequest("Lease details are required.");
        }

        if (request.tenantId() == null || request.unitId() == null) {
            throw badRequest("Tenant and unit are required.");
        }

        if (request.startDate() == null || request.endDate() == null) {
            throw badRequest("Lease start and end dates are required.");
        }

        if (request.endDate().isBefore(request.startDate())) {
            throw badRequest(
                    "Lease end date must be on or after the start date.");
        }

        if (request.monthlyRent() == null
                || request.monthlyRent().compareTo(BigDecimal.ZERO) <= 0) {
            throw badRequest("Monthly rent must be greater than zero.");
        }

        if (request.depositAmount() == null
                || request.depositAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw badRequest("Deposit cannot be negative.");
        }

        if (request.rentDueDay() == null
                || request.rentDueDay() < 1
                || request.rentDueDay() > 28) {
            throw badRequest("Rent due day must be between 1 and 28.");
        }
    }

    private void ensureNoOverlappingLease(
            Long unitId,
            LocalDate startDate,
            LocalDate endDate,
            Long excludedLeaseId) {

        boolean overlap = excludedLeaseId == null
                ? leaseRepository
                .existsByUnit_IdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        unitId,
                        BLOCKING_STATUSES,
                        endDate,
                        startDate)
                : leaseRepository
                .existsByUnit_IdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqualAndIdNot(
                        unitId,
                        BLOCKING_STATUSES,
                        endDate,
                        startDate,
                        excludedLeaseId);

        if (overlap) {
            throw conflict(
                    "This unit already has a draft or active lease overlapping those dates.");
        }
    }

    private User getOwner(String email) {

        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user could not be identified.");
        }

        return userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user was not found."));
    }

    private TenantLease findOwnedLease(Long leaseId, Long ownerId) {

        return leaseRepository.findByIdAndOwner_Id(leaseId, ownerId)
                .orElseThrow(() ->
                        notFound("Lease not found in your portfolio."));
    }

    private TenantLeaseResponse toResponse(TenantLease lease) {

        TenantUnit unit = lease.getUnit();

        return new TenantLeaseResponse(
                lease.getId(),
                lease.getLeaseCode(),
                lease.getTenant().getId(),
                lease.getTenant().getFullName(),
                unit.getProperty().getId(),
                unit.getProperty().getName(),
                unit.getId(),
                unit.getUnitNumber(),
                lease.getStartDate(),
                lease.getEndDate(),
                lease.getMonthlyRent(),
                lease.getDepositAmount(),
                lease.getRentDueDay(),
                lease.getStatus(),
                lease.getTerms(),
                lease.getTerminatedAt(),
                lease.getCreatedAt(),
                lease.getUpdatedAt()
        );
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                message);
    }
}
