package com.example.inventory.dto;

import com.example.inventory.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Role is optional: defaults to ADMIN when the super admin invites, STAFF when an org admin invites. */
public record InviteUserRequest(
        @Size(max = 100)
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Valid email is required")
        String email,

        Role role
) {
}
