
package com.example.Credit.management.services;

import com.example.Credit.management.DTO.CreateTenantReminderRequest;
import com.example.Credit.management.DTO.RentReminderCandidateResponse;
import com.example.Credit.management.Model.Role;
import com.example.Credit.management.Model.TenantLease;
import com.example.Credit.management.Model.TenantReminder;
import com.example.Credit.management.Repository.TenantLeaseRepository;
import com.example.Credit.management.Repository.TenantReminderRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AutomaticRentReminderService {

    private static final Set<String> LEGACY_CATEGORIES = Set.of(
            "ALL",
            "DUE_SOON",
            "DUE_TODAY",
            "OVERDUE"
    );

    private final TenantRentReminderEligibilityService eligibilityService;
    private final TenantReminderRepository reminderRepository;
    private final TenantReminderService reminderService;
    private final TenantLeaseRepository leaseRepository;

    public AutomaticRentReminderService(
            TenantRentReminderEligibilityService eligibilityService,
            TenantReminderRepository reminderRepository,
            TenantReminderService reminderService,
            TenantLeaseRepository leaseRepository
    ) {
        this.eligibilityService = eligibilityService;
        this.reminderRepository = reminderRepository;
        this.reminderService = reminderService;
        this.leaseRepository = leaseRepository;
    }

    /**
     * Creates missing rent reminders for eligible outstanding charges.
     *
     * Existing idempotency keys are preserved.
     *
     * New automatic reminders use the lease owner's email
     * as the canonical key identity, so ADMIN and the owning
     * TENANT_AGENT share the same key.
     */
    @Transactional
    public int generate(
            String actor,
            String category,
            int daysAhead,
            TenantReminder.ReminderChannel channel
    ) {

        if (actor == null || actor.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "An authenticated actor is required."
            );
        }

        String authenticatedActor = actor.trim();

        if (channel == null) {
            channel = TenantReminder.ReminderChannel.IN_APP;
        }

        String normalizedCategory = category == null
                ? "ALL"
                : category.trim().toUpperCase(Locale.ROOT);

        List<RentReminderCandidateResponse> candidates =
                eligibilityService.findEligible(
                        authenticatedActor,
                        normalizedCategory,
                        daysAhead
                );

        int created = 0;

        for (RentReminderCandidateResponse candidate : candidates) {

            if (candidate.tenantId() == null
                    || candidate.leaseId() == null
                    || candidate.chargeId() == null
                    || candidate.dueDate() == null
                    || candidate.outstandingAmount() == null
                    || candidate.outstandingAmount().signum() <= 0) {
                continue;
            }

            TenantLease lease = leaseRepository.findById(candidate.leaseId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "The lease associated with a reminder candidate no longer exists."
                    ));

            if (lease.getOwner() == null
                    || lease.getOwner().getEmail() == null
                    || lease.getOwner().getEmail().isBlank()
                    || lease.getOwner().getRole() != Role.TENANT_AGENT
                    || lease.getTenant() == null
                    || !lease.getTenant().getId().equals(candidate.tenantId())) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The lease owner or tenant association is invalid."
                );
            }

            String ownerEmail = lease.getOwner().getEmail().trim();

            /*
             * Check existing records created under both:
             * 1. The current actor's email.
             * 2. The actual lease owner's email.
             *
             * Check all legacy categories because a charge
             * may have moved from DUE_SOON to OVERDUE.
             */
            if (alreadyGenerated(
                    authenticatedActor,
                    ownerEmail,
                    candidate.chargeId()
            )) {
                continue;
            }

            /*
             * New keys use the lease owner's email.
             * This allows ADMIN and the owning agent to share
             * the same idempotency key going forward.
             */
            String idempotencyKey = buildIdempotencyKey(
                    ownerEmail,
                    candidate.chargeId(),
                    normalizedCategory
            );

            /*
             * Final check before creation.
             */
            if (reminderRepository.existsByIdempotencyKey(
                    idempotencyKey
            )) {
                continue;
            }

            boolean overdue = "OVERDUE".equalsIgnoreCase(
                    candidate.category()
            );

            TenantReminder.ReminderType type = overdue
                    ? TenantReminder.ReminderType.RENT_OVERDUE
                    : TenantReminder.ReminderType.RENT_DUE;

            String tenantName = candidate.tenantName() == null
                    || candidate.tenantName().isBlank()
                    ? "Tenant"
                    : candidate.tenantName().trim();

            String formattedAmount = candidate.outstandingAmount()
                    .setScale(2, java.math.RoundingMode.HALF_UP)
                    .toPlainString();

            String formattedDueDate = candidate.dueDate().format(
                    DateTimeFormatter.ISO_LOCAL_DATE
            );

            String title = overdue
                    ? "Overdue Rent Reminder"
                    : "Upcoming Rent Reminder";

            String message = overdue
                    ? "Hello " + tenantName
                    + ", our records show outstanding rent of KES "
                    + formattedAmount
                    + " due on " + formattedDueDate
                    + ". Please review your rent balance and contact "
                    + "the property management team if you have already paid."
                    : "Hello " + tenantName
                    + ", your outstanding rent balance is KES "
                    + formattedAmount
                    + ", due on " + formattedDueDate
                    + ". Please arrange payment by the due date. "
                    + "If you have already paid, please contact "
                    + "the property management team to confirm allocation.";

            CreateTenantReminderRequest request =
                    new CreateTenantReminderRequest(
                            candidate.tenantId(),
                            null,
                            null,
                            title,
                            message,
                            type,
                            channel,
                            candidate.dueDate(),
                            candidate.outstandingAmount(),
                            null,
                            idempotencyKey
                    );

            /*
             * Preserve the authenticated actor as createdBy.
             * The canonical idempotency identity does not
             * change the reminder's audit ownership.
             */
            reminderService.create(request, authenticatedActor);

            created++;
        }

        return created;
    }

    /**
     * Checks legacy idempotency keys without modifying them.
     */
    private boolean alreadyGenerated(
            String actor,
            String ownerEmail,
            Long chargeId
    ) {

        for (String category : LEGACY_CATEGORIES) {

            String ownerKey = buildIdempotencyKey(
                    ownerEmail,
                    chargeId,
                    category
            );

            if (reminderRepository.existsByIdempotencyKey(ownerKey)) {
                return true;
            }

            if (!actor.equalsIgnoreCase(ownerEmail)) {

                String actorKey = buildIdempotencyKey(
                        actor,
                        chargeId,
                        category
                );

                if (reminderRepository.existsByIdempotencyKey(actorKey)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Keep this formula unchanged for compatibility
     * with previously generated idempotency records.
     */
    private String buildIdempotencyKey(
            String actor,
            Long chargeId,
            String category
    ) {

        String normalizedCategory = category == null
                ? "ALL"
                : category.trim().toUpperCase(Locale.ROOT);

        String raw = actor.trim()
                + "|rent-charge|"
                + chargeId
                + "|"
                + normalizedCategory;

        return "rent-" + sha256(raw);
    }

    private String sha256(String value) {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {

            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception
            );
        }
    }
}
