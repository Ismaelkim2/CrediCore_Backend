
package com.example.Credit.management.controller;

import com.example.Credit.management.DTO.TenantPropertyRequest;
import com.example.Credit.management.DTO.TenantPropertyResponse;
import com.example.Credit.management.Model.TenantProperty;
import com.example.Credit.management.services.TenantPropertyService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/tenant/properties")
@Validated
public class TenantPropertyController {

    private final TenantPropertyService propertyService;

    public TenantPropertyController(
            TenantPropertyService propertyService) {
        this.propertyService = propertyService;
    }

    // CREATE PROPERTY WITH OPTIONAL IMAGE
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TenantPropertyResponse create(
            Authentication authentication,

            @Valid @ModelAttribute TenantPropertyRequest request,

            @RequestPart(value = "image", required = false)
            MultipartFile image) {

        return propertyService.create(
                authentication.getName(),
                request,
                image
        );
    }

    // LIST OWNER PROPERTIES
    @GetMapping
    public List<TenantPropertyResponse> list(
            Authentication authentication) {

        return propertyService.list(authentication.getName());
    }

    // GET PROPERTY BY ID
    @GetMapping("/{id}")
    public TenantPropertyResponse getById(
            Authentication authentication,
            @PathVariable Long id) {

        return propertyService.getById(
                authentication.getName(),
                id
        );
    }

    // UPDATE PROPERTY WITH OPTIONAL NEW IMAGE
    @PutMapping(
            value = "/{id}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public TenantPropertyResponse update(
            Authentication authentication,

            @PathVariable Long id,

            @Valid @ModelAttribute TenantPropertyRequest request,

            @RequestPart(value = "image", required = false)
            MultipartFile image) {

        return propertyService.update(
                authentication.getName(),
                id,
                request,
                image
        );
    }

    // PROPERTY STATUS REQUEST
    public record StatusRequest(
            @NotNull TenantProperty.PropertyStatus status
    ) {}

    // UPDATE PROPERTY STATUS
    @PatchMapping("/{id}/status")
    public TenantPropertyResponse updateStatus(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody StatusRequest request) {

        return propertyService.updateStatus(
                authentication.getName(),
                id,
                request.status()
        );
    }
}
