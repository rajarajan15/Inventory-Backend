package com.example.inventory.dto;

import com.example.inventory.entity.Organization;

/** Minimal organization info the public login/register pages need; exposes no inventory data. */
public record PublicOrganizationResponse(String name, String slug) {
    public static PublicOrganizationResponse fromEntity(Organization organization) {
        return new PublicOrganizationResponse(organization.getName(), organization.getSlug());
    }
}
