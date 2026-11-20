
package com.example.Credit.management.controller;

import com.example.Credit.management.DTO.TenantUnitRequest;
import com.example.Credit.management.DTO.TenantUnitResponse;
import com.example.Credit.management.Model.TenantUnit;
import com.example.Credit.management.services.TenantUnitService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/tenant/properties/{propertyId}/units")
@Validated
public class TenantUnitController {

    private final TenantUnitService tenantUnitService;

    public TenantUnitController(TenantUnitService tenantUnitService) {
        this.tenantUnitService = tenantUnitService;
    }

    // CREATE UNIT - EXISTING JSON REQUEST
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TenantUnitResponse create(
            Principal principal,
            @PathVariable Long propertyId,
            @Valid @RequestBody TenantUnitRequest request) {

        return tenantUnitService.create(
                principal.getName(),
                propertyId,
                request
        );
    }

    // CREATE UNIT - MULTIPART REQUEST WITH OPTIONAL IMAGE
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TenantUnitResponse createWithImage(
            Principal principal,
            @PathVariable Long propertyId,
            @Valid @RequestPart("request") TenantUnitRequest request,
            @RequestPart(value = "image", required = false)
            MultipartFile image) {

        return tenantUnitService.create(
                principal.getName(),
                propertyId,
                request,
                image
        );
    }

    // LIST UNITS
    @GetMapping
    public List<TenantUnitResponse> list(
            Principal principal,
            @PathVariable Long propertyId) {

        return tenantUnitService.listByProperty(
                principal.getName(),
                propertyId
        );
    }

    // GET SINGLE UNIT
    @GetMapping("/{unitId}")
    public TenantUnitResponse getById(
            Principal principal,
            @PathVariable Long propertyId,
            @PathVariable Long unitId) {

        return tenantUnitService.getById(
                principal.getName(),
                propertyId,
                unitId
        );
    }

    // UPDATE UNIT - EXISTING JSON REQUEST
    @PutMapping(
            value = "/{unitId}",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    public TenantUnitResponse update(
            Principal principal,
            @PathVariable Long propertyId,
            @PathVariable Long unitId,
            @Valid @RequestBody TenantUnitRequest request) {

        return tenantUnitService.update(
                principal.getName(),
                propertyId,
                unitId,
                request
        );
    }

    // UPDATE UNIT - MULTIPART REQUEST WITH OPTIONAL IMAGE
    @PutMapping(
            value = "/{unitId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public TenantUnitResponse updateWithImage(
            Principal principal,
            @PathVariable Long propertyId,
            @PathVariable Long unitId,
            @Valid @RequestPart("request") TenantUnitRequest request,
            @RequestPart(value = "image", required = false)
            MultipartFile image) {

        return tenantUnitService.update(
                principal.getName(),
                propertyId,
                unitId,
                request,
                image
        );
    }

    // UPDATE UNIT STATUS
    @PatchMapping("/{unitId}/status")
    public TenantUnitResponse updateStatus(
            Principal principal,
            @PathVariable Long propertyId,
            @PathVariable Long unitId,
            @Valid @RequestBody StatusRequest request) {

        return tenantUnitService.updateStatus(
                principal.getName(),
                propertyId,
                unitId,
                request.status()
        );
    }

    public record StatusRequest(
            @NotNull(message = "Unit status is required.")
            TenantUnit.UnitStatus status) {
    }
}
