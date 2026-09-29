package com.example.inventory.dto;

import com.example.inventory.entity.SubscriptionRequest;
import com.example.inventory.entity.SubscriptionRequestStatus;

import java.time.LocalDateTime;

public record SubscriptionRequestResponse(
        Long id,
        String organizationName,
        String contactName,
        String contactEmail,
        String contactPhone,
        boolean hasExistingData,
        String message,
        SubscriptionRequestStatus status,
        String reviewNote,
        Long organizationId,
        String organizationSlug,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt
) {
    public static SubscriptionRequestResponse fromEntity(SubscriptionRequest request) {
        return new SubscriptionRequestResponse(
                request.getId(),
                request.getOrganizationName(),
                request.getContactName(),
                request.getContactEmail(),
                request.getContactPhone(),
                request.isHasExistingData(),
                request.getMessage(),
                request.getStatus(),
                request.getReviewNote(),
                request.getOrganization() != null ? request.getOrganization().getId() : null,
                request.getOrganization() != null ? request.getOrganization().getSlug() : null,
                request.getCreatedAt(),
                request.getReviewedAt()
        );
    }
}
