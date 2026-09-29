package com.example.inventory.dto;

import com.example.inventory.entity.UserStatus;

public record RegistrationResponse(String message, String email, UserStatus status) {
}
