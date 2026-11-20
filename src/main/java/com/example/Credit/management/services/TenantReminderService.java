package com.example.Credit.management.services;
import com.example.Credit.management.DTO.*;
import com.example.Credit.management.Model.ReminderAuditLog;
import com.example.Credit.management.Model.TenantReminder;
import com.example.Credit.management.Model.TenantResident;
import com.example.Credit.management.Repository.ReminderAuditLogRepository;
import com.example.Credit.management.Repository.TenantReminderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class TenantReminderService {

    private final TenantReminderRepository reminders;
    private final ReminderAuditLogRepository audits;
    private final TenantResidentService tenantResidentService;

    public TenantReminderService(
            TenantReminderRepository reminders,
            ReminderAuditLogRepository audits,
            TenantResidentService tenantResidentService
    ) {
        this.reminders = reminders;
        this.audits = audits;
        this.tenantResidentService = tenantResidentService;
    }

    // =========================================================
    // CREATE REMINDER
    // =========================================================

    @Transactional
    public TenantReminderResponse create(
            CreateTenantReminderRequest request,
            String actor
    ) {

        requireActor(actor);

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reminder request cannot be empty."
            );
        }

        if (request.type() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reminder type is required."
            );
        }

        // Calendar events may be created without a tenant.
        // All other reminder types require an active owned tenant.
        if (request.type() == TenantReminder.ReminderType.CALENDAR) {

            if (request.tenantId() != null) {
                validateTenantOwnership(request.tenantId(), actor);
            }

        } else {

            if (request.tenantId() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Tenant ID is required for tenant reminders."
                );
            }

            validateTenantOwnership(request.tenantId(), actor);
        }

        String title = clean(request.title());
        String message = clean(request.message());
        String idempotencyKey = clean(request.idempotencyKey());

        validateTitleAndMessage(title, message);

        if (request.dueDate() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reminder due date is required."
            );
        }

        validateAmount(request.amount());

        if (idempotencyKey != null
                && reminders.existsByIdempotencyKey(idempotencyKey)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This reminder request has already been submitted."
            );
        }

        TenantReminder reminder = new TenantReminder();

        reminder.setTenantId(request.tenantId());
        reminder.setPropertyId(request.propertyId());
        reminder.setUnitId(request.unitId());

        reminder.setTitle(title);
        reminder.setMessage(message);
        reminder.setType(request.type());

        // Calendar events must remain internal IN_APP events.
        if (request.type() == TenantReminder.ReminderType.CALENDAR) {

            reminder.setChannel(
                    TenantReminder.ReminderChannel.IN_APP
            );

        } else {

            reminder.setChannel(
                    request.channel() != null
                            ? request.channel()
                            : TenantReminder.ReminderChannel.IN_APP
            );
        }

        reminder.setDueDate(request.dueDate());
        reminder.setAmount(request.amount());
        reminder.setScheduledAt(request.scheduledAt());
        reminder.setCreatedBy(actor);
        reminder.setIdempotencyKey(idempotencyKey);

        reminder.setStatus(
                request.scheduledAt() != null
                        ? TenantReminder.ReminderStatus.SCHEDULED
                        : TenantReminder.ReminderStatus.PENDING
        );

        TenantReminder saved = reminders.saveAndFlush(reminder);

        audit(
                saved,
                "CREATED",
                actor,
                request.type() == TenantReminder.ReminderType.CALENDAR
                        ? "Calendar event created."
                        : "Reminder created."
        );

        return toResponse(saved);
    }

    // =========================================================
    // LIST REMINDERS CREATED BY THE CURRENT ACTOR
    // =========================================================

    @Transactional(readOnly = true)
    public Page<TenantReminderResponse> list(
            String actor,
            int page,
            int size
    ) {

        requireActor(actor);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        return reminders.findByCreatedByOrderByCreatedAtDesc(
                        actor,
                        PageRequest.of(
                                safePage,
                                safeSize,
                                Sort.by(
                                        Sort.Direction.DESC,
                                        "createdAt"
                                )
                        )
                )
                .map(this::toResponse);
    }

    // =========================================================
    // GET ONE REMINDER
    // =========================================================

    @Transactional(readOnly = true)
    public TenantReminderResponse get(
            Long id,
            String actor
    ) {

        requireActor(actor);

        return toResponse(
                ownedReminder(id, actor)
        );
    }

    // =========================================================
    // CANCEL REMINDER
    // =========================================================

    @Transactional
    public TenantReminderResponse cancel(
            Long id,
            String actor
    ) {

        requireActor(actor);

        TenantReminder reminder = ownedReminder(id, actor);

        if (reminder.getStatus() == TenantReminder.ReminderStatus.SENT
                || reminder.getStatus() == TenantReminder.ReminderStatus.DELIVERED) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A sent or delivered reminder cannot be cancelled."
            );
        }

        if (reminder.getStatus() == TenantReminder.ReminderStatus.CANCELLED) {
            return toResponse(reminder);
        }

        reminder.setStatus(
                TenantReminder.ReminderStatus.CANCELLED
        );

        reminder.setCancelledAt(LocalDateTime.now());

        TenantReminder saved = reminders.save(reminder);

        audit(
                saved,
                "CANCELLED",
                actor,
                "Reminder cancelled."
        );

        return toResponse(saved);
    }

    // =========================================================
    // RECORD FOLLOW-UP
    // =========================================================

    @Transactional
    public TenantReminderResponse markFollowedUp(
            Long id,
            String actor
    ) {

        requireActor(actor);

        TenantReminder reminder = ownedReminder(id, actor);

        if (reminder.getStatus() == TenantReminder.ReminderStatus.CANCELLED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A cancelled reminder cannot be followed up."
            );
        }

        reminder.setStatus(
                TenantReminder.ReminderStatus.FOLLOWED_UP
        );

        reminder.setFollowedUpAt(LocalDateTime.now());

        TenantReminder saved = reminders.save(reminder);

        audit(
                saved,
                "FOLLOWED_UP",
                actor,
                "Follow-up recorded by user."
        );

        return toResponse(saved);
    }

    // =========================================================
    // REQUEST RETRY FOR A FAILED REMINDER
    // =========================================================

    @Transactional
    public TenantReminderResponse requestRetry(
            Long id,
            String actor
    ) {

        requireActor(actor);

        TenantReminder reminder = ownedReminder(id, actor);

        if (reminder.getType() == TenantReminder.ReminderType.CALENDAR) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Calendar events do not use the delivery retry process."
            );
        }

        if (reminder.getStatus() != TenantReminder.ReminderStatus.FAILED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only failed reminders can be retried."
            );
        }

        reminder.setStatus(
                TenantReminder.ReminderStatus.PENDING
        );

        reminder.setNextAttemptAt(null);
        reminder.setLastError(null);

        TenantReminder saved = reminders.save(reminder);

        audit(
                saved,
                "RETRY_REQUESTED",
                actor,
                "Reminder queued for retry."
        );

        return toResponse(saved);
    }

    // =========================================================
    // LOAD CALENDAR EVENTS
    // =========================================================

    @Transactional(readOnly = true)
    public List<TenantReminderResponse> getCalendarEvents(
            String actor,
            LocalDate from,
            LocalDate to
    ) {

        requireActor(actor);

        if (from == null || to == null || from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid calendar date range is required."
            );
        }

        if (ChronoUnit.DAYS.between(from, to) > 62) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Calendar date range cannot exceed 63 days."
            );
        }

        return reminders
                .findByCreatedByAndDueDateBetweenOrderByDueDateAscScheduledAtAsc(
                        actor,
                        from,
                        to
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // =========================================================
    // UPDATE CALENDAR EVENT
    // =========================================================

    @Transactional
    public TenantReminderResponse updateCalendarEvent(
            Long id,
            CreateTenantReminderRequest request,
            String actor
    ) {

        requireActor(actor);

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Calendar event details are required."
            );
        }

        if (request.type() != TenantReminder.ReminderType.CALENDAR) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The event type must be CALENDAR."
            );
        }

        TenantReminder reminder = ownedReminder(id, actor);

        if (reminder.getType() != TenantReminder.ReminderType.CALENDAR) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only calendar events can be edited through this endpoint."
            );
        }

        if (reminder.getStatus() == TenantReminder.ReminderStatus.CANCELLED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A cancelled calendar event cannot be edited."
            );
        }

        String title = clean(request.title());
        String message = clean(request.message());

        validateTitleAndMessage(title, message);

        if (request.dueDate() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Event date is required."
            );
        }

        validateAmount(request.amount());

        if (request.tenantId() != null) {
            validateTenantOwnership(request.tenantId(), actor);
        }

        reminder.setTenantId(request.tenantId());
        reminder.setPropertyId(request.propertyId());
        reminder.setUnitId(request.unitId());

        reminder.setTitle(title);
        reminder.setMessage(message);
        reminder.setType(TenantReminder.ReminderType.CALENDAR);

        // Calendar events are always internal.
        reminder.setChannel(
                TenantReminder.ReminderChannel.IN_APP
        );

        reminder.setDueDate(request.dueDate());
        reminder.setAmount(request.amount());
        reminder.setScheduledAt(request.scheduledAt());

        // Preserve an existing completed follow-up.
        if (reminder.getStatus() != TenantReminder.ReminderStatus.FOLLOWED_UP) {

            reminder.setStatus(
                    request.scheduledAt() != null
                            ? TenantReminder.ReminderStatus.SCHEDULED
                            : TenantReminder.ReminderStatus.PENDING
            );
        }

        reminder.setNextAttemptAt(null);
        reminder.setLastError(null);

        TenantReminder saved = reminders.saveAndFlush(reminder);

        audit(
                saved,
                "CALENDAR_EVENT_UPDATED",
                actor,
                "Calendar event updated."
        );

        return toResponse(saved);
    }

    // =========================================================
    // FIND A REMINDER OWNED BY THE CURRENT ACTOR
    // =========================================================

    private TenantReminder ownedReminder(
            Long id,
            String actor
    ) {

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid reminder ID is required."
            );
        }

        return reminders.findByIdAndCreatedBy(id, actor)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Reminder not found."
                ));
    }

    // =========================================================
    // AUDIT TRAIL
    // =========================================================

    private void audit(
            TenantReminder reminder,
            String action,
            String actor,
            String details
    ) {

        audits.save(
                new ReminderAuditLog(
                        reminder.getId(),
                        action,
                        actor,
                        details
                )
        );
    }

    // =========================================================
    // VALIDATE ACTOR
    // =========================================================

    private void requireActor(String actor) {

        if (actor == null || actor.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required."
            );
        }
    }

    // =========================================================
    // VALIDATE TENANT OWNERSHIP
    // =========================================================

    private void validateTenantOwnership(
            Long tenantId,
            String actor
    ) {

        if (tenantId == null || tenantId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid tenant ID is required."
            );
        }

        tenantResidentService.validateActiveOwnedTenantIds(
                List.of(tenantId),
                actor
        );
    }

    // =========================================================
    // VALIDATE TITLE AND MESSAGE
    // =========================================================

    private void validateTitleAndMessage(
            String title,
            String message
    ) {

        if (title == null || title.length() > 160) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A title between 1 and 160 characters is required."
            );
        }

        if (message == null || message.length() > 2000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A message between 1 and 2000 characters is required."
            );
        }
    }

    // =========================================================
    // VALIDATE AMOUNT
    // =========================================================

    private void validateAmount(java.math.BigDecimal amount) {

        if (amount != null && amount.signum() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reminder amount cannot be negative."
            );
        }
    }

    // =========================================================
    // NORMALIZE OPTIONAL STRINGS
    // =========================================================

    private String clean(String value) {

        return value == null || value.isBlank()
                ? null
                : value.trim();
    }

    // =========================================================
    // CREATE BULK REMINDERS
    // =========================================================

    @Transactional
    public List<TenantReminderResponse> createBulk(
            BulkTenantReminderRequest request,
            String actor
    ) {

        requireActor(actor);

        if (request == null || request.reminder() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reminder details are required."
            );
        }

        if (request.tenantIds() == null || request.tenantIds().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "At least one tenant must be selected."
            );
        }

        if (request.reminder().type() == TenantReminder.ReminderType.CALENDAR) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Calendar events cannot be sent as bulk tenant reminders."
            );
        }

        // Validate every recipient before saving any reminders.
        List<Long> tenantIds =
                tenantResidentService.validateActiveOwnedTenantIds(
                        request.tenantIds(),
                        actor
                );

        if (tenantIds == null || tenantIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No eligible tenants were found."
            );
        }

        CreateTenantReminderRequest original = request.reminder();

        List<TenantReminderResponse> results = new ArrayList<>();

        for (Long tenantId : tenantIds) {

            String key = clean(original.idempotencyKey());

            // Ensure every reminder has a unique idempotency key.
            if (key != null) {

                String suffix = "-" + tenantId;
                int maxBaseLength = 200 - suffix.length();

                if (key.length() > maxBaseLength) {
                    key = key.substring(0, maxBaseLength);
                }

                key = key + suffix;
            }

            CreateTenantReminderRequest recipientRequest =
                    new CreateTenantReminderRequest(
                            tenantId,
                            original.propertyId(),
                            original.unitId(),
                            original.title(),
                            original.message(),
                            original.type(),
                            original.channel(),
                            original.dueDate(),
                            original.amount(),
                            original.scheduledAt(),
                            key
                    );

            results.add(create(recipientRequest, actor));
        }

        return results;
    }

    // =========================================================
    // COUNT ELIGIBLE TENANTS
    // =========================================================

    @Transactional(readOnly = true)
    public EligibleTenantCountResponse getEligibleRecipientCount(
            String actor
    ) {

        requireActor(actor);

        long count = tenantResidentService.getAll(actor)
                .stream()
                .filter(tenant ->
                        tenant.status() == TenantResident.TenantStatus.ACTIVE
                )
                .count();

        return new EligibleTenantCountResponse(
                Math.toIntExact(count)
        );
    }

    // =========================================================
    // CREATE REMINDERS FOR ALL ACTIVE TENANTS
    // =========================================================

    @Transactional
    public BulkReminderResultResponse createForAllEligible(
            BulkTenantReminderRequest request,
            String actor
    ) {

        requireActor(actor);

        if (request == null || request.reminder() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reminder details are required."
            );
        }

        var details = request.reminder();

        if (details.type() == TenantReminder.ReminderType.CALENDAR) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Calendar events cannot be sent to all tenants."
            );
        }

        List<Long> tenantIds = tenantResidentService.getAll(actor)
                .stream()
                .filter(tenant ->
                        tenant.status() == TenantResident.TenantStatus.ACTIVE
                )
                .map(tenant -> tenant.id())
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (tenantIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "There are no active tenants eligible to receive reminders."
            );
        }

        CreateTenantReminderRequest reminder =
                new CreateTenantReminderRequest(
                        null,
                        details.propertyId(),
                        details.unitId(),
                        details.title(),
                        details.message(),
                        details.type(),
                        details.channel(),
                        details.dueDate(),
                        details.amount(),
                        details.scheduledAt(),
                        details.idempotencyKey()
                );

        BulkTenantReminderRequest bulkRequest =
                new BulkTenantReminderRequest(
                        tenantIds,
                        reminder
                );

        List<TenantReminderResponse> created =
                createBulk(bulkRequest, actor);

        return new BulkReminderResultResponse(created.size());
    }

    // =========================================================
    // MAP REMINDER + TENANT DETAILS TO RESPONSE
    // =========================================================

    private TenantReminderResponse toResponse(
            TenantReminder reminder
    ) {

        if (reminder == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Reminder not found."
            );
        }

        TenantResidentResponse tenant = null;

        // Calendar events can exist without an associated tenant.
        if (reminder.getTenantId() != null) {

            tenant = tenantResidentService.getAll(
                            reminder.getCreatedBy()
                    )
                    .stream()
                    .filter(t -> Objects.equals(
                            t.id(),
                            reminder.getTenantId()
                    ))
                    .findFirst()
                    .orElse(null);
        }

        return TenantReminderResponse.from(
                reminder,
                tenant
        );
    }
}
