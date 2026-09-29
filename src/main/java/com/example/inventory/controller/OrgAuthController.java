package com.example.inventory.controller;

import com.example.inventory.dto.AuthResponse;
import com.example.inventory.dto.LoginRequest;
import com.example.inventory.dto.RegisterRequest;
import com.example.inventory.dto.RegistrationResponse;
import com.example.inventory.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-in and sign-up on an organization's own portal ({frontend}/o/{orgSlug}).
 */
@RestController
@RequestMapping("/api/orgs/{orgSlug}/auth")
@Tag(name = "Organization Authentication", description = "Login and registration on an organization's portal")
public class OrgAuthController {

    private final AuthService authService;

    public OrgAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register for an organization", description = "Creates a PENDING staff account; the organization admin must approve it before the user can log in")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Login to an organization", description = "Only ACTIVE members of this organization can log in; returns JWT bearer tokens")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
