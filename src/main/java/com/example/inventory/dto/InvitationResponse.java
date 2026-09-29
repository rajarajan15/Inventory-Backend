package com.example.inventory.dto;

import com.example.inventory.entity.Invitation;
import com.example.inventory.entity.Role;

import java.time.LocalDateTime;

public record InvitationResponse(
        String email,
        String name,
        Role role,
        String organizationName,
        String organizationSlug,
        LocalDateTime expiresAt
) {
    public static InvitationResponse fromEntity(Invitation invitation) {
        return new InvitationResponse(
                invitation.getEmail(),
                invitation.getName(),
                invitation.getRole(),
                invitation.getOrganization().getName(),
                invitation.getOrganization().getSlug(),
                invitation.getExpiresAt()
        );
    }
}
