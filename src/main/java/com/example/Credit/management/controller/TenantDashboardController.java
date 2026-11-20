
package com.example.Credit.management.controller;

import com.example.Credit.management.DTO.TenantDashboardResponse;
import com.example.Credit.management.services.TenantDashboardService;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/tenant/dashboard")
public class TenantDashboardController {

    private final TenantDashboardService dashboardService;

    public TenantDashboardController(
            TenantDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public TenantDashboardResponse getDashboard(
            Principal principal,

            @RequestParam("from")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam("to")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to) {

        return dashboardService.getDashboard(
                principal.getName(),
                from,
                to
        );
    }
}
