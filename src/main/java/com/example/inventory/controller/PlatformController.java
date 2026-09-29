package com.example.inventory.controller;

import com.example.inventory.dto.AuthResponse;
import com.example.inventory.dto.CreateOrganizationRequest;
import com.example.inventory.dto.InviteUserRequest;
import com.example.inventory.dto.LoginRequest;
import com.example.inventory.dto.OrganizationResponse;
import com.example.inventory.dto.ReviewNoteRequest;
import com.example.inventory.dto.SubscriptionRequestResponse;
import com.example.inventory.dto.UpdateOrganizationStatusRequest;
import com.example.inventory.dto.UserResponse;
import com.example.inventory.entity.SubscriptionRequestStatus;
import com.example.inventory.service.AuthService;
import com.example.inventory.service.PlatformOrganizationService;
import com.example.inventory.service.SubscriptionRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * StockWise super admin portal. Everything except /auth/login requires ROLE_SUPER_ADMIN (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/platform")
@Tag(name = "Platform (Super Admin)", description = "StockWise owner: review subscription requests, create organizations, invite org admins")
public class PlatformController {

    private final AuthService authService;
    private final SubscriptionRequestService subscriptionRequestService;
    private final PlatformOrganizationService platformOrganizationService;

    public PlatformController(AuthService authService,
                              SubscriptionRequestService subscriptionRequestService,
                              PlatformOrganizationService platformOrganizationService) {
        this.authService = authService;
        this.subscriptionRequestService = subscriptionRequestService;
        this.platformOrganizationService = platformOrganizationService;
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Super admin login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.loginSuperAdmin(request));
    }

    @GetMapping("/subscription-requests")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List subscription requests", description = "Optional ?status=PENDING|APPROVED|REJECTED")
    public ResponseEntity<List<SubscriptionRequestResponse>> listSubscriptionRequests(
            @RequestParam(required = false) SubscriptionRequestStatus status) {
        return ResponseEntity.ok(subscriptionRequestService.list(status));
    }

    @PostMapping("/subscription-requests/{id}/reject")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Reject a subscription request", description = "The requester is emailed, with the optional note")
    public ResponseEntity<SubscriptionRequestResponse> rejectSubscriptionRequest(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ReviewNoteRequest request) {
        return ResponseEntity.ok(subscriptionRequestService.reject(id, request != null ? request.note() : null));
    }

    @PostMapping("/organizations")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create an organization",
            description = "Creates the organization and its portal URL. Pass subscriptionRequestId to approve a request, and adminEmail to email an org admin invitation")
    public ResponseEntity<OrganizationResponse> createOrganization(@Valid @RequestBody CreateOrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(platformOrganizationService.createOrganization(request));
    }

    @GetMapping("/organizations")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List organizations")
    public ResponseEntity<List<OrganizationResponse>> listOrganizations() {
        return ResponseEntity.ok(platformOrganizationService.listOrganizations());
    }

    @GetMapping("/organizations/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get organization")
    public ResponseEntity<OrganizationResponse> getOrganization(@PathVariable Long id) {
        return ResponseEntity.ok(platformOrganizationService.getOrganization(id));
    }

    @PatchMapping("/organizations/{id}/status")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Activate or suspend an organization", description = "Suspended organizations cannot log in or use their portal")
    public ResponseEntity<OrganizationResponse> updateOrganizationStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateOrganizationStatusRequest request) {
        return ResponseEntity.ok(platformOrganizationService.updateStatus(id, request.status()));
    }

    @GetMapping("/organizations/{id}/admins")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List an organization's admins")
    public ResponseEntity<List<UserResponse>> listAdmins(@PathVariable Long id) {
        return ResponseEntity.ok(platformOrganizationService.listAdmins(id));
    }

    @PostMapping("/organizations/{id}/admin-invitations")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Invite an organization admin", description = "Emails a link to set a password and become ADMIN of this organization")
    public ResponseEntity<Map<String, String>> inviteAdmin(
            @PathVariable Long id,
            @Valid @RequestBody InviteUserRequest request) {
        platformOrganizationService.inviteAdmin(id, request);
        return ResponseEntity.ok(Map.of("message", "Admin invitation sent to " + request.email()));
    }
}
