package com.example.inventory.controller;

import com.example.inventory.dto.AcceptInvitationRequest;
import com.example.inventory.dto.InvitationResponse;
import com.example.inventory.dto.PublicOrganizationResponse;
import com.example.inventory.dto.SubscriptionRequestCreateRequest;
import com.example.inventory.entity.Organization;
import com.example.inventory.exception.ResourceNotFoundException;
import com.example.inventory.repository.OrganizationRepository;
import com.example.inventory.service.InvitationService;
import com.example.inventory.service.SubscriptionRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Unauthenticated endpoints: the subscription request page, organization portal landing info,
 * and invitation acceptance.
 */
@RestController
@RequestMapping("/api/public")
@Tag(name = "Public", description = "Subscription requests, organization lookup and invitation acceptance")
public class PublicController {

    private final SubscriptionRequestService subscriptionRequestService;
    private final InvitationService invitationService;
    private final OrganizationRepository organizationRepository;

    public PublicController(SubscriptionRequestService subscriptionRequestService,
                            InvitationService invitationService,
                            OrganizationRepository organizationRepository) {
        this.subscriptionRequestService = subscriptionRequestService;
        this.invitationService = invitationService;
        this.organizationRepository = organizationRepository;
    }

    @PostMapping("/subscription-requests")
    @Operation(summary = "Request StockWise for an organization", description = "Emails StockWise; the super admin reviews it")
    public ResponseEntity<Map<String, String>> createSubscriptionRequest(
            @Valid @RequestBody SubscriptionRequestCreateRequest request) {
        subscriptionRequestService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Thanks! Your request was sent to StockWise. We will contact you at " + request.contactEmail()));
    }

    @GetMapping("/organizations/{slug}")
    @Operation(summary = "Look up an organization portal", description = "Returns name and slug for an ACTIVE organization, 404 otherwise")
    public ResponseEntity<PublicOrganizationResponse> getOrganization(@PathVariable String slug) {
        Organization organization = organizationRepository.findBySlug(slug.toLowerCase())
                .filter(Organization::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found: " + slug));
        return ResponseEntity.ok(PublicOrganizationResponse.fromEntity(organization));
    }

    @GetMapping("/invitations/{token}")
    @Operation(summary = "Get invitation details", description = "Used by the accept-invitation page")
    public ResponseEntity<InvitationResponse> getInvitation(@PathVariable String token) {
        return ResponseEntity.ok(invitationService.getInvitation(token));
    }

    @PostMapping("/invitations/{token}/accept")
    @Operation(summary = "Accept an invitation", description = "Sets name and password and activates the account; then log in on the organization portal")
    public ResponseEntity<InvitationResponse> acceptInvitation(
            @PathVariable String token,
            @Valid @RequestBody AcceptInvitationRequest request) {
        return ResponseEntity.ok(invitationService.acceptInvitation(token, request));
    }
}
