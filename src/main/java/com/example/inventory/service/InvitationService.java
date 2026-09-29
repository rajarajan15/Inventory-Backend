package com.example.inventory.service;

import com.example.inventory.dto.AcceptInvitationRequest;
import com.example.inventory.dto.InvitationResponse;
import com.example.inventory.entity.Invitation;
import com.example.inventory.entity.Organization;
import com.example.inventory.entity.Role;
import com.example.inventory.entity.User;
import com.example.inventory.entity.UserStatus;
import com.example.inventory.exception.BadRequestException;
import com.example.inventory.exception.ConflictException;
import com.example.inventory.exception.ForbiddenException;
import com.example.inventory.exception.ResourceNotFoundException;
import com.example.inventory.repository.InvitationRepository;
import com.example.inventory.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class InvitationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    @Value("${app.invitation.expiry-days:7}")
    private int expiryDays;

    public InvitationService(InvitationRepository invitationRepository,
                             UserRepository userRepository,
                             PasswordEncoder passwordEncoder,
                             NotificationService notificationService) {
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.notificationService = notificationService;
    }

    /**
     * Creates an invitation and emails the link. Only ADMIN or STAFF can be invited; SUPER_ADMIN is never
     * granted through the API.
     */
    @Transactional
    public Invitation invite(Organization organization, String name, String email, Role role) {
        if (role != Role.ADMIN && role != Role.STAFF) {
            throw new BadRequestException("Invitations can only grant ADMIN or STAFF roles");
        }
        String normalizedEmail = AuthService.normalizeEmail(email);
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("A user with email " + normalizedEmail + " already exists");
        }

        Invitation invitation = new Invitation();
        invitation.setToken(generateToken());
        invitation.setEmail(normalizedEmail);
        invitation.setName(name != null && !name.isBlank() ? name.trim() : null);
        invitation.setRole(role);
        invitation.setOrganization(organization);
        invitation.setExpiresAt(LocalDateTime.now().plusDays(expiryDays));
        Invitation saved = invitationRepository.save(invitation);

        notificationService.invitation(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public InvitationResponse getInvitation(String token) {
        return InvitationResponse.fromEntity(findUsable(token));
    }

    /** Creates the ACTIVE account for the invitee; they can log in on their organization's portal immediately. */
    @Transactional
    public InvitationResponse acceptInvitation(String token, AcceptInvitationRequest request) {
        Invitation invitation = findUsable(token);
        Organization organization = invitation.getOrganization();
        if (!organization.isActive()) {
            throw new ForbiddenException("This organization's StockWise access is suspended.");
        }
        if (userRepository.existsByEmail(invitation.getEmail())) {
            throw new ConflictException("An account for " + invitation.getEmail() + " already exists. Please sign in.");
        }

        User user = new User(
                request.name().trim(),
                invitation.getEmail(),
                passwordEncoder.encode(request.password()),
                invitation.getRole(),
                UserStatus.ACTIVE,
                organization
        );
        userRepository.save(user);

        invitation.setAcceptedAt(LocalDateTime.now());
        invitationRepository.save(invitation);
        return InvitationResponse.fromEntity(invitation);
    }

    private Invitation findUsable(String token) {
        Invitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found"));
        if (!invitation.isUsable()) {
            throw new BadRequestException("This invitation has expired or was already used. Please ask for a new one.");
        }
        return invitation;
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
