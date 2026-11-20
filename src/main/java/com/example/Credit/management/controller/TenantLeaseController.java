package com.example.Credit.management.controller;
import com.example.Credit.management.DTO.TenantLeaseRequest;
import com.example.Credit.management.DTO.TenantLeaseResponse;
import com.example.Credit.management.services.TenantLeaseService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/tenant/leases")
public class TenantLeaseController {

    private final TenantLeaseService leaseService;

    public TenantLeaseController(TenantLeaseService leaseService) {
        this.leaseService = leaseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantLeaseResponse create(
            @Valid @RequestBody TenantLeaseRequest request,
            Principal principal) {
        return leaseService.create(request, principal.getName());
    }

    @GetMapping
    public List<TenantLeaseResponse> getAll(Principal principal) {
        return leaseService.getAll(principal.getName());
    }

    @GetMapping("/{leaseId}")
    public TenantLeaseResponse getById(
            @PathVariable Long leaseId,
            Principal principal) {
        return leaseService.getById(leaseId, principal.getName());
    }

    @PatchMapping("/{leaseId}/activate")
    public TenantLeaseResponse activate(
            @PathVariable Long leaseId,
            Principal principal) {
        return leaseService.activate(leaseId, principal.getName());
    }

    @PatchMapping("/{leaseId}/terminate")
    public TenantLeaseResponse terminate(
            @PathVariable Long leaseId,
            Principal principal) {
        return leaseService.terminate(leaseId, principal.getName());
    }
}