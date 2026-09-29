package com.example.inventory.dto;

import com.example.inventory.entity.Organization;
import com.example.inventory.entity.OrganizationStatus;

import java.time.LocalDateTime;

public record OrganizationResponse(
        Long id,
        String name,
        String slug,
        String description,
        String contactEmail,
        OrganizationStatus status,
        boolean setupCompleted,
        String portalUrl,
        LocalDateTime createdAt
) {
    public static OrganizationResponse fromEntity(Organization organization, String portalUrl) {
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getSlug(),
                organization.getDescription(),
                organization.getContactEmail(),
                organization.getStatus(),
                organization.isSetupCompleted(),
                portalUrl,
                organization.getCreatedAt()
        );
    }
}
