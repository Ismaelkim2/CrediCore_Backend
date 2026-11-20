
package com.example.Credit.management.services;

import com.example.Credit.management.DTO.TenantDashboardResponse;
import com.example.Credit.management.Model.TenantLease;
import com.example.Credit.management.Model.TenantPaymentAllocation;
import com.example.Credit.management.Model.TenantRentPayment;
import com.example.Credit.management.Model.TenantResident;
import com.example.Credit.management.Model.TenantUnit;
import com.example.Credit.management.Model.User;
import com.example.Credit.management.Repository.TenantLeaseRepository;
import com.example.Credit.management.Repository.TenantPaymentAllocationRepository;
import com.example.Credit.management.Repository.TenantPropertyRepository;
import com.example.Credit.management.Repository.TenantRentChargeRepository;
import com.example.Credit.management.Repository.TenantResidentRepository;
import com.example.Credit.management.Repository.TenantUnitRepository;
import com.example.Credit.management.Repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@Transactional(readOnly = true)
public class TenantDashboardService {

    private static final ZoneId KENYA_ZONE =
            ZoneId.of("Africa/Nairobi");

    private static final BigDecimal ONE_HUNDRED =
            BigDecimal.valueOf(100);

    private static final BigDecimal ZERO =
            BigDecimal.ZERO;

    private static final int PERCENTAGE_SCALE = 2;

    private final TenantPropertyRepository propertyRepository;
    private final TenantUnitRepository unitRepository;
    private final TenantResidentRepository residentRepository;
    private final TenantLeaseRepository leaseRepository;
    private final TenantRentChargeRepository chargeRepository;
    private final TenantPaymentAllocationRepository allocationRepository;
    private final UserRepository userRepository;

    public TenantDashboardService(
            TenantPropertyRepository propertyRepository,
            TenantUnitRepository unitRepository,
            TenantResidentRepository residentRepository,
            TenantLeaseRepository leaseRepository,
            TenantRentChargeRepository chargeRepository,
            TenantPaymentAllocationRepository allocationRepository,
            UserRepository userRepository) {

        this.propertyRepository = propertyRepository;
        this.unitRepository = unitRepository;
        this.residentRepository = residentRepository;
        this.leaseRepository = leaseRepository;
        this.chargeRepository = chargeRepository;
        this.allocationRepository = allocationRepository;
        this.userRepository = userRepository;
    }

    // =========================================================
    // TENANT DASHBOARD
    // =========================================================

