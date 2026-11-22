package com.example.Credit.management.services;

import com.example.Credit.management.DTO.TenantRentChargeResponse;
import com.example.Credit.management.DTO.TenantRentPaymentRequest;
import com.example.Credit.management.DTO.TenantRentPaymentResponse;
import com.example.Credit.management.Model.*;
import com.example.Credit.management.Repository.*;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class TenantRentService {

    private static final List<TenantLease.LeaseStatus> BILLABLE_LEASE_STATUSES =
            List.of(
                    TenantLease.LeaseStatus.ACTIVE,
                    TenantLease.LeaseStatus.TERMINATED,
                    TenantLease.LeaseStatus.EXPIRED
            );

    private static final List<TenantRentCharge.ChargeStatus> OUTSTANDING_STATUSES =
            List.of(
                    TenantRentCharge.ChargeStatus.OPEN,
                    TenantRentCharge.ChargeStatus.PARTIALLY_PAID,
                    TenantRentCharge.ChargeStatus.OVERDUE
            );

    private final TenantRentChargeRepository chargeRepository;
    private final TenantRentPaymentRepository paymentRepository;
    private final TenantPaymentAllocationRepository allocationRepository;
    private final TenantLeaseRepository leaseRepository;
    private final UserRepository userRepository;

    public TenantRentService(
            TenantRentChargeRepository chargeRepository,
            TenantRentPaymentRepository paymentRepository,
            TenantPaymentAllocationRepository allocationRepository,
            TenantLeaseRepository leaseRepository,
            UserRepository userRepository) {
        this.chargeRepository = chargeRepository;
        this.paymentRepository = paymentRepository;
        this.allocationRepository = allocationRepository;
        this.leaseRepository = leaseRepository;
        this.userRepository = userRepository;
    }

    /*
     * Global generation method.
     * Called by the scheduled system process.
     */
    public int generateRentCharges() {

        LocalDate today = LocalDate.now();

        int generated = 0;

        List<TenantLease> leases =
                leaseRepository.findByStatusIn(BILLABLE_LEASE_STATUSES);

        for (TenantLease lease : leases) {
            generated += generateChargesForLease(lease, today);
        }

        chargeRepository.flush();

        // Global refresh is retained for the system scheduler.
        refreshOverdueStatuses(today);

        return generated;
    }

    /*
     * Generates charges for one lease belonging to the authenticated user.
     * Used when an agent activates an individual lease.
     */
    public int generateRentChargesForLease(
            Long leaseId,
            String authenticatedEmail) {

        User owner = getOwner(authenticatedEmail);

        TenantLease lease = leaseRepository
                .findByIdAndOwner_Id(leaseId, owner.getId())
                .orElseThrow(() ->
                        notFound("Lease not found in your portfolio."));

        if (!BILLABLE_LEASE_STATUSES.contains(lease.getStatus())) {
            throw badRequest(
                    "Rent charges can only be generated for an active or previously billable lease.");
        }

        int generated = generateChargesForLease(
                lease,
                LocalDate.now()
        );

        chargeRepository.flush();

        return generated;
    }

    /*
     * Shared generation logic.
     * This method processes only the supplied lease.
     */
    private int generateChargesForLease(
            TenantLease lease,
            LocalDate today) {

        YearMonth currentMonth = YearMonth.from(today);

        LocalDate billingEnd = lease.getEndDate();

        if (lease.getTerminatedAt() != null) {
            LocalDate terminationDate =
                    lease.getTerminatedAt().toLocalDate();

            if (terminationDate.isBefore(billingEnd)) {
                billingEnd = terminationDate;
            }
        }

        if (lease.getStartDate().isAfter(today)
                || billingEnd.isBefore(lease.getStartDate())) {
            return 0;
        }

        int generated = 0;

        YearMonth period = YearMonth.from(lease.getStartDate());

        while (!period.isAfter(currentMonth)
                && !period.atDay(1).isAfter(billingEnd)) {

            LocalDate periodStart = period.atDay(1);
            LocalDate periodEnd = period.atEndOfMonth();

            boolean overlapsLease =
                    !periodEnd.isBefore(lease.getStartDate())
                            && !periodStart.isAfter(billingEnd);

            if (overlapsLease
                    && !chargeRepository.existsByLease_IdAndPeriodStart(
                    lease.getId(),
                    periodStart)) {

                TenantRentCharge charge = new TenantRentCharge();

                charge.setLease(lease);
                charge.setOwner(lease.getOwner());
                charge.setPeriodStart(periodStart);
                charge.setPeriodEnd(periodEnd);

                charge.setDueDate(
                        period.atDay(Math.min(
                                lease.getRentDueDay(),
                                period.lengthOfMonth()
                        ))
                );

                charge.setAmount(lease.getMonthlyRent());
                charge.setPaidAmount(BigDecimal.ZERO);

                charge.setStatus(
                        charge.getDueDate().isBefore(today)
                                ? TenantRentCharge.ChargeStatus.OVERDUE
                                : TenantRentCharge.ChargeStatus.OPEN
                );

                TenantRentCharge savedCharge =
                        chargeRepository.saveAndFlush(charge);

                // Apply any unallocated advance payments.
                applyAvailableCredits(lease, savedCharge);

                generated++;
            }

            period = period.plusMonths(1);
        }

        return generated;
    }

    public int refreshOverdueStatuses(LocalDate today) {
        int changed = 0;

        for (TenantRentCharge charge : chargeRepository.findAll()) {
            if (charge.getPaidAmount().compareTo(charge.getAmount()) >= 0) {
                charge.setStatus(TenantRentCharge.ChargeStatus.PAID);
            } else if (charge.getDueDate().isBefore(today)) {
                charge.setStatus(TenantRentCharge.ChargeStatus.OVERDUE);
            } else if (charge.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
                charge.setStatus(TenantRentCharge.ChargeStatus.PARTIALLY_PAID);
            } else {
                charge.setStatus(TenantRentCharge.ChargeStatus.OPEN);
            }

            chargeRepository.save(charge);
            changed++;
        }

        return changed;
    }

    @Transactional(readOnly = true)
    public List<TenantRentChargeResponse> getCharges(String email) {
        User owner = getOwner(email);

        return chargeRepository.findByOwner_IdOrderByDueDateAsc(owner.getId())
                .stream()
                .map(this::toChargeResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TenantRentChargeResponse> getChargesForLease(
            Long leaseId, String email) {

        User owner = getOwner(email);

        // Confirm lease ownership before returning its charges.
        leaseRepository.findByIdAndOwner_Id(leaseId, owner.getId())
                .orElseThrow(() -> notFound("Lease not found in your portfolio."));

        return chargeRepository
                .findByOwner_IdAndLease_IdOrderByPeriodStartAsc(
                        owner.getId(), leaseId)
                .stream()
                .map(this::toChargeResponse)
                .toList();
    }

    public TenantRentPaymentResponse recordPayment(
            TenantRentPaymentRequest request,
            String email) {

        User owner = getOwner(email);

        if (request == null || request.leaseId() == null
                || request.amount() == null
                || request.paymentMethod() == null
                || request.paymentReference() == null
                || request.paymentReference().isBlank()) {
            throw badRequest("Lease, reference, amount and payment method are required.");
        }

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw badRequest("Payment amount must be greater than zero.");
        }

        String reference = request.paymentReference().trim();

        if (paymentRepository
                .existsByOwner_IdAndPaymentReferenceIgnoreCase(
                        owner.getId(), reference)) {
            throw conflict("This payment reference has already been recorded.");
        }

        TenantLease lease = leaseRepository
                .findByIdAndOwner_Id(request.leaseId(), owner.getId())
                .orElseThrow(() -> notFound("Lease not found in your portfolio."));

        if (lease.getStatus() == TenantLease.LeaseStatus.DRAFT) {
            throw badRequest("Payments cannot be allocated to a draft lease.");
        }


        List<TenantRentCharge> outstanding =
                chargeRepository.findOutstandingChargesForAllocation(
                        owner.getId(),
                        lease.getId(),
                        OUTSTANDING_STATUSES
                );

        TenantRentPayment payment = new TenantRentPayment();
        payment.setLease(lease);
        payment.setOwner(owner);
        payment.setPaymentReference(reference);
        payment.setAmount(request.amount());
        payment.setPaymentMethod(request.paymentMethod());
        payment.setStatus(TenantRentPayment.PaymentStatus.POSTED);
        payment.setReceivedAt(LocalDateTime.now());
        payment.setNotes(clean(request.notes()));

        try {
            paymentRepository.saveAndFlush(payment);

            BigDecimal remaining = request.amount();

            // Allocate against existing charges, oldest first.
            for (TenantRentCharge charge : outstanding) {
                if (remaining.signum() <= 0) {
                    break;
                }

                BigDecimal balance = charge.getBalance();

                if (balance.signum() <= 0) {
                    continue;
                }

                BigDecimal allocated = remaining.min(balance);

                TenantPaymentAllocation allocation =
                        new TenantPaymentAllocation();

                allocation.setPayment(payment);
                allocation.setCharge(charge);
                allocation.setAmount(allocated);

                allocationRepository.save(allocation);

                charge.setPaidAmount(
                        charge.getPaidAmount().add(allocated)
                );

                updateChargeStatus(charge);
                chargeRepository.save(charge);

                remaining = remaining.subtract(allocated);
            }

            /*
             * Do not reject a remaining amount.
             *
             * The unallocated portion remains credit represented by
             * the payment amount minus its recorded allocations.
             * Future rent charges will consume that credit.
             */
            chargeRepository.flush();
            allocationRepository.flush();

            return toPaymentResponse(payment);

        } catch (DataIntegrityViolationException ex) {
            throw conflict(
                    "The payment could not be saved. Check whether its reference has already been used."
            );
        }

    }


    private void applyAvailableCredits(
            TenantLease lease,
            TenantRentCharge charge) {

        if (lease == null || charge == null
                || charge.getOwner() == null
                || charge.getLease() == null
                || !charge.getOwner().getId().equals(lease.getOwner().getId())
                || !charge.getLease().getId().equals(lease.getId())) {
            throw conflict("Rent charge ownership does not match the lease.");
        }

        List<TenantRentPayment> payments =
                paymentRepository
                        .findByLease_IdAndOwner_IdAndStatusOrderByReceivedAtAsc(
                                lease.getId(),
                                lease.getOwner().getId(),
                                TenantRentPayment.PaymentStatus.POSTED
                        );

        for (TenantRentPayment payment : payments) {

            BigDecimal balance = charge.getBalance();

            if (balance.signum() <= 0) {
                break;
            }

            if (payment.getOwner() == null
                    || !payment.getOwner().getId().equals(lease.getOwner().getId())
                    || payment.getLease() == null
                    || !payment.getLease().getId().equals(lease.getId())) {
                throw conflict("Payment ownership does not match the lease.");
            }

            List<TenantPaymentAllocation> existingAllocations =
                    allocationRepository
                            .findByPayment_IdAndPayment_Owner_IdOrderByIdAsc(
                                    payment.getId(),
                                    lease.getOwner().getId()
                            );

            BigDecimal alreadyAllocated = existingAllocations.stream()
                    .map(TenantPaymentAllocation::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal availableCredit =
                    payment.getAmount().subtract(alreadyAllocated);

            if (availableCredit.signum() <= 0) {
                continue;
            }

            BigDecimal allocated = availableCredit.min(balance);

            TenantPaymentAllocation allocation =
                    new TenantPaymentAllocation();

            allocation.setPayment(payment);
            allocation.setCharge(charge);
            allocation.setAmount(allocated);

            allocationRepository.save(allocation);

            charge.setPaidAmount(
                    charge.getPaidAmount().add(allocated)
            );

            updateChargeStatus(charge);
            chargeRepository.save(charge);
        }
    }


    public TenantRentPaymentResponse reversePayment(
            Long paymentId,
            String reason,
            String email) {

        User owner = getOwner(email);

        if (reason == null || reason.isBlank()) {
            throw badRequest("A reason is required to reverse a payment.");
        }

        TenantRentPayment payment = paymentRepository
                .findForUpdate(paymentId, owner.getId())
                .orElseThrow(() ->
                        notFound("Payment not found in your portfolio."));

        if (payment.getStatus() == TenantRentPayment.PaymentStatus.REVERSED) {
            throw conflict("This payment has already been reversed.");
        }

        if (payment.getLease() == null
                || payment.getOwner() == null
                || !payment.getOwner().getId().equals(owner.getId())) {
            throw conflict("Payment ownership information is invalid.");
        }

        Long leaseId = payment.getLease().getId();

        List<TenantPaymentAllocation> allocations =
                allocationRepository
                        .findByPayment_IdAndPayment_Owner_IdOrderByIdAsc(
                                paymentId,
                                owner.getId()
                        );

        BigDecimal totalAllocated = BigDecimal.ZERO;

        for (TenantPaymentAllocation allocation : allocations) {

            if (allocation.getAmount() == null
                    || allocation.getAmount().signum() <= 0) {
                throw conflict("An invalid payment allocation was found.");
            }

            if (allocation.getCharge() == null
                    || allocation.getCharge().getLease() == null
                    || !allocation.getCharge().getLease().getId().equals(leaseId)) {
                throw conflict("Payment allocation does not belong to this lease.");
            }

            totalAllocated = totalAllocated.add(allocation.getAmount());

            TenantRentCharge charge = chargeRepository
                    .findByIdAndOwner_IdAndLease_Id(
                            allocation.getCharge().getId(),
                            owner.getId(),
                            leaseId
                    )
                    .orElseThrow(() ->
                            notFound("Allocated rent charge was not found in your portfolio."));

            BigDecimal restoredPaidAmount =
                    charge.getPaidAmount().subtract(allocation.getAmount());

            if (restoredPaidAmount.compareTo(BigDecimal.ZERO) < 0) {
                throw conflict("Reversal would create an invalid rent balance.");
            }

            charge.setPaidAmount(restoredPaidAmount);

            updateChargeStatus(charge);

            chargeRepository.save(charge);
        }

        if (totalAllocated.compareTo(payment.getAmount()) > 0) {
            throw conflict("Payment allocations exceed the original payment amount.");
        }

        payment.setStatus(TenantRentPayment.PaymentStatus.REVERSED);
        payment.setReversedAt(LocalDateTime.now());
        payment.setReversalReason(reason.trim());

        paymentRepository.saveAndFlush(payment);
        chargeRepository.flush();

        return toPaymentResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<TenantRentPaymentResponse> getPayments(String email) {
        User owner = getOwner(email);

        return paymentRepository.findByOwner_IdOrderByReceivedAtDesc(owner.getId())
                .stream()
                .map(this::toPaymentResponse)
                .toList();
    }


    @Transactional(readOnly = true)
    public List<TenantRentPaymentResponse> getPaymentsForLease(
            Long leaseId,
            String email) {

        User owner = getOwner(email);

        leaseRepository.findByIdAndOwner_Id(leaseId, owner.getId())
                .orElseThrow(() ->
                        notFound("Lease not found in your portfolio."));

        return paymentRepository
                .findByOwner_IdAndLease_IdOrderByReceivedAtDesc(
                        owner.getId(),
                        leaseId
                )
                .stream()
                .map(this::toPaymentResponse)
                .toList();
    }


    private void updateChargeStatus(TenantRentCharge charge) {
        if (charge.getPaidAmount().compareTo(charge.getAmount()) >= 0) {
            charge.setStatus(TenantRentCharge.ChargeStatus.PAID);
        } else if (charge.getDueDate().isBefore(LocalDate.now())) {
            charge.setStatus(TenantRentCharge.ChargeStatus.OVERDUE);
        } else if (charge.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            charge.setStatus(TenantRentCharge.ChargeStatus.PARTIALLY_PAID);
        } else {
            charge.setStatus(TenantRentCharge.ChargeStatus.OPEN);
        }
    }

    private TenantRentChargeResponse toChargeResponse(
            TenantRentCharge charge) {

        TenantLease lease = charge.getLease();
        TenantUnit unit = lease.getUnit();
        TenantProperty property = unit.getProperty();

        return new TenantRentChargeResponse(
                charge.getId(),
                lease.getId(),
                lease.getLeaseCode(),
                lease.getTenant().getId(),
                lease.getTenant().getFullName(),
                property.getId(),
                property.getName(),
                unit.getId(),
                unit.getUnitNumber(),
                charge.getPeriodStart(),
                charge.getPeriodEnd(),
                charge.getDueDate(),
                charge.getAmount(),
                charge.getPaidAmount(),
                charge.getBalance(),
                charge.getStatus()
        );
    }

    private TenantRentPaymentResponse toPaymentResponse(
            TenantRentPayment payment) {

        if (payment.getOwner() == null || payment.getLease() == null) {
            throw conflict("Payment ownership information is incomplete.");
        }

        List<TenantRentPaymentResponse.Allocation> allocations =
                allocationRepository
                        .findByPayment_IdAndPayment_Owner_IdOrderByIdAsc(
                                payment.getId(),
                                payment.getOwner().getId()
                        )
                        .stream()
                        .map(a -> {

                            if (a.getCharge() == null
                                    || a.getCharge().getOwner() == null
                                    || a.getCharge().getLease() == null
                                    || !a.getCharge().getOwner().getId()
                                    .equals(payment.getOwner().getId())
                                    || !a.getCharge().getLease().getId()
                                    .equals(payment.getLease().getId())) {
                                throw conflict(
                                        "Payment allocation ownership is inconsistent."
                                );
                            }

                            return new TenantRentPaymentResponse.Allocation(
                                    a.getCharge().getId(),
                                    YearMonth.from(
                                            a.getCharge().getPeriodStart()
                                    ).toString(),
                                    a.getAmount()
                            );
                        })
                        .toList();

        return new TenantRentPaymentResponse(
                payment.getId(),
                payment.getLease().getId(),
                payment.getLease().getLeaseCode(),
                payment.getLease().getTenant().getFullName(),
                payment.getPaymentReference(),
                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getStatus(),
                payment.getReceivedAt(),
                payment.getNotes(),
                allocations,
                payment.getReversedAt(),
                payment.getReversalReason()
        );
    }


    private User getOwner(String email) {

        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user could not be identified."
            );
        }

        User user = userRepository.findByEmail(email.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user was not found."
                ));

        if (user.getRole() != Role.TENANT_AGENT
                && user.getRole() != Role.ADMIN) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not authorized to access rental management."
            );
        }

        return user;
    }


    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}