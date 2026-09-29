package com.example.inventory.controller;

import com.example.inventory.dto.RefreshTokenRequest;
import com.example.inventory.dto.TokenRefreshResponse;
import com.example.inventory.exception.BadRequestException;
import com.example.inventory.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Token refresh and logout, shared by organization users and the super admin. Tokens travel only in JSON
 * bodies and the Authorization header, never in cookies, so these endpoints cannot be triggered cross-site (CSRF).
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Token refresh and logout (shared by organization users and the super admin). Login lives under /api/orgs/{orgSlug}/auth and /api/platform/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Generates a fresh access token from the refresh token in the JSON body")
    public ResponseEntity<TokenRefreshResponse> refreshToken(@RequestBody(required = false) RefreshTokenRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            throw new BadRequestException("Refresh token is required");
        }
        return ResponseEntity.ok(authService.refreshToken(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Revokes the refresh token of this session")
    public ResponseEntity<Map<String, String>> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        if (request != null && request.getRefreshToken() != null && !request.getRefreshToken().isBlank()) {
            authService.logout(request.getRefreshToken());
        }
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
