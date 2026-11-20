
package com.example.Credit.management.DTO;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TenantUnitRequest(

        @NotBlank(message = "Unit number is required.")
        @Size(max = 40)
        String unitNumber,

        @Size(max = 100)
        String floor,

        @Size(max = 100)
        String unitType,

        @NotNull(message = "Monthly rent is required.")
        @DecimalMin(value = "0.00", inclusive = false,
                message = "Monthly rent must be greater than zero.")
        BigDecimal monthlyRent,

        @NotNull(message = "Deposit amount is required.")
        @DecimalMin(value = "0.00", message = "Deposit cannot be negative.")
        BigDecimal depositAmount,

        @Size(max = 5000)
        String description
) {
}
