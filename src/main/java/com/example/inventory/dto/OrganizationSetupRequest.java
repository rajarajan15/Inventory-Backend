package com.example.inventory.dto;

import jakarta.validation.constraints.NotNull;

/**
 * First-login choice for an org admin: start fresh (false) or bring existing data via CSV import (true).
 */
public record OrganizationSetupRequest(
        @NotNull(message = "hasExistingData is required")
        Boolean hasExistingData
) {
}
