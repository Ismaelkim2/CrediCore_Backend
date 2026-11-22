
package com.example.Credit.management.controller;

import com.example.Credit.management.DTO.TenantDashboardResponse;
import com.example.Credit.management.services.TenantDashboardService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/tenant/dashboard")
public class TenantDashboardController {

    private final TenantDashboardService dashboardService;

    public TenantDashboardController(
            TenantDashboardService dashboardService
    ) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public TenantDashboardResponse getDashboard(
            Authentication authentication,

            @RequestParam("from")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam("to")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to
    ) {

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required."
            );
        }

        if (from == null || to == null || from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid dashboard reporting date range."
            );
        }

        if (from.isBefore(LocalDate.now().minusYears(5))) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Dashboard reporting range cannot exceed five years."
            );
        }

        if (to.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "The reporting end date cannot be in the future."
            );
        }

        return dashboardService.getDashboard(
                authentication.getName().trim(),
                from,
                to
        );
    }
}
