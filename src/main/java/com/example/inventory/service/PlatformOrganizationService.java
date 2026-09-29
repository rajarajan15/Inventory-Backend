package com.example.inventory.service;

import com.example.inventory.dto.CreateOrganizationRequest;
import com.example.inventory.dto.InviteUserRequest;
import com.example.inventory.dto.OrganizationResponse;
import com.example.inventory.dto.UserResponse;
import com.example.inventory.entity.Organization;
import com.example.inventory.entity.OrganizationStatus;
import com.example.inventory.entity.Role;
import com.example.inventory.entity.SubscriptionRequest;
import com.example.inventory.entity.SubscriptionRequestStatus;
import com.example.inventory.exception.BadRequestException;
import com.example.inventory.exception.ResourceNotFoundException;
import com.example.inventory.repository.OrganizationRepository;
import com.example.inventory.repository.SubscriptionRequestRepository;
import com.example.inventory.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Super admin operations on organizations. Deliberately exposes no inventory data (products, categories,
 * stock) so each organization's business data stays private to that organization.
 */
@Service
public class PlatformOrganizationService {

    /** Slugs that would collide with frontend routes. */
    private static final Set<String> RESERVED_SLUGS = Set.of(
            "api", "platform", "admin", "invite", "register", "request", "login", "www", "app", "stockwise");

    private final OrganizationRepository organizationRepository;
    private final SubscriptionRequestRepository subscriptionRequestRepository;
    private final SubscriptionRequestService subscriptionRequestService;
    private final UserRepository userRepository;
    private final InvitationService invitationService;
    private final NotificationService notificationService;

    public PlatformOrganizationService(OrganizationRepository organizationRepository,
                                       SubscriptionRequestRepository subscriptionRequestRepository,
                                       SubscriptionRequestService subscriptionRequestService,
                                       UserRepository userRepository,
                                       InvitationService invitationService,
                                       NotificationService notificationService) {
        this.organizationRepository = organizationRepository;
        this.subscriptionRequestRepository = subscriptionRequestRepository;
        this.subscriptionRequestService = subscriptionRequestService;
        this.userRepository = userRepository;
        this.invitationService = invitationService;
        this.notificationService = notificationService;
    }

    /**
     * Creates an organization (and so its portal URL). If it comes from a subscription request, that request is
     * marked APPROVED. If an admin email is given, that person is emailed an invitation to become the org admin.
     */
    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request) {
        String slug = request.slug().trim().toLowerCase();
        if (RESERVED_SLUGS.contains(slug)) {
            throw new BadRequestException("Slug '" + slug + "' is reserved. Please choose another.");
        }
        if (organizationRepository.existsBySlug(slug)) {
            throw new BadRequestException("Slug '" + slug + "' is already taken");
        }

        SubscriptionRequest subscriptionRequest = request.subscriptionRequestId() != null
                ? subscriptionRequestService.findPending(request.subscriptionRequestId())
                : null;

        String contactEmail = request.contactEmail() != null ? request.contactEmail()
                : subscriptionRequest != null ? subscriptionRequest.getContactEmail() : null;

        Organization organization = new Organization(
                request.name().trim(), slug, request.description(), AuthService.normalizeEmail(contactEmail));
        Organization saved = organizationRepository.save(organization);

        if (subscriptionRequest != null) {
            subscriptionRequest.setStatus(SubscriptionRequestStatus.APPROVED);
            subscriptionRequest.setOrganization(saved);
            subscriptionRequest.setReviewedAt(LocalDateTime.now());
            subscriptionRequestRepository.save(subscriptionRequest);
        }

        if (request.adminEmail() != null && !request.adminEmail().isBlank()) {
            invitationService.invite(saved, request.adminName(), request.adminEmail(), Role.ADMIN);
        }

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> listOrganizations() {
        return organizationRepository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrganizationResponse getOrganization(Long id) {
        return toResponse(find(id));
    }

    @Transactional
    public OrganizationResponse updateStatus(Long id, OrganizationStatus status) {
        Organization organization = find(id);
        organization.setStatus(status);
        return toResponse(organizationRepository.save(organization));
    }

    /** Invite another admin for an organization (e.g. the first invite expired or a second admin is needed). */
    @Transactional
    public void inviteAdmin(Long organizationId, InviteUserRequest request) {
        invitationService.invite(find(organizationId), request.name(), request.email(), Role.ADMIN);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listAdmins(Long organizationId) {
        find(organizationId);
        return userRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .filter(user -> user.getRole() == Role.ADMIN)
                .map(UserResponse::fromEntity)
                .toList();
    }

    private Organization find(Long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with id: " + id));
    }

    private OrganizationResponse toResponse(Organization organization) {
        return OrganizationResponse.fromEntity(organization, notificationService.portalUrl(organization));
    }
}
