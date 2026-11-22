
package com.example.Credit.management.services;

import com.example.Credit.management.DTO.*;
import com.example.Credit.management.Model.*;
import com.example.Credit.management.Repository.ReminderAuditLogRepository;
import com.example.Credit.management.Repository.TenantLeaseRepository;
import com.example.Credit.management.Repository.TenantPropertyRepository;
import com.example.Credit.management.Repository.TenantReminderRepository;
import com.example.Credit.management.Repository.TenantResidentRepository;
import com.example.Credit.management.Repository.TenantUnitRepository;
import com.example.Credit.management.Repository.UserRepository;

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
    private final TenantResidentRepository tenantRepository;
    private final TenantPropertyRepository propertyRepository;
    private final TenantUnitRepository unitRepository;
    private final TenantLeaseRepository leaseRepository;
    private final UserRepository userRepository;

    public TenantReminderService(
            TenantReminderRepository reminders,
            ReminderAuditLogRepository audits,
            TenantResidentService tenantResidentService,
            TenantResidentRepository tenantRepository,
            TenantPropertyRepository propertyRepository,
            TenantUnitRepository unitRepository,
            TenantLeaseRepository leaseRepository,
            UserRepository userRepository
    ) {
        this.reminders = reminders;
        this.audits = audits;
        this.tenantResidentService = tenantResidentService;
        this.tenantRepository = tenantRepository;
        this.propertyRepository = propertyRepository;
        this.unitRepository = unitRepository;
        this.leaseRepository = leaseRepository;
        this.userRepository = userRepository;
    }

    // =========================================================
    // AUTHENTICATED ACTOR
    // =========================================================

    private User requireActor(String actor) {

        if (actor == null || actor.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required."
            );
        }

        return userRepository.findByEmail(actor.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found."
                ));
    }

    private boolean isAdmin(User user) {
        return user.getRole() == Role.ADMIN;
    }

    private void requireTenantAgentOrAdmin(User user) {

        if (user.getRole() != Role.TENANT_AGENT
                && user.getRole() != Role.ADMIN) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not authorized to manage tenant reminders."
            );
        }
    }

    // =========================================================
    // VALIDATE TENANT
    // =========================================================

    private TenantResident validateTenant(
            Long tenantId,
            User actor
    ) {

        if (tenantId == null || tenantId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid tenant ID is required."
            );
        }

        TenantResident tenant;

        if (isAdmin(actor)) {

            tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Tenant not found."
                    ));

        } else {

            tenant = tenantRepository.findByIdAndOwner_Id(
                            tenantId,
                            actor.getId()
                    )
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Tenant not found."
                    ));
        }

        if (tenant.getStatus() != TenantResident.TenantStatus.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The selected tenant is not active."
            );
        }

        if (tenant.getOwner() == null
                || tenant.getOwner().getId() == null
                || tenant.getOwner().getRole() != Role.TENANT_AGENT) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The tenant does not belong to a valid tenant agent."
            );
        }

        return tenant;
    }

    // =========================================================
    // VALIDATE PROPERTY AND UNIT RELATIONSHIPS
    // =========================================================

    private void validatePropertyAndUnit(
            Long propertyId,
            Long unitId,
            TenantResident tenant,
            User actor
    ) {

        if (unitId != null && propertyId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Property ID is required when a unit is selected."
            );
        }

        if (propertyId == null) {
            return;
        }

        var property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Property not found."
                ));

        if (property.getOwner() == null
                || property.getOwner().getId() == null
                || property.getOwner().getRole() != Role.TENANT_AGENT) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The property does not belong to a valid tenant agent."
            );
        }

        // Agents can only use their own properties.
        if (!isAdmin(actor)
                && !property.getOwner().getId().equals(actor.getId())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You cannot use another agent's property."
            );
        }

        // If a tenant is supplied, the property must belong
        // to the same agent who owns that tenant.
        if (tenant != null
                && !property.getOwner().getId().equals(
                tenant.getOwner().getId()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The selected tenant and property belong to different agents."
            );
        }

        if (unitId == null) {

            if (tenant != null
                    && !leaseRepository.existsByTenant_IdAndUnit_Property_IdAndOwner_Id(
                    tenant.getId(),
                    propertyId,
                    property.getOwner().getId()
            )) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "The selected tenant has no lease associated with this property."
                );
            }

            return;
        }

        var unit = unitRepository.findByIdAndProperty_Id(
                        unitId,
                        propertyId
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "The selected unit does not belong to this property."
                ));

        if (unit.getProperty() == null
                || unit.getProperty().getOwner() == null
                || !unit.getProperty().getOwner().getId().equals(
                property.getOwner().getId()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The selected unit has an invalid property owner."
            );
        }

        if (tenant != null
                && !leaseRepository.existsByTenant_IdAndUnit_IdAndOwner_Id(
                tenant.getId(),
                unitId,
                property.getOwner().getId()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The selected tenant has no lease associated with this unit."
            );
        }
    }

    // =========================================================
    // CREATE REMINDER
    // =========================================================

    @Transactional
    public TenantReminderResponse create(
            CreateTenantReminderRequest request,
            String actor
    ) {

        User currentUser = requireActor(actor);
        requireTenantAgentOrAdmin(currentUser);

        if (request == null || request.type() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reminder details and reminder type are required."
            );
        }

        boolean calendar =
                request.type() == TenantReminder.ReminderType.CALENDAR;

        TenantResident tenant = null;

        if (request.tenantId() != null) {
            tenant = validateTenant(request.tenantId(), currentUser);
        } else if (!calendar) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tenant ID is required for tenant reminders."
            );
        }

        validatePropertyAndUnit(
                request.propertyId(),
                request.unitId(),
                tenant,
                currentUser
        );

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

        reminder.setChannel(
                calendar
                        ? TenantReminder.ReminderChannel.IN_APP
                        : request.channel() != null
                        ? request.channel()
                        : TenantReminder.ReminderChannel.IN_APP
        );

        reminder.setDueDate(request.dueDate());
        reminder.setAmount(request.amount());
        reminder.setScheduledAt(request.scheduledAt());

        // Always use the authenticated user, never a request-supplied creator.
        reminder.setCreatedBy(currentUser.getEmail());

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
                currentUser.getEmail(),
                calendar
                        ? "Calendar event created."
                        : "Reminder created."
        );

        return toResponse(saved);
    }

    // =========================================================
    // LIST REMINDERS
    // =========================================================

    @Transactional(readOnly = true)
    public Page<TenantReminderResponse> list(
            String actor,
            int page,
            int size
    ) {

        User currentUser = requireActor(actor);
        requireTenantAgentOrAdmin(currentUser);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        PageRequest pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<TenantReminder> results = isAdmin(currentUser)
                ? reminders.findAllByOrderByCreatedAtDesc(pageable)
                : reminders.findByCreatedByOrderByCreatedAtDesc(
                currentUser.getEmail(),
                pageable
        );

        return results.map(this::toResponse);
    }

    // =========================================================
    // GET ONE REMINDER
    // =========================================================

    @Transactional(readOnly = true)
    public TenantReminderResponse get(
            Long id,
            String actor
    ) {

        User currentUser = requireActor(actor);
        requireTenantAgentOrAdmin(currentUser);

        return toResponse(ownedReminder(id, currentUser));
    }

    // =========================================================
    // CANCEL REMINDER
    // =========================================================

    @Transactional
    public TenantReminderResponse cancel(
            Long id,
            String actor
    ) {

        User currentUser = requireActor(actor);
        requireTenantAgentOrAdmin(currentUser);

        TenantReminder reminder = ownedReminder(id, currentUser);

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

        reminder.setStatus(TenantReminder.ReminderStatus.CANCELLED);
        reminder.setCancelledAt(LocalDateTime.now());

        TenantReminder saved = reminders.save(reminder);

        audit(
                saved,
                "CANCELLED",
                currentUser.getEmail(),
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

        User currentUser = requireActor(actor);
        requireTenantAgentOrAdmin(currentUser);

        TenantReminder reminder = ownedReminder(id, currentUser);

        if (reminder.getStatus() == TenantReminder.ReminderStatus.CANCELLED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A cancelled reminder cannot be followed up."
            );
        }

        reminder.setStatus(TenantReminder.ReminderStatus.FOLLOWED_UP);
        reminder.setFollowedUpAt(LocalDateTime.now());

        TenantReminder saved = reminders.save(reminder);

        audit(
                saved,
                "FOLLOWED_UP",
                currentUser.getEmail(),
                "Follow-up recorded by user."
        );

        return toResponse(saved);
    }

    // =========================================================
    // RETRY FAILED REMINDER
    // =========================================================

    @Transactional
    public TenantReminderResponse requestRetry(
            Long id,
            String actor
    ) {

        User currentUser = requireActor(actor);
        requireTenantAgentOrAdmin(currentUser);

        TenantReminder reminder = ownedReminder(id, currentUser);

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

        reminder.setStatus(TenantReminder.ReminderStatus.PENDING);
        reminder.setNextAttemptAt(null);
        reminder.setLastError(null);

        TenantReminder saved = reminders.save(reminder);

        audit(
                saved,
                "RETRY_REQUESTED",
                currentUser.getEmail(),
                "Reminder queued for retry."
        );

        return toResponse(saved);
    }

    // =========================================================
    // CALENDAR EVENTS
    // =========================================================

    @Transactional(readOnly = true)
    public List<TenantReminderResponse> getCalendarEvents(
            String actor,
            LocalDate from,
            LocalDate to
    ) {

        User currentUser = requireActor(actor);
        requireTenantAgentOrAdmin(currentUser);

        validateCalendarRange(from, to);

        List<TenantReminder> events = isAdmin(currentUser)
                ? reminders.findByTypeAndDueDateBetweenOrderByDueDateAscScheduledAtAsc(
                TenantReminder.ReminderType.CALENDAR,
                from,
                to
        )
                : reminders.findByCreatedByAndDueDateBetweenOrderByDueDateAscScheduledAtAsc(
                currentUser.getEmail(),
                from,
                to
        );

        return events.stream()
                .filter(event -> event.getType() == TenantReminder.ReminderType.CALENDAR)
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

        User currentUser = requireActor(actor);
        requireTenantAgentOrAdmin(currentUser);

        if (request == null
                || request.type() != TenantReminder.ReminderType.CALENDAR) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Valid calendar event details are required."
            );
        }

        TenantReminder reminder = ownedReminder(id, currentUser);

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

        TenantResident tenant = request.tenantId() == null
                ? null
                : validateTenant(request.tenantId(), currentUser);

        validatePropertyAndUnit(
                request.propertyId(),
                request.unitId(),
                tenant,
                currentUser
        );

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

        reminder.setTenantId(request.tenantId());
        reminder.setPropertyId(request.propertyId());
        reminder.setUnitId(request.unitId());

        reminder.setTitle(title);
        reminder.setMessage(message);
        reminder.setType(TenantReminder.ReminderType.CALENDAR);
        reminder.setChannel(TenantReminder.ReminderChannel.IN_APP);

        reminder.setDueDate(request.dueDate());
        reminder.setAmount(request.amount());
        reminder.setScheduledAt(request.scheduledAt());

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
                currentUser.getEmail(),
                "Calendar event updated."
        );

        return toResponse(saved);
    }

    // =========================================================
    // FIND REMINDER
    // AGENT: OWN REMINDERS
    // ADMIN: ALL REMINDERS
    // =========================================================

    private TenantReminder ownedReminder(
            Long id,
            User actor
    ) {

        if (id == null || id <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid reminder ID is required."
            );
        }

        if (isAdmin(actor)) {
            return reminders.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Reminder not found."
                    ));
        }

        return reminders.findByIdAndCreatedBy(
                        id,
                        actor.getEmail()
                )
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
    // VALIDATE TITLE AND MESSAGE
    // =========================================================

    private void validateTitleAndMessage(
            String title,
            String message
    ) {

        if (title == null || title.isBlank() || title.length() > 160) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A title between 1 and 160 characters is required."
            );
        }

        if (message == null || message.isBlank() || message.length() > 2000) {
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
    // VALIDATE CALENDAR RANGE
    // =========================================================

    private void validateCalendarRange(
            LocalDate from,
            LocalDate to
    ) {

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

        User currentUser = requireActor(actor);
        requireTenantAgentOrAdmin(currentUser);

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

        if (request.tenantIds().size() > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A maximum of 500 tenants can be selected."
            );
        }

        if (request.reminder().type() == null
                || request.reminder().type() == TenantReminder.ReminderType.CALENDAR) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Calendar events cannot be sent as bulk tenant reminders."
            );
        }

        List<Long> tenantIds = request.tenantIds().stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (tenantIds.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No valid tenant IDs were supplied."
            );
        }

        // Validate every recipient before saving any reminder.
        for (Long tenantId : tenantIds) {
            validateTenant(tenantId, currentUser);
        }

        CreateTenantReminderRequest original = request.reminder();

        List<TenantReminderResponse> results = new ArrayList<>();

        for (Long tenantId : tenantIds) {

            String key = clean(original.idempotencyKey());

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

            results.add(create(recipientRequest, currentUser.getEmail()));
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

        User currentUser = requireActor(actor);
        requireTenantAgentOrAdmin(currentUser);

        long count;

        if (isAdmin(currentUser)) {
            count = tenantRepository.findAll()
                    .stream()
                    .filter(t -> t.getStatus() == TenantResident.TenantStatus.ACTIVE)
                    .filter(t -> t.getOwner() != null
                            && t.getOwner().getRole() == Role.TENANT_AGENT)
                    .count();
        } else {
            count = tenantRepository.countByOwner_IdAndStatus(
                    currentUser.getId(),
                    TenantResident.TenantStatus.ACTIVE
            );
        }

        return new EligibleTenantCountResponse(Math.toIntExact(count));
    }

    // =========================================================
    // CREATE REMINDERS FOR ALL ELIGIBLE TENANTS
    // =========================================================

    @Transactional
    public BulkReminderResultResponse createForAllEligible(
            BulkTenantReminderRequest request,
            String actor
    ) {

        User currentUser = requireActor(actor);
        requireTenantAgentOrAdmin(currentUser);

        if (request == null || request.reminder() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reminder details are required."
            );
        }

        CreateTenantReminderRequest details = request.reminder();

        if (details.type() == null
                || details.type() == TenantReminder.ReminderType.CALENDAR) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Calendar events cannot be sent to all tenants."
            );
        }

        List<TenantResident> eligibleTenants;

        if (isAdmin(currentUser)) {
            eligibleTenants = tenantRepository.findAll()
                    .stream()
                    .filter(t -> t.getStatus() == TenantResident.TenantStatus.ACTIVE)
                    .filter(t -> t.getOwner() != null
                            && t.getOwner().getRole() == Role.TENANT_AGENT)
                    .toList();
        } else {
            eligibleTenants = tenantRepository.findByOwner_IdOrderByCreatedAtDesc(
                            currentUser.getId()
                    )
                    .stream()
                    .filter(t -> t.getStatus() == TenantResident.TenantStatus.ACTIVE)
                    .toList();
        }

        List<Long> tenantIds = eligibleTenants.stream()
                .map(TenantResident::getId)
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
                new BulkTenantReminderRequest(tenantIds, reminder);

        List<TenantReminderResponse> created =
                createBulk(bulkRequest, currentUser.getEmail());

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

        if (reminder.getTenantId() != null) {

            TenantResident resident = tenantRepository.findById(
                            reminder.getTenantId()
                    )
                    .orElse(null);

            if (resident != null && resident.getOwner() != null) {

                // Use the actual tenant owner, not the reminder creator.
                // This also supports administrator-created reminders.
                tenant = tenantResidentService.getById(
                        resident.getId(),
                        resident.getOwner().getEmail()
                );
            }
        }

        return TenantReminderResponse.from(reminder, tenant);
    }
}
