
package com.example.Credit.management.controller;

import com.example.Credit.management.DTO.TenantRecipientResponse;
import com.example.Credit.management.DTO.TenantResidentRequest;
import com.example.Credit.management.DTO.TenantResidentResponse;
import com.example.Credit.management.Model.TenantResident;
import com.example.Credit.management.services.TenantResidentService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/tenant/residents")
@Validated
public class TenantResidentController {

    private final TenantResidentService tenantResidentService;

    public TenantResidentController(
            TenantResidentService tenantResidentService) {
        this.tenantResidentService = tenantResidentService;
    }

    // =========================================================
    // AUTHENTICATION
    // =========================================================

    private String authenticatedEmail(Principal principal) {

        if (principal == null
                || principal.getName() == null
                || principal.getName().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required."
            );
        }

        return principal.getName().trim();
    }

    // =========================================================
    // SEARCH ELIGIBLE TENANTS
    // GET /api/tenant/residents/eligible?q=john
    // =========================================================

    @GetMapping("/eligible")
    public List<TenantRecipientResponse> searchEligibleTenants(
            @RequestParam(defaultValue = "") String q,
            Principal principal) {

        return tenantResidentService.searchEligibleTenants(
                q.trim(),
                authenticatedEmail(principal)
        );
    }

    // =========================================================
    // CREATE TENANT - JSON
    // POST /api/tenant/residents
    // =========================================================

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResidentResponse create(
            @Valid @RequestBody TenantResidentRequest request,
            Principal principal) {

        return tenantResidentService.create(
                request,
                authenticatedEmail(principal)
        );
    }

    // =========================================================
    // CREATE TENANT - MULTIPART WITH OPTIONAL IMAGE
    // POST /api/tenant/residents
    // =========================================================

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResidentResponse createWithImage(

            @Valid
            @RequestPart("request")
            TenantResidentRequest request,

            @RequestPart(value = "image", required = false)
            MultipartFile image,

            Principal principal) {

        return tenantResidentService.create(
                request,
                image,
                authenticatedEmail(principal)
        );
    }

    // =========================================================
    // GET ALL TENANTS
    // =========================================================

    @GetMapping
    public List<TenantResidentResponse> getAll(
            Principal principal) {

        return tenantResidentService.getAll(
                authenticatedEmail(principal)
        );
    }

    // =========================================================
    // GET SINGLE TENANT
    // =========================================================

    @GetMapping("/{tenantId}")
    public TenantResidentResponse getById(
            @PathVariable Long tenantId,
            Principal principal) {

        return tenantResidentService.getById(
                tenantId,
                authenticatedEmail(principal)
        );
    }

    // =========================================================
    // UPDATE TENANT - RETAIN EXISTING IMAGE
    // =========================================================

    @PutMapping(
            value = "/{tenantId}",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public TenantResidentResponse update(
            @PathVariable Long tenantId,

            @Valid
            @RequestBody TenantResidentRequest request,

            Principal principal) {

        return tenantResidentService.update(
                tenantId,
                request,
                authenticatedEmail(principal)
        );
    }

    // =========================================================
    // UPDATE TENANT - REPLACE IMAGE
    // =========================================================
    @PutMapping(
            value = "/{tenantId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public TenantResidentResponse updateWithImage(

            @PathVariable Long tenantId,

            @Valid
            @RequestPart("request")
            TenantResidentRequest request,

            @RequestPart(value = "image", required = false)
            MultipartFile image,

            Principal principal) {

        return tenantResidentService.update(
                tenantId,
                request,
                image,
                authenticatedEmail(principal)
        );
    }

    // =========================================================
    // UPDATE TENANT STATUS
    // =========================================================

    @PatchMapping("/{tenantId}/status")
    public TenantResidentResponse updateStatus(

            @PathVariable Long tenantId,

            @Valid
            @RequestBody StatusRequest request,

            Principal principal) {

        return tenantResidentService.updateStatus(
                tenantId,
                request.status(),
                authenticatedEmail(principal)
        );
    }

    // =========================================================
    // STATUS REQUEST
    // =========================================================

    public record StatusRequest(

            @NotNull(message = "Tenant status is required.")
            TenantResident.TenantStatus status

    ) {}
}
