
package com.example.Credit.management.DTO;

import com.example.Credit.management.Model.TenantResident;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TenantResidentRequest(

        // TENANT FULL NAME
        @NotBlank(message = "Tenant full name is required.")
        @Size(
                min = 5,
                max = 120,
                message = "Full name must be between 5 and 120 characters."
        )
        @Pattern(
                regexp = "^[\\p{L}]+(?:[ '\\-’][\\p{L}]+)+$",
                message = "Enter the tenant's full name using at least two names. Numbers and special characters are not allowed."
        )
        String fullName,

        // IDENTIFICATION TYPE
        @NotNull(message = "Identification type is required.")
        TenantResident.IdentificationType identificationType,

        // IDENTIFICATION NUMBER
        @NotBlank(message = "Identification number is required.")
        @Size(
                min = 5,
                max = 40,
                message = "Identification number must be between 5 and 40 characters."
        )
        @Pattern(
                regexp = "^[A-Za-z0-9-]+$",
                message = "Identification number can contain only letters, numbers and hyphens."
        )
        String identificationNumber,

        // TENANT PHONE NUMBER
        @NotBlank(message = "Tenant phone number is required.")
        @Size(
                max = 25,
                message = "Phone number cannot exceed 25 characters."
        )
        @Pattern(
                regexp = "^(?:0[17][0-9]{2}[ -]?[0-9]{3}[ -]?[0-9]{3}|\\+254[17][0-9]{2}[ -]?[0-9]{3}[ -]?[0-9]{3})$",
                message = "Enter a valid Kenyan mobile number, e.g. 0712345678 or +254712345678."
        )
        String phoneNumber,

        // EMAIL ADDRESS - OPTIONAL, BUT MUST BE VALID WHEN PROVIDED
        @Size(
                max = 254,
                message = "Email address cannot exceed 254 characters."
        )
        @Email(
                message = "Enter a valid email address."
        )
        String email,

        // PHYSICAL / POSTAL ADDRESS
        @Size(
                max = 250,
                message = "Address cannot exceed 250 characters."
        )
        String address,

        // EMERGENCY CONTACT NAME
        @NotBlank(message = "Emergency contact name is required.")
        @Size(
                min = 5,
                max = 120,
                message = "Emergency contact name must be between 5 and 120 characters."
        )
        @Pattern(
                regexp = "^[\\p{L}]+(?:[ '\\-’][\\p{L}]+)+$",
                message = "Enter the emergency contact's full name using at least two names."
        )
        String emergencyContactName,

        // EMERGENCY CONTACT PHONE
        @NotBlank(message = "Emergency contact phone number is required.")
        @Size(
                max = 25,
                message = "Emergency contact phone number cannot exceed 25 characters."
        )
        @Pattern(
                regexp = "^(?:0[17][0-9]{2}[ -]?[0-9]{3}[ -]?[0-9]{3}|\\+254[17][0-9]{2}[ -]?[0-9]{3}[ -]?[0-9]{3})$",
                message = "Enter a valid Kenyan emergency contact number, e.g. 0712345678 or +254712345678."
        )
        String emergencyContactPhone,

        // ADDITIONAL NOTES
        @Size(
                max = 2000,
                message = "Notes cannot exceed 2000 characters."
        )
        String notes

) {
}
