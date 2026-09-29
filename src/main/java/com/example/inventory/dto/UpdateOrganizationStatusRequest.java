package com.example.inventory.dto;

import com.example.inventory.entity.OrganizationStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrganizationStatusRequest(
        @NotNull(message = "Status is required (ACTIVE or SUSPENDED)")
        OrganizationStatus status
) {
}
