package com.example.inventory.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubscriptionRequestCreateRequest(
        @NotBlank(message = "Organization name is required")
        @Size(max = 255)
        String organizationName,

        @NotBlank(message = "Contact name is required")
        @Size(max = 100)
        String contactName,

        @NotBlank(message = "Contact email is required")
        @Email(message = "Valid contact email is required")
        String contactEmail,

        @Size(max = 50)
        String contactPhone,

        Boolean hasExistingData,

        @Size(max = 2000)
        String message
) {
}
