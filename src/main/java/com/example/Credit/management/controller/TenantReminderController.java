
package com.example.Credit.management.controller;

import com.example.Credit.management.DTO.*;
import com.example.Credit.management.Model.TenantReminder;
import com.example.Credit.management.services.AutomaticRentReminderService;
import com.example.Credit.management.services.TenantReminderService;
import com.example.Credit.management.services.TenantRentReminderEligibilityService;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/tenant/reminders")
public class TenantReminderController {

    private final TenantReminderService service;
    private final TenantRentReminderEligibilityService eligibilityService;
    private final AutomaticRentReminderService automaticRentReminderService;

    public TenantReminderController(
            TenantReminderService service,
            TenantRentReminderEligibilityService eligibilityService, AutomaticRentReminderService automaticRentReminderService
    ) {
        this.service = service;
        this.eligibilityService = eligibilityService;
        this.automaticRentReminderService = automaticRentReminderService;
    }

    // Create one reminder
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantReminderResponse create(
            @Valid @RequestBody CreateTenantReminderRequest request,
            Authentication authentication
    ) {
        return service.create(request, actor(authentication));
    }

    // Create reminders for selected tenants
    @PostMapping("/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public List<TenantReminderResponse> createBulk(
            @Valid @RequestBody BulkTenantReminderRequest request,
            Authentication authentication
    ) {
        return service.createBulk(request, actor(authentication));
    }

    // List reminders with pagination
    @GetMapping
    public Page<TenantReminderResponse> list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page cannot be negative."
            );
        }

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must be between 1 and 100."
            );
        }

        return service.list(actor(authentication), page, size);
    }

    // Find tenants with rent reminders due.
    // Categories: ALL, DUE_SOON, DUE_TODAY, OVERDUE
    @GetMapping("/eligible")
    public List<RentReminderCandidateResponse> eligibleRentRecipients(
            Authentication authentication,
            @RequestParam(defaultValue = "ALL") String category,
            @RequestParam(defaultValue = "7") int daysAhead
    ) {
        return eligibilityService.findEligible(
                actor(authentication),
                category,
                daysAhead
        );
    }

    // Count distinct eligible tenants for the selected rent category.
    @GetMapping("/eligible-count")
    public EligibleTenantCountResponse eligibleRentRecipientCount(
            Authentication authentication,
            @RequestParam(defaultValue = "ALL") String category,
            @RequestParam(defaultValue = "7") int daysAhead
    ) {
        int count = Math.toIntExact(
                eligibilityService.findEligible(
                                actor(authentication),
                                category,
                                daysAhead
                        )
                        .stream()
                        .map(RentReminderCandidateResponse::tenantId)
                        .distinct()
                        .count()
        );

        return new EligibleTenantCountResponse(count);
    }



    @PostMapping("/auto-generate")
    public BulkReminderResultResponse generateRentReminders(
            Authentication authentication,
            @RequestParam(defaultValue = "ALL") String category,
            @RequestParam(defaultValue = "7") int daysAhead,
            @RequestParam(
                    defaultValue = "IN_APP"
            ) TenantReminder.ReminderChannel channel
    ) {
        int created = automaticRentReminderService.generate(
                actor(authentication),
                category,
                daysAhead,
                channel
        );

        return new BulkReminderResultResponse(created);
    }


    // Get a reminder by ID
    @GetMapping("/{id}")
    public TenantReminderResponse get(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return service.get(id, actor(authentication));
    }

    // Cancel a reminder
    @PostMapping("/{id}/cancel")
    public TenantReminderResponse cancel(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return service.cancel(id, actor(authentication));
    }

    // Mark a reminder as followed up
    @PostMapping("/{id}/follow-up")
    public TenantReminderResponse followUp(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return service.markFollowedUp(id, actor(authentication));
    }

    // Request a retry for a failed reminder
    @PostMapping("/{id}/retry")
    public TenantReminderResponse retry(
            @PathVariable Long id,
            Authentication authentication
    ) {
        return service.requestRetry(id, actor(authentication));
    }

    private String actor(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required."
            );
        }

        return authentication.getName();
    }

    // =====================================================
// CALENDAR EVENTS
// =====================================================

    @GetMapping("/calendar")
    public List<TenantReminderResponse> calendar(
            Authentication authentication,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        return service.getCalendarEvents(
                actor(authentication),
                from,
                to
        );
    }


    @PutMapping("/{id}")
    public TenantReminderResponse update(
            @PathVariable Long id,
            @Valid @RequestBody CreateTenantReminderRequest request,
            Authentication authentication
    ) {
        return service.updateCalendarEvent(
                id,
                request,
                actor(authentication)
        );
    }
}