    public TenantDashboardResponse getDashboard(
            String email,
            LocalDate from,
            LocalDate to) {

        // -----------------------------------------------------
        // VALIDATE AUTHENTICATED USER
        // -----------------------------------------------------

        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user could not be identified."
            );
        }

        User owner = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user was not found."
                ));

        Long ownerId = owner.getId();

        if (ownerId == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated account has no valid owner ID."
            );
        }

        // -----------------------------------------------------
        // VALIDATE SELECTED DATE RANGE
        // -----------------------------------------------------

        if (from == null || to == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Both start date and end date are required."
            );
        }

        if (from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Start date cannot be after end date."
            );
        }

        // The selected end date is inclusive.
        // Repository queries use an exclusive upper boundary.
        LocalDate toExclusive = to.plusDays(1);

        // -----------------------------------------------------
        // CURRENT DATE — AFRICA/NAIROBI
        // -----------------------------------------------------

        LocalDate today = LocalDate.now(KENYA_ZONE);

        // -----------------------------------------------------
        // PROPERTY STATISTICS — CURRENT SNAPSHOT
        // -----------------------------------------------------

        long totalProperties =
                propertyRepository.countByOwner_Id(ownerId);

        // -----------------------------------------------------
        // UNIT STATISTICS — CURRENT SNAPSHOT
        // -----------------------------------------------------

        long totalUnits =
                unitRepository.countUnitsForOwner(ownerId);

        long occupiedUnits =
                unitRepository.countUnitsForOwnerByStatus(
                        ownerId,
                        TenantUnit.UnitStatus.OCCUPIED
                );

        long vacantUnits =
                unitRepository.countUnitsForOwnerByStatus(
                        ownerId,
                        TenantUnit.UnitStatus.VACANT
                );

        // -----------------------------------------------------
        // TENANT STATISTICS — CURRENT SNAPSHOT
        // -----------------------------------------------------

        long activeTenants =
                residentRepository.countByOwner_IdAndStatus(
                        ownerId,
                        TenantResident.TenantStatus.ACTIVE
                );

        long activeLeases =
                leaseRepository.countByOwner_IdAndStatus(
                        ownerId,
                        TenantLease.LeaseStatus.ACTIVE
                );

        // -----------------------------------------------------
        // RENT CHARGED — SELECTED DATE RANGE
        //
        // Uses rent charges whose periodStart falls within
        // the selected date range.
        // -----------------------------------------------------

        BigDecimal rentCharged =
                safeAmount(
                        chargeRepository.sumRentChargedForPeriod(
                                ownerId,
                                from,
                                toExclusive
                        )
                );

        // -----------------------------------------------------
        // RENT COLLECTED — SELECTED DATE RANGE
        //
        // Counts allocations against rent charges belonging
        // to the selected date range.
        //
        // Only POSTED payments are included.
        //
        // Advance payments allocated to future rent periods
        // do not inflate the collection rate for this period.
        // -----------------------------------------------------

        BigDecimal rentCollected =
                safeAmount(
                        allocationRepository.sumAllocatedToChargesForPeriod(
                                ownerId,
                                TenantRentPayment.PaymentStatus.POSTED,
                                from,
                                toExclusive
                        )
                );

        // -----------------------------------------------------
        // OUTSTANDING RENT — CURRENT BALANCE
        // -----------------------------------------------------

        BigDecimal outstandingRent =
                safeAmount(
                        chargeRepository.sumOutstandingRent(ownerId)
                );

        // -----------------------------------------------------
        // OVERDUE ARREARS — CURRENT POSITION
        // -----------------------------------------------------

        BigDecimal overdueArrears =
                safeAmount(
                        chargeRepository.sumOverdueArrears(
                                ownerId,
                                today
                        )
                );

        long overdueCharges =
                chargeRepository.countOverdueCharges(
                        ownerId,
                        today
                );

        // -----------------------------------------------------
        // OCCUPANCY RATE — CURRENT SNAPSHOT
        // -----------------------------------------------------

        BigDecimal occupancyRate = percentage(
                BigDecimal.valueOf(Math.max(occupiedUnits, 0)),
                BigDecimal.valueOf(Math.max(totalUnits, 0))
        );

        // -----------------------------------------------------
        // COLLECTION RATE — SELECTED DATE RANGE
        //
        // Numerator: allocations against rent charges in
        // the selected period.
        //
        // Denominator: rent charged in the selected period.
        //
        // The displayed percentage is capped at 100%.
        // The actual rentCollected amount remains uncapped.
        // -----------------------------------------------------

        BigDecimal collectionRate = percentage(
                rentCollected,
                rentCharged
        );

        // -----------------------------------------------------
        // DASHBOARD RESPONSE
        // -----------------------------------------------------

        return new TenantDashboardResponse(
                totalProperties,
                totalUnits,
                occupiedUnits,
                vacantUnits,
                activeTenants,
                activeLeases,
                occupancyRate,
                rentCharged,
                rentCollected,
                outstandingRent,
                overdueArrears,
                overdueCharges,
                collectionRate
        );
    }

    // =========================================================
    // SAFE MONEY NORMALIZATION
    // =========================================================

    private BigDecimal safeAmount(BigDecimal amount) {

        if (amount == null || amount.compareTo(ZERO) < 0) {
            return ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    // =========================================================
    // SAFE PERCENTAGE CALCULATION
    // =========================================================

    private BigDecimal percentage(
            BigDecimal numerator,
            BigDecimal denominator) {

        BigDecimal safeDenominator = safeAmount(denominator);

        if (safeDenominator.compareTo(ZERO) <= 0) {
            return ZERO.setScale(
                    PERCENTAGE_SCALE,
                    RoundingMode.HALF_UP
            );
        }

        BigDecimal safeNumerator = safeAmount(numerator)
                .min(safeDenominator);

        return safeNumerator
                .multiply(ONE_HUNDRED)
                .divide(
                        safeDenominator,
                        PERCENTAGE_SCALE,
                        RoundingMode.HALF_UP
                );
    }
}
