
package com.example.Credit.management.services;

import com.example.Credit.management.DTO.TenantAccountSummaryResponse;
import com.example.Credit.management.DTO.TenantRentStatementResponse;
import com.example.Credit.management.Model.TenantLease;
import com.example.Credit.management.Model.TenantPaymentAllocation;
import com.example.Credit.management.Model.TenantRentCharge;
import com.example.Credit.management.Model.TenantRentPayment;
import com.example.Credit.management.Model.User;
import com.example.Credit.management.Repository.TenantLeaseRepository;
import com.example.Credit.management.Repository.TenantPaymentAllocationRepository;
import com.example.Credit.management.Repository.TenantRentChargeRepository;
import com.example.Credit.management.Repository.TenantRentPaymentRepository;
import com.example.Credit.management.Repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class TenantAccountService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final TenantLeaseRepository leaseRepository;
    private final TenantRentChargeRepository chargeRepository;
    private final TenantRentPaymentRepository paymentRepository;
    private final TenantPaymentAllocationRepository allocationRepository;
    private final UserRepository userRepository;

    public TenantAccountService(
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

    // =========================================================
    // TENANT ACCOUNT REGISTER
    // =========================================================

    public List<TenantAccountSummaryResponse> getAccounts(String email) {

        User owner = getOwner(email);

        return leaseRepository
                .findByOwner_IdOrderByCreatedAtDesc(owner.getId())
                .stream()
                .map(lease -> buildSummary(lease, owner.getId()))
                .toList();
    }

    public TenantAccountSummaryResponse getAccount(
            Long leaseId,
            String email
    ) {

        User owner = getOwner(email);

        TenantLease lease = getOwnedLease(
                leaseId,
                owner.getId()
        );

        return buildSummary(lease, owner.getId());
    }

    // =========================================================
    // BUILD TENANT ACCOUNT SUMMARY
    // =========================================================

    private TenantAccountSummaryResponse buildSummary(
            TenantLease lease,
            Long ownerId
    ) {

        List<TenantRentCharge> charges =
                chargeRepository
                        .findByOwner_IdAndLease_IdOrderByPeriodStartAsc(
                                ownerId,
                                lease.getId()
                        );

        List<TenantRentPayment> payments =
                paymentRepository
                        .findByOwner_IdOrderByReceivedAtDesc(ownerId)
                        .stream()
                        .filter(p ->
                                p.getLease() != null
                                        && Objects.equals(
                                        p.getLease().getId(),
                                        lease.getId()
                                )
                        )
                        .toList();

        // -----------------------------------------------------
        // TOTAL RENT CHARGED
        // -----------------------------------------------------

        BigDecimal totalCharges = charges.stream()
                .map(TenantRentCharge::getAmount)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);

        // -----------------------------------------------------
        // TOTAL OUTSTANDING
        // -----------------------------------------------------

        BigDecimal outstanding = charges.stream()
                .map(TenantRentCharge::getBalance)
                .filter(Objects::nonNull)
                .filter(balance -> balance.compareTo(ZERO) > 0)
                .reduce(ZERO, BigDecimal::add);

        LocalDate today = LocalDate.now();

        // -----------------------------------------------------
        // OVERDUE ARREARS
        // -----------------------------------------------------

        BigDecimal overdue = charges.stream()
                .filter(c -> c.getDueDate() != null)
                .filter(c -> c.getDueDate().isBefore(today))
                .map(TenantRentCharge::getBalance)
                .filter(Objects::nonNull)
                .filter(balance -> balance.compareTo(ZERO) > 0)
                .reduce(ZERO, BigDecimal::add);

        // -----------------------------------------------------
        // VALID POSTED PAYMENTS
        // -----------------------------------------------------

        BigDecimal validPayments = payments.stream()
                .filter(p ->
                        p.getStatus()
                                == TenantRentPayment.PaymentStatus.POSTED
                )
                .map(TenantRentPayment::getAmount)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);

        // -----------------------------------------------------
        // POSTED PAYMENT IDS
        // -----------------------------------------------------

        List<Long> postedPaymentIds = payments.stream()
                .filter(p ->
                        p.getStatus()
                                == TenantRentPayment.PaymentStatus.POSTED
                )
                .map(TenantRentPayment::getId)
                .filter(Objects::nonNull)
                .toList();

        // -----------------------------------------------------
        // PAYMENT ALLOCATIONS
        // -----------------------------------------------------

        List<TenantPaymentAllocation> allocations =
                postedPaymentIds.isEmpty()
                        ? List.of()
                        : allocationRepository
                        .findByPayment_IdInOrderByIdAsc(
                                postedPaymentIds
                        );

        BigDecimal allocatedPayments = allocations.stream()
                .map(TenantPaymentAllocation::getAmount)
                .filter(Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);

        // -----------------------------------------------------
        // AVAILABLE CREDIT
        // -----------------------------------------------------

        BigDecimal credit = validPayments
                .subtract(allocatedPayments)
                .max(ZERO);

        // -----------------------------------------------------
        // NEXT RENT DUE DATE
        // -----------------------------------------------------

        LocalDate nextRentDueDate = calculateNextRentDueDate(
                lease,
                charges,
                credit
        );

        // -----------------------------------------------------
        // NET ACCOUNT POSITION
        // -----------------------------------------------------

        BigDecimal netPosition = totalCharges.subtract(validPayments);

        // -----------------------------------------------------
        // TENANT DETAILS AND PHOTOGRAPH
        // -----------------------------------------------------

        String tenantName = lease.getTenant() != null
                ? lease.getTenant().getFullName()
                : "Unknown Tenant";

        String tenantImageUrl = lease.getTenant() != null
                ? lease.getTenant().getImageUrl()
                : null;

        // -----------------------------------------------------
        // PROPERTY AND UNIT DETAILS
        // -----------------------------------------------------

        String propertyName =
                lease.getUnit() != null
                        && lease.getUnit().getProperty() != null
                        ? lease.getUnit().getProperty().getName()
                        : "Property not specified";

        String unitName =
                lease.getUnit() != null
                        ? lease.getUnit().getUnitNumber()
                        : "Unit not specified";

        // -----------------------------------------------------
        // RETURN ACCOUNT SUMMARY
        // -----------------------------------------------------

        return new TenantAccountSummaryResponse(

                lease.getId(),

                String.valueOf(lease.getId()),

                tenantName,

                tenantImageUrl,

                totalCharges,

                validPayments,

                outstanding,

                overdue,

                credit,

                netPosition,

                nextRentDueDate,

                propertyName,

                unitName
        );
    }

    // =========================================================
    // TENANT RENT STATEMENT
    // =========================================================

    public TenantRentStatementResponse getStatement(
            Long leaseId,
            LocalDate from,
            LocalDate to,
            String email
    ) {

        if (from == null || to == null || from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid statement date range is required."
            );
        }

        User owner = getOwner(email);

        TenantLease lease = getOwnedLease(
                leaseId,
                owner.getId()
        );

        List<TenantRentCharge> charges =
                chargeRepository
                        .findByOwner_IdAndLease_IdOrderByPeriodStartAsc(
                                owner.getId(),
                                leaseId
                        );

        List<TenantRentPayment> payments =
                paymentRepository
                        .findByOwner_IdOrderByReceivedAtDesc(owner.getId())
                        .stream()
                        .filter(p ->
                                p.getLease() != null
                                        && Objects.equals(
                                        p.getLease().getId(),
                                        leaseId
                                )
                        )
                        .toList();

        List<TenantRentStatementResponse.Transaction> allTransactions =
                new ArrayList<>();

        // -----------------------------------------------------
        // RENT CHARGES
        // -----------------------------------------------------

        for (TenantRentCharge charge : charges) {

            LocalDate transactionDate = charge.getPeriodStart();

            allTransactions.add(
                    new TenantRentStatementResponse.Transaction(
                            transactionDate,
                            "RENT_CHARGE",
                            "CHARGE-" + charge.getId(),
                            "Rent charge for "
                                    + charge.getPeriodStart()
                                    + " to "
                                    + charge.getPeriodEnd(),
                            charge.getAmount(),
                            ZERO,
                            ZERO
                    )
            );
        }

        // -----------------------------------------------------
        // PAYMENTS AND REVERSALS
        // -----------------------------------------------------

        for (TenantRentPayment payment : payments) {

            if (payment.getReceivedAt() == null) {
                continue;
            }

            LocalDate receivedDate =
                    payment.getReceivedAt().toLocalDate();

            allTransactions.add(
                    new TenantRentStatementResponse.Transaction(
                            receivedDate,
                            "PAYMENT",
                            payment.getPaymentReference(),
                            "Rent payment received via "
                                    + payment.getPaymentMethod(),
                            ZERO,
                            payment.getAmount(),
                            ZERO
                    )
            );

            if (payment.getReversedAt() != null) {

                allTransactions.add(
                        new TenantRentStatementResponse.Transaction(
                                payment.getReversedAt().toLocalDate(),
                                "REVERSAL",
                                payment.getPaymentReference(),
                                "Payment reversal: "
                                        + (payment.getReversalReason() == null
                                        ? "No reason supplied"
                                        : payment.getReversalReason()),
                                payment.getAmount(),
                                ZERO,
                                ZERO
                        )
                );
            }
        }

        // -----------------------------------------------------
        // ORDER TRANSACTIONS
        // -----------------------------------------------------

        Comparator<TenantRentStatementResponse.Transaction> ordering =
                Comparator.comparing(
                        TenantRentStatementResponse.Transaction::date,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).thenComparing(
                        TenantRentStatementResponse.Transaction::type,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ).thenComparing(
                        TenantRentStatementResponse.Transaction::reference,
                        Comparator.nullsLast(Comparator.naturalOrder())
                );

        allTransactions.sort(ordering);

        // -----------------------------------------------------
        // OPENING BALANCE
        // -----------------------------------------------------

        BigDecimal openingBalance = ZERO;

        for (TenantRentStatementResponse.Transaction transaction
                : allTransactions) {

            if (transaction.date() != null
                    && transaction.date().isBefore(from)) {

                openingBalance = openingBalance
                        .add(transaction.debit())
                        .subtract(transaction.credit());
            }
        }

        // -----------------------------------------------------
        // FILTER STATEMENT PERIOD
        // -----------------------------------------------------

        List<TenantRentStatementResponse.Transaction> periodTransactions =
                allTransactions.stream()
                        .filter(t -> t.date() != null)
                        .filter(t -> !t.date().isBefore(from))
                        .filter(t -> !t.date().isAfter(to))
                        .toList();

        BigDecimal runningBalance = openingBalance;
        BigDecimal totalStatementCharges = ZERO;
        BigDecimal totalPayments = ZERO;
        BigDecimal totalReversals = ZERO;

        List<TenantRentStatementResponse.Transaction> finalTransactions =
                new ArrayList<>();

        // -----------------------------------------------------
        // RUNNING BALANCE AND TOTALS
        // -----------------------------------------------------

        for (TenantRentStatementResponse.Transaction transaction
                : periodTransactions) {

            BigDecimal debit = transaction.debit() == null
                    ? ZERO
                    : transaction.debit();

            BigDecimal credit = transaction.credit() == null
                    ? ZERO
                    : transaction.credit();

            runningBalance = runningBalance
                    .add(debit)
                    .subtract(credit);

            if ("RENT_CHARGE".equals(transaction.type())) {

                totalStatementCharges =
                        totalStatementCharges.add(debit);

            } else if ("PAYMENT".equals(transaction.type())) {

                totalPayments = totalPayments.add(credit);

            } else if ("REVERSAL".equals(transaction.type())) {

                totalReversals = totalReversals.add(debit);
            }

            finalTransactions.add(
                    new TenantRentStatementResponse.Transaction(
                            transaction.date(),
                            transaction.type(),
                            transaction.reference(),
                            transaction.description(),
                            debit,
                            credit,
                            runningBalance
                    )
            );
        }

        // -----------------------------------------------------
        // RETURN STATEMENT
        // -----------------------------------------------------

        String tenantName = lease.getTenant() != null
                ? lease.getTenant().getFullName()
                : "Unknown Tenant";

        return new TenantRentStatementResponse(
                lease.getId(),
                String.valueOf(lease.getId()),
                tenantName,
                from,
                to,
                openingBalance,
                totalStatementCharges,
                totalPayments,
                totalReversals,
                runningBalance,
                finalTransactions
        );
    }

    // =========================================================
    // SECURITY: GET AUTHENTICATED OWNER
    // =========================================================

    private User getOwner(String email) {

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated owner was not found."
                ));
    }

    // =========================================================
    // SECURITY: VERIFY LEASE OWNERSHIP
    // =========================================================

    private TenantLease getOwnedLease(
            Long leaseId,
            Long ownerId
    ) {

        return leaseRepository.findByIdAndOwner_Id(
                        leaseId,
                        ownerId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Lease not found or access denied."
                ));
    }

    // =========================================================
    // CALCULATE NEXT RENT DUE DATE
    // =========================================================

    private LocalDate calculateNextRentDueDate(
            TenantLease lease,
            List<TenantRentCharge> charges,
            BigDecimal availableCredit
    ) {

        if (lease == null
                || lease.getStartDate() == null
                || lease.getEndDate() == null
                || lease.getRentDueDay() == null
                || lease.getMonthlyRent() == null
                || lease.getMonthlyRent().compareTo(ZERO) <= 0
                || lease.getStatus() != TenantLease.LeaseStatus.ACTIVE) {

            return null;
        }

        BigDecimal credit = availableCredit == null
                ? ZERO
                : availableCredit.max(ZERO);

        List<TenantRentCharge> orderedCharges = charges.stream()
                .filter(charge -> charge.getDueDate() != null)
                .filter(charge -> charge.getPeriodStart() != null)
                .sorted(
                        Comparator.comparing(
                                TenantRentCharge::getPeriodStart
                        )
                )
                .toList();

        LocalDate latestPeriodStart = null;

        // -----------------------------------------------------
        // PROCESS EXISTING CHARGES IN CHRONOLOGICAL ORDER
        // -----------------------------------------------------

        for (TenantRentCharge charge : orderedCharges) {

            latestPeriodStart = charge.getPeriodStart();

            BigDecimal balance = charge.getBalance() == null
                    ? ZERO
                    : charge.getBalance().max(ZERO);

            if (balance.compareTo(ZERO) <= 0) {
                continue;
            }

            BigDecimal amountCovered = credit.min(balance);

            BigDecimal remainingBalance =
                    balance.subtract(amountCovered);

            credit = credit.subtract(amountCovered);

            if (remainingBalance.compareTo(ZERO) > 0) {
                return charge.getDueDate();
            }
        }

        // -----------------------------------------------------
        // DETERMINE NEXT MONTHLY BILLING PERIOD
        // -----------------------------------------------------

        YearMonth nextPeriod;

        if (latestPeriodStart != null) {

            nextPeriod = YearMonth
                    .from(latestPeriodStart)
                    .plusMonths(1);

        } else {

            YearMonth startMonth =
                    YearMonth.from(lease.getStartDate());

            YearMonth currentMonth = YearMonth.now();

            nextPeriod = currentMonth.isAfter(startMonth)
                    ? currentMonth
                    : startMonth;
        }

        YearMonth endMonth =
                YearMonth.from(lease.getEndDate());

        // -----------------------------------------------------
        // APPLY ADVANCE CREDIT TO FUTURE MONTHLY RENT
        // -----------------------------------------------------

        while (!nextPeriod.isAfter(endMonth)) {

            LocalDate periodStart = nextPeriod.atDay(1);

            // Do not create a billing period before the lease starts.
            if (periodStart.isBefore(lease.getStartDate())) {

                nextPeriod = nextPeriod.plusMonths(1);
                continue;
            }

            LocalDate dueDate = nextPeriod.atDay(
                    Math.min(
                            lease.getRentDueDay(),
                            nextPeriod.lengthOfMonth()
                    )
            );

            BigDecimal amountCovered =
                    credit.min(lease.getMonthlyRent());

            BigDecimal remainingRent =
                    lease.getMonthlyRent().subtract(amountCovered);

            credit = credit.subtract(amountCovered);

            if (remainingRent.compareTo(ZERO) > 0) {
                return dueDate;
            }

            nextPeriod = nextPeriod.plusMonths(1);
        }

        return null;
    }
}
