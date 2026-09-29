package com.example.inventory.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Super admin creates an organization (optionally from a subscription request) and, if adminEmail
 * is given, emails that person an invitation to become the organization admin.
 */
public record CreateOrganizationRequest(
        @NotBlank(message = "Organization name is required")
        @Size(max = 255)
        String name,

        @NotBlank(message = "Slug is required")
        @Pattern(regexp = "^[a-z0-9](?:[a-z0-9-]{1,61}[a-z0-9])$",
                message = "Slug must be 3-63 characters: lowercase letters, digits and hyphens, not starting or ending with a hyphen")
        String slug,

        @Size(max = 1000)
        String description,

        @Email(message = "Valid contact email is required")
        String contactEmail,

        @Size(max = 100)
        String adminName,

        @Email(message = "Valid admin email is required")
        String adminEmail,

        Long subscriptionRequestId
) {
}
