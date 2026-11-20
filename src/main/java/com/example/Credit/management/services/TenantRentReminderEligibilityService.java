
package com.example.Credit.management.services;

import com.example.Credit.management.DTO.RentReminderCandidateResponse;
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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
     * category:
     * DUE_SOON  - due after today and within the look-ahead window
     * DUE_TODAY - due today
     * OVERDUE   - due before today
     *
     * Credit is applied logically against the oldest outstanding
     * charges first. No payment allocations are modified.
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

        if (daysAhead < 1 || daysAhead > 60) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "daysAhead must be between 1 and 60."
            );
        }

        String normalizedCategory = category == null
                ? "ALL"
                : category.trim().toUpperCase();

        if (!Set.of("ALL", "DUE_SOON", "DUE_TODAY", "OVERDUE")
                .contains(normalizedCategory)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Category must be ALL, DUE_SOON, DUE_TODAY, or OVERDUE."
            );
        }

        var owner = userRepository.findByEmail(actor)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated owner account was not found."
                ));

        Long ownerId = owner.getId();
        LocalDate today = LocalDate.now();
        LocalDate dueSoonLimit = today.plusDays(daysAhead);

        List<TenantLease> leases =
                leaseRepository.findByOwner_IdOrderByCreatedAtDesc(ownerId);

        List<TenantRentPayment> ownerPayments =
                paymentRepository.findByOwner_IdOrderByReceivedAtDesc(ownerId);

        Map<Long, List<TenantRentPayment>> paymentsByLease =
                ownerPayments.stream()
                        .filter(p -> p.getStatus()
                                == TenantRentPayment.PaymentStatus.POSTED)
                        .filter(p -> p.getLease() != null
                                && p.getLease().getId() != null)
                        .collect(Collectors.groupingBy(
                                p -> p.getLease().getId()
                        ));

        List<Long> postedPaymentIds = ownerPayments.stream()
                .filter(p -> p.getStatus()
                        == TenantRentPayment.PaymentStatus.POSTED)
                .map(TenantRentPayment::getId)
                .filter(Objects::nonNull)
                .toList();

        Map<Long, BigDecimal> allocatedByPayment = new HashMap<>();

        if (!postedPaymentIds.isEmpty()) {
            List<TenantPaymentAllocation> allocations =
                    allocationRepository.findByPayment_IdInOrderByIdAsc(
                            postedPaymentIds
                    );


            for (TenantPaymentAllocation allocation : allocations) {
                if (allocation.getPayment() == null
                        || allocation.getPayment().getId() == null
                        || allocation.getAmount() == null
                        || allocation.getAmount().signum() <= 0) {
                    continue;
                }

                Long paymentId = allocation.getPayment().getId();

                allocatedByPayment.merge(
                        paymentId,
                        allocation.getAmount(),
                        BigDecimal::add
                );
            }

        }

        List<RentReminderCandidateResponse> results =
                new ArrayList<>();

        for (TenantLease lease : leases) {
            if (lease.getStatus() != TenantLease.LeaseStatus.ACTIVE) {
                continue;
            }

            TenantResident tenant = lease.getTenant();

            if (tenant == null
                    || tenant.getId() == null
                    || tenant.getStatus()
                    != TenantResident.TenantStatus.ACTIVE) {
                continue;
            }

            List<TenantRentCharge> charges =
                    chargeRepository
                            .findByOwner_IdAndLease_IdOrderByPeriodStartAsc(
                                    ownerId,
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

            List<TenantRentPayment> leasePayments =
                    paymentsByLease.getOrDefault(
                            lease.getId(),
                            List.of()
                    );

            BigDecimal unappliedCredit = ZERO;

            for (TenantRentPayment payment : leasePayments) {
                BigDecimal allocated = allocatedByPayment.getOrDefault(
                        payment.getId(),
                        ZERO
                );

                BigDecimal remaining = payment.getAmount()
                        .subtract(allocated);

                if (remaining.signum() > 0) {
                    unappliedCredit = unappliedCredit.add(remaining);
                }
            }

            for (TenantRentCharge charge : orderedCharges) {
                BigDecimal paidAmount = charge.getPaidAmount() == null
                        ? ZERO
                        : charge.getPaidAmount();

                BigDecimal balance = charge.getAmount()
                        .subtract(paidAmount);

                if (balance.signum() <= 0) {
                    continue;
                }

                // Apply available credit to the oldest balance first.
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
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)
                        )
        );

        return results;
    }
}
