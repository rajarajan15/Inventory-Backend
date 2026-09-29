package com.example.inventory.service;

import com.example.inventory.dto.InviteUserRequest;
import com.example.inventory.dto.UserResponse;
import com.example.inventory.entity.Organization;
import com.example.inventory.entity.Role;
import com.example.inventory.entity.User;
import com.example.inventory.entity.UserStatus;
import com.example.inventory.exception.BadRequestException;
import com.example.inventory.exception.ResourceNotFoundException;
import com.example.inventory.repository.OrganizationRepository;
import com.example.inventory.repository.UserRepository;
import com.example.inventory.security.CurrentUser;
import com.example.inventory.security.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * User management inside the current organization (org admin only).
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final InvitationService invitationService;
    private final NotificationService notificationService;
    private final RefreshTokenService refreshTokenService;

    public UserService(UserRepository userRepository,
                       OrganizationRepository organizationRepository,
                       InvitationService invitationService,
                       NotificationService notificationService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.invitationService = invitationService;
        this.notificationService = notificationService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers(UserStatus status) {
        Long orgId = TenantContext.requireOrganizationId();
        List<User> users = status == null
                ? userRepository.findByOrganizationIdOrderByCreatedAtDesc(orgId)
                : userRepository.findByOrganizationIdAndStatusOrderByCreatedAtDesc(orgId, status);
        return users.stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        return UserResponse.fromEntity(findInOrganization(id));
    }

    @Transactional
    public UserResponse approveUser(Long id) {
        User user = findInOrganization(id);
        if (user.getStatus() != UserStatus.PENDING && user.getStatus() != UserStatus.REJECTED) {
            throw new BadRequestException("Only pending or rejected users can be approved (current status: " + user.getStatus() + ")");
        }
        user.setStatus(UserStatus.ACTIVE);
        User saved = userRepository.save(user);
        notificationService.userApproved(saved, currentOrganization());
        return UserResponse.fromEntity(saved);
    }

    @Transactional
    public UserResponse rejectUser(Long id) {
        User user = findInOrganization(id);
        if (user.getStatus() != UserStatus.PENDING) {
            throw new BadRequestException("Only pending users can be rejected (current status: " + user.getStatus() + ")");
        }
        user.setStatus(UserStatus.REJECTED);
        User saved = userRepository.save(user);
        notificationService.userRejected(saved, currentOrganization());
        return UserResponse.fromEntity(saved);
    }

    @Transactional
    public UserResponse disableUser(Long id) {
        User user = findInOrganization(id);
        assertNotSelf(user);
        user.setStatus(UserStatus.DISABLED);
        refreshTokenService.deleteByUserId(user.getId()); // signs them out everywhere
        return UserResponse.fromEntity(userRepository.save(user));
    }

    @Transactional
    public UserResponse enableUser(Long id) {
        User user = findInOrganization(id);
        if (user.getStatus() != UserStatus.DISABLED) {
            throw new BadRequestException("Only disabled users can be re-enabled (current status: " + user.getStatus() + ")");
        }
        user.setStatus(UserStatus.ACTIVE);
        return UserResponse.fromEntity(userRepository.save(user));
    }

    /** Org admin invites a teammate by email (role defaults to STAFF). */
    @Transactional
    public void inviteUser(InviteUserRequest request) {
        Role role = request.role() != null ? request.role() : Role.STAFF;
        invitationService.invite(currentOrganization(), request.name(), request.email(), role);
    }

    private User findInOrganization(Long id) {
        return userRepository.findByIdAndOrganizationId(id, TenantContext.requireOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    private Organization currentOrganization() {
        return organizationRepository.findById(TenantContext.requireOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
    }

    private void assertNotSelf(User user) {
        if (CurrentUser.id().filter(user.getId()::equals).isPresent()) {
            throw new BadRequestException("You cannot disable your own account");
        }
    }
}
