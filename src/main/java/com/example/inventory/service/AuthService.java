package com.example.inventory.service;

import com.example.inventory.dto.AuthResponse;
import com.example.inventory.dto.LoginRequest;
import com.example.inventory.dto.RefreshTokenRequest;
import com.example.inventory.dto.RegisterRequest;
import com.example.inventory.dto.RegistrationResponse;
import com.example.inventory.dto.TokenRefreshResponse;
import com.example.inventory.entity.Organization;
import com.example.inventory.entity.RefreshToken;
import com.example.inventory.entity.Role;
import com.example.inventory.entity.User;
import com.example.inventory.entity.UserStatus;
import com.example.inventory.exception.BadRequestException;
import com.example.inventory.exception.ConflictException;
import com.example.inventory.exception.ForbiddenException;
import com.example.inventory.exception.TokenRefreshException;
import com.example.inventory.repository.InvitationRepository;
import com.example.inventory.repository.OrganizationRepository;
import com.example.inventory.repository.UserRepository;
import com.example.inventory.security.JwtService;
import com.example.inventory.security.TenantContext;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final InvitationRepository invitationRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final NotificationService notificationService;

    public AuthService(UserRepository userRepository,
                       OrganizationRepository organizationRepository,
                       InvitationRepository invitationRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       NotificationService notificationService) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.invitationRepository = invitationRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.notificationService = notificationService;
    }

    /**
     * Self-registration on the current organization's portal. Creates a PENDING staff account and
     * emails the organization's admins; the user can log in only after an admin approves.
     */
    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        Organization org = currentOrganization();
        String email = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already registered: " + email);
        }
        if (invitationRepository.existsByEmailAndAcceptedAtIsNullAndExpiresAtAfter(email, LocalDateTime.now())) {
            throw new BadRequestException("An invitation was already sent to " + email + ". Please use the link in that email.");
        }

        User user = new User(
                request.getName().trim(),
                email,
                passwordEncoder.encode(request.getPassword()),
                Role.STAFF,
                UserStatus.PENDING,
                org
        );
        userRepository.save(user);

        List<String> adminEmails = userRepository
                .findByOrganizationIdAndRoleAndStatus(org.getId(), Role.ADMIN, UserStatus.ACTIVE)
                .stream().map(User::getEmail).toList();
        notificationService.userRegistrationPending(user, org, adminEmails);

        return new RegistrationResponse(
                "Registration received. You can sign in once an administrator of " + org.getName() + " approves your account.",
                email,
                UserStatus.PENDING
        );
    }

    /** Login on an organization's portal: only ACTIVE members of that organization succeed. */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        Organization org = currentOrganization();
        User user = authenticate(request);

        // A wrong organization looks exactly like a wrong password, so membership elsewhere is not revealed
        if (user.getOrganization() == null || !org.getId().equals(user.getOrganization().getId())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        assertActive(user);
        return issueTokens(user);
    }

    /** Login for the StockWise super admin portal. */
    @Transactional
    public AuthResponse loginSuperAdmin(LoginRequest request) {
        User user = authenticate(request);
        if (user.getRole() != Role.SUPER_ADMIN) {
            throw new BadCredentialsException("Invalid email or password");
        }
        assertActive(user);
        return issueTokens(user);
    }

    @Transactional
    public TokenRefreshResponse refreshToken(RefreshTokenRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    if (!user.isEnabled()) {
                        throw new TokenRefreshException("Your account is no longer active. Please sign in again.");
                    }
                    if (user.getOrganization() != null && !user.getOrganization().isActive()) {
                        throw new TokenRefreshException("Your organization's StockWise access is suspended. Please contact StockWise support.");
                    }
                    String newAccessToken = jwtService.generateToken(buildClaims(user), user);
                    return new TokenRefreshResponse(newAccessToken, requestRefreshToken);
                })
                .orElseThrow(() -> new TokenRefreshException("Your session is no longer valid. Please sign in again."));
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.trim().isEmpty()) {
            refreshTokenService.revokeByToken(refreshToken.trim());
        }
    }

    private User authenticate(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return user;
    }

    /** Called only after the password is verified, so account status never leaks to strangers. */
    private void assertActive(User user) {
        switch (user.getStatus()) {
            case ACTIVE -> {
            }
            case PENDING -> throw new ForbiddenException(
                    "Your account is awaiting approval by your organization administrator.");
            case REJECTED -> throw new ForbiddenException(
                    "Your registration was not approved. Please contact your organization administrator.");
            case DISABLED -> throw new ForbiddenException(
                    "Your account has been disabled. Please contact your organization administrator.");
        }
    }

    private AuthResponse issueTokens(User user) {
        String jwtToken = jwtService.generateToken(buildClaims(user), user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());
        Organization org = user.getOrganization();

        return new AuthResponse(
                jwtToken,
                refreshToken.getToken(),
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                org != null ? org.getSlug() : null,
                org != null ? org.getName() : null
        );
    }

    private Map<String, Object> buildClaims(User user) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", user.getRole().name());
        extraClaims.put("userId", user.getId());
        extraClaims.put("name", user.getName());
        if (user.getOrganization() != null) {
            extraClaims.put("orgId", user.getOrganization().getId());
            extraClaims.put("orgSlug", user.getOrganization().getSlug());
        }
        return extraClaims;
    }

    private Organization currentOrganization() {
        return organizationRepository.findById(TenantContext.requireOrganizationId())
                .orElseThrow(() -> new BadRequestException("Organization not found"));
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
