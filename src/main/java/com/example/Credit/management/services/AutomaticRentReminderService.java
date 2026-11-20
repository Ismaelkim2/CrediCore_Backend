
package com.example.Credit.management.services;

import com.example.Credit.management.DTO.CreateTenantReminderRequest;
import com.example.Credit.management.DTO.RentReminderCandidateResponse;
import com.example.Credit.management.Model.TenantReminder;
import com.example.Credit.management.Repository.TenantReminderRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class AutomaticRentReminderService {

    private final TenantRentReminderEligibilityService eligibilityService;
    private final TenantReminderRepository reminderRepository;
    private final TenantReminderService reminderService;

    public AutomaticRentReminderService(
            TenantRentReminderEligibilityService eligibilityService,
            TenantReminderRepository reminderRepository,
            TenantReminderService reminderService
    ) {
        this.eligibilityService = eligibilityService;
        this.reminderRepository = reminderRepository;
        this.reminderService = reminderService;
    }

    /**
     * Creates missing rent reminders for eligible outstanding charges.
     *
     * Existing reminders with the same idempotency key are skipped.
     * This method creates reminder records; it does not send SMS or email.
     */
    @Transactional
    public int generate(
            String actor,
            String category,
            int daysAhead,
            TenantReminder.ReminderChannel channel
    ) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException(
                    "An authenticated actor is required."
            );
        }

        if (channel == null) {
            channel = TenantReminder.ReminderChannel.IN_APP;
        }

        List<RentReminderCandidateResponse> candidates =
                eligibilityService.findEligible(
                        actor,
                        category,
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

            String idempotencyKey = buildIdempotencyKey(
                    actor,
                    candidate.chargeId(),
                    candidate.category()
            );

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
                            null, // Property ID is not supplied by the candidate DTO.
                            null, // Unit ID is not supplied by the candidate DTO.
                            title,
                            message,
                            type,
                            channel,
                            candidate.dueDate(),
                            candidate.outstandingAmount(),
                            null, // Immediate reminder; no scheduled time.
                            idempotencyKey
                    );

            // Uses the existing creation and audit workflow.
            // The unique database constraint remains the final
            // protection against concurrent duplicate creation.
            reminderService.create(request, actor);
            created++;
        }

        return created;
    }

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
