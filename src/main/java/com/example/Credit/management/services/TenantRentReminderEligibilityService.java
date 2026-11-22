
package com.example.Credit.management.services;

import com.example.Credit.management.DTO.RentReminderCandidateResponse;
import com.example.Credit.management.Model.Role;
import com.example.Credit.management.Model.TenantLease;
import com.example.Credit.management.Model.TenantRentCharge;
import com.example.Credit.management.Model.TenantRentPayment;
import com.example.Credit.management.Model.TenantPaymentAllocation;
import com.example.Credit.management.Model.TenantResident;
import com.example.Credit.management.Repository.TenantLeaseRepository;
import com.example.Credit.management.Repository.TenantRentChargeRepository;
import com.example.Credit.management.Repository.TenantRentPaymentRepository;
import com.example.Credit.management.Repository.TenantPaymentAllocationRepository;
import com.example.Credit.management.Repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class TenantRentReminderEligibilityService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final TenantLeaseRepository leaseRepository;
    private final TenantRentChargeRepository chargeRepository;
    private final TenantRentPaymentRepository paymentRepository;
    private final TenantPaymentAllocationRepository allocationRepository;
    private final UserRepository userRepository;

    public TenantRentReminderEligibilityService(
            TenantLeaseRepository leaseRepository,
            TenantRentChargeRepository chargeRepository,
            TenantRentPaymentRepository paymentRepository,
            TenantPaymentAllocationRepository allocationRepository,
            UserRepository userRepository
    ) {
        this.leaseRepository = leaseRepository;
        this.chargeRepository = chargeRepository;
        this.paymentRepository = paymentRepository;
        this.allocationRepository = allocationRepository;
        this.userRepository = userRepository;
    }

    /**
     * Returns tenants with rent due soon, due today, or overdue.
     *
     * TENANT_AGENT:
     * Only sees leases and tenants belonging to their own portfolio.
     *
     * ADMIN:
     * Can see eligible tenants across all tenant agents.
     *
     * Available categories:
     * ALL
     * DUE_SOON
     * DUE_TODAY
     * OVERDUE
     *
     * Available payment credit is applied logically against
     * the oldest outstanding rent charges first.
     * No payment allocations are modified.
     */
    @Transactional(readOnly = true)
    public List<RentReminderCandidateResponse> findEligible(
            String actor,
            String category,
            int daysAhead
    ) {

        if (actor == null || actor.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required."
            );
        }

        if (daysAhead < 0 || daysAhead > 365) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "daysAhead must be between 0 and 365."
            );
        }

        String normalizedCategory = category == null
                ? "ALL"
                : category.trim().toUpperCase(Locale.ROOT);

        if (!Set.of("ALL", "DUE_SOON", "DUE_TODAY", "OVERDUE")
                .contains(normalizedCategory)) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Category must be ALL, DUE_SOON, DUE_TODAY, or OVERDUE."
            );
        }

        var authenticatedUser = userRepository.findByEmail(actor.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user account was not found."
                ));

        Role role = authenticatedUser.getRole();

        boolean admin = role == Role.ADMIN;

        if (!admin && role != Role.TENANT_AGENT) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not authorized to access rental reminders."
            );
        }

        LocalDate today = LocalDate.now();
        LocalDate dueSoonLimit = today.plusDays(daysAhead);

        /*
         * ADMIN retrieves all active leases.
         * TENANT_AGENT retrieves only their own leases.
         */
        List<TenantLease> leases = admin
                ? leaseRepository.findByStatusIn(
                List.of(TenantLease.LeaseStatus.ACTIVE)
        )
                : leaseRepository.findByOwner_IdOrderByCreatedAtDesc(
                authenticatedUser.getId()
        );

        List<RentReminderCandidateResponse> results =
                new ArrayList<>();

        for (TenantLease lease : leases) {

            if (lease == null
                    || lease.getId() == null
                    || lease.getStatus() != TenantLease.LeaseStatus.ACTIVE) {
                continue;
            }

            /*
             * Every lease must have a valid tenant-agent owner.
             */
            if (lease.getOwner() == null
                    || lease.getOwner().getId() == null
                    || lease.getOwner().getRole() != Role.TENANT_AGENT) {
                continue;
            }

            Long leaseOwnerId = lease.getOwner().getId();

            /*
             * Extra ownership validation for tenant agents.
             */
            if (!admin
                    && !Objects.equals(
                    authenticatedUser.getId(),
                    leaseOwnerId
            )) {
                continue;
            }

            TenantResident tenant = lease.getTenant();

            if (tenant == null
                    || tenant.getId() == null
                    || tenant.getStatus()
                    != TenantResident.TenantStatus.ACTIVE
                    || tenant.getOwner() == null
                    || !Objects.equals(
                    tenant.getOwner().getId(),
                    leaseOwnerId
            )) {
                continue;
            }

            /*
             * Retrieve rent charges using the actual lease owner.
             */
            List<TenantRentCharge> charges =
                    chargeRepository
                            .findByOwner_IdAndLease_IdOrderByPeriodStartAsc(
                                    leaseOwnerId,
                                    lease.getId()
                            );

            List<TenantRentCharge> orderedCharges = charges.stream()
                    .filter(c -> c.getDueDate() != null)
                    .filter(c -> c.getAmount() != null)
                    .filter(c -> c.getPaidAmount() != null)
                    .sorted(
                            Comparator
                                    .comparing(TenantRentCharge::getDueDate)
                                    .thenComparing(TenantRentCharge::getId)
                    )
                    .toList();

            /*
             * Retrieve payments belonging to this exact owner and lease.
             */
            List<TenantRentPayment> leasePayments =
                    paymentRepository
                            .findByOwner_IdAndLease_IdOrderByReceivedAtDesc(
                                    leaseOwnerId,
                                    lease.getId()
                            );

            List<TenantRentPayment> postedPayments = leasePayments.stream()
                    .filter(p -> p.getStatus()
                            == TenantRentPayment.PaymentStatus.POSTED)
                    .toList();

            List<Long> postedPaymentIds = postedPayments.stream()
                    .map(TenantRentPayment::getId)
                    .filter(Objects::nonNull)
                    .toList();

            Map<Long, BigDecimal> allocatedByPayment =
                    new HashMap<>();

            if (!postedPaymentIds.isEmpty()) {

                List<TenantPaymentAllocation> allocations =
                        allocationRepository
                                .findByPayment_IdInOrderByIdAsc(
                                        postedPaymentIds
                                );

                for (TenantPaymentAllocation allocation : allocations) {

                    if (allocation.getPayment() == null
                            || allocation.getPayment().getId() == null
                            || allocation.getAmount() == null
                            || allocation.getAmount().signum() <= 0) {
                        continue;
                    }

                    var payment = allocation.getPayment();

                    /*
                     * Never use an allocation belonging to another
                     * owner or another lease.
                     */
                    if (payment.getOwner() == null
                            || payment.getLease() == null
                            || !Objects.equals(
                            payment.getOwner().getId(),
                            leaseOwnerId
                    )
                            || !Objects.equals(
                            payment.getLease().getId(),
                            lease.getId()
                    )) {

                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "A payment allocation has inconsistent ownership."
                        );
                    }

                    TenantRentCharge charge = allocation.getCharge();

                    if (charge == null
                            || charge.getOwner() == null
                            || charge.getLease() == null
                            || !Objects.equals(
                            charge.getOwner().getId(),
                            leaseOwnerId
                    )
                            || !Objects.equals(
                            charge.getLease().getId(),
                            lease.getId()
                    )) {

                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "A rent allocation references an inconsistent charge."
                        );
                    }

                    allocatedByPayment.merge(
                            payment.getId(),
                            allocation.getAmount(),
                            BigDecimal::add
                    );
                }
            }

            /*
             * Calculate unapplied payment credit.
             */
            BigDecimal unappliedCredit = ZERO;

            for (TenantRentPayment payment : postedPayments) {

                BigDecimal paymentAmount = payment.getAmount() == null
                        ? ZERO
                        : payment.getAmount();

                BigDecimal allocated = allocatedByPayment.getOrDefault(
                        payment.getId(),
                        ZERO
                );

                BigDecimal remaining = paymentAmount.subtract(allocated);

                if (remaining.signum() > 0) {
                    unappliedCredit = unappliedCredit.add(remaining);
                }
            }

            /*
             * Apply available credit to the oldest outstanding
             * rent charges before identifying reminder candidates.
             */
            for (TenantRentCharge charge : orderedCharges) {

                BigDecimal paidAmount = charge.getPaidAmount() == null
                        ? ZERO
                        : charge.getPaidAmount();

                BigDecimal balance = charge.getAmount()
                        .subtract(paidAmount);

                if (balance.signum() <= 0) {
                    continue;
                }

                BigDecimal creditApplied = balance.min(unappliedCredit);

                balance = balance.subtract(creditApplied);
                unappliedCredit = unappliedCredit.subtract(creditApplied);

                if (balance.signum() <= 0) {
                    continue;
                }

                LocalDate dueDate = charge.getDueDate();

                String reminderCategory;

                if (dueDate.isBefore(today)) {
                    reminderCategory = "OVERDUE";
                } else if (dueDate.isEqual(today)) {
                    reminderCategory = "DUE_TODAY";
                } else if (!dueDate.isAfter(dueSoonLimit)) {
                    reminderCategory = "DUE_SOON";
                } else {
                    continue;
                }

                if (!normalizedCategory.equals("ALL")
                        && !normalizedCategory.equals(reminderCategory)) {
                    continue;
                }

                results.add(new RentReminderCandidateResponse(
                        tenant.getId(),
                        tenant.getFullName(),
                        tenant.getPhoneNumber(),
                        tenant.getEmail(),
                        lease.getId(),
                        charge.getId(),
                        dueDate,
                        balance,
                        reminderCategory
                ));
            }
        }

        results.sort(
                Comparator
                        .comparing(
                                RentReminderCandidateResponse::dueDate
                        )
                        .thenComparing(
                                RentReminderCandidateResponse::tenantName,
                                Comparator.nullsLast(
                                        String.CASE_INSENSITIVE_ORDER
                                )
                        )
        );

        return results;
    }
}
