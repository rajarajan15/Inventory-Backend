package com.example.inventory.service;

import com.example.inventory.dto.AuthResponse;
import com.example.inventory.dto.LoginRequest;
import com.example.inventory.dto.RegisterRequest;
import com.example.inventory.dto.RegistrationResponse;
import com.example.inventory.entity.Organization;
import com.example.inventory.entity.RefreshToken;
import com.example.inventory.entity.Role;
import com.example.inventory.entity.User;
import com.example.inventory.entity.UserStatus;
import com.example.inventory.exception.BadRequestException;
import com.example.inventory.exception.ConflictException;
import com.example.inventory.exception.ForbiddenException;
import com.example.inventory.repository.InvitationRepository;
import com.example.inventory.repository.OrganizationRepository;
import com.example.inventory.repository.UserRepository;
import com.example.inventory.security.JwtService;
import com.example.inventory.security.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private AuthService authService;

    private Organization acme;
    private Organization globex;

    @BeforeEach
    void setUp() {
        acme = new Organization("Acme", "acme", null, null);
        acme.setId(1L);
        globex = new Organization("Globex", "globex", null, null);
        globex.setId(2L);
        TenantContext.set(acme.getId(), acme.getSlug());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private User user(UserStatus status, Organization org, Role role) {
        User user = new User("Alice", "alice@example.com", "encodedPassword", role, status, org);
        user.setId(10L);
        return user;
    }

    @Test
    void testRegister_CreatesPendingStaffAndNotifiesAdmins() {
        User admin = new User("Admin", "admin@acme.com", "x", Role.ADMIN, UserStatus.ACTIVE, acme);
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(acme));
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123")).thenReturn("encodedPassword");
        when(userRepository.findByOrganizationIdAndRoleAndStatus(1L, Role.ADMIN, UserStatus.ACTIVE)).thenReturn(List.of(admin));

        RegistrationResponse response = authService.register(new RegisterRequest("Alice", " Alice@Example.com ", "Password123"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(UserStatus.PENDING, saved.getValue().getStatus());
        assertEquals(Role.STAFF, saved.getValue().getRole());
        assertSame(acme, saved.getValue().getOrganization());
        assertEquals("alice@example.com", saved.getValue().getEmail());
        assertEquals(UserStatus.PENDING, response.status());
        verify(notificationService).userRegistrationPending(any(User.class), eq(acme), eq(List.of("admin@acme.com")));
    }

    @Test
    void testRegister_DuplicateEmail() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(acme));
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> authService.register(new RegisterRequest("Alice", "alice@example.com", "Password123")));
        verify(userRepository, never()).save(any());
        verify(notificationService, never()).userRegistrationPending(any(), any(), anyList());
    }

    @Test
    void testLogin_ActiveMember_Success() {
        User alice = user(UserStatus.ACTIVE, acme, Role.STAFF);
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(acme));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(passwordEncoder.matches("Password123", "encodedPassword")).thenReturn(true);
        when(jwtService.generateToken(any(), any(User.class))).thenReturn("mock-jwt-token");
        when(refreshTokenService.createRefreshToken(10L)).thenReturn(
                new RefreshToken(alice, "mock-refresh-token", "hash", Instant.now().plusSeconds(3600)));

        AuthResponse response = authService.login(new LoginRequest("alice@example.com", "Password123"));

        assertEquals("mock-jwt-token", response.getAccessToken());
        assertEquals("mock-refresh-token", response.getRefreshToken());
        assertEquals("acme", response.getOrganizationSlug());
        assertEquals(Role.STAFF, response.getRole());
    }

    @Test
    void testLogin_PendingUser_ForbiddenAfterPasswordCheck() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(acme));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user(UserStatus.PENDING, acme, Role.STAFF)));
        when(passwordEncoder.matches("Password123", "encodedPassword")).thenReturn(true);

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> authService.login(new LoginRequest("alice@example.com", "Password123")));
        assertTrue(ex.getMessage().contains("awaiting approval"));
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    void testLogin_PendingUserWrongPassword_DoesNotRevealStatus() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(acme));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user(UserStatus.PENDING, acme, Role.STAFF)));
        when(passwordEncoder.matches("wrong", "encodedPassword")).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("alice@example.com", "wrong")));
    }

    @Test
    void testLogin_MemberOfAnotherOrganization_Rejected() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(acme));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user(UserStatus.ACTIVE, globex, Role.ADMIN)));
        when(passwordEncoder.matches("Password123", "encodedPassword")).thenReturn(true);

        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("alice@example.com", "Password123")));
        verify(jwtService, never()).generateToken(any(), any());
    }

    @Test
    void testLoginSuperAdmin_OrgUserRejected() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user(UserStatus.ACTIVE, acme, Role.ADMIN)));
        when(passwordEncoder.matches("Password123", "encodedPassword")).thenReturn(true);

        assertThrows(BadCredentialsException.class,
                () -> authService.loginSuperAdmin(new LoginRequest("alice@example.com", "Password123")));
    }

    @Test
    void testLogin_UnknownEmail() {
        when(organizationRepository.findById(1L)).thenReturn(Optional.of(acme));
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("nobody@example.com", "Password123")));
    }
}
