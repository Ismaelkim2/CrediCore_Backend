package com.example.Credit.management.DTO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BulkTenantReminderRequest(
        @NotEmpty(message = "Select at least one tenant.")
        @Size(max = 500, message = "A maximum of 500 tenants can be selected per request.")
        List<@NotNull Long> tenantIds,

        @NotNull(message = "Reminder details are required.")
        @Valid
        CreateTenantReminderRequest reminder
) {}