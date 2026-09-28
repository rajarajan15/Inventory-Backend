package com.example.inventory.service;

import com.example.inventory.dto.AuthResponse;
import com.example.inventory.dto.LoginRequest;
import com.example.inventory.dto.RegisterRequest;
import com.example.inventory.entity.RefreshToken;
import com.example.inventory.entity.Role;
import com.example.inventory.entity.User;
import com.example.inventory.exception.BadRequestException;
import com.example.inventory.repository.UserRepository;
import com.example.inventory.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User(1L, "Alice Staff", "alice@example.com", "encodedPassword", Role.STAFF, null);
    }

    @Test
    void testRegister_Success() {
        RegisterRequest request = new RegisterRequest("Alice Staff", "alice@example.com", "plainPassword", Role.STAFF);

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plainPassword")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(jwtService.generateToken(any(), any(User.class))).thenReturn("mock-jwt-token");
        RefreshToken mockRefresh = new RefreshToken(1L, testUser, "mock-refresh-token", java.time.Instant.now().plusSeconds(3600), false, java.time.Instant.now());
        when(refreshTokenService.createRefreshToken(any())).thenReturn(mockRefresh);

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.getToken());
        assertEquals("mock-jwt-token", response.getAccessToken());
        assertEquals("mock-refresh-token", response.getRefreshToken());
        assertEquals("alice@example.com", response.getEmail());
        assertEquals(Role.STAFF, response.getRole());
    }

    @Test
    void testRegister_DuplicateEmail() {
        RegisterRequest request = new RegisterRequest("Alice Staff", "alice@example.com", "plainPassword", Role.STAFF);

        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void testLogin_Success() {
        LoginRequest request = new LoginRequest("alice@example.com", "plainPassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUser));
        when(jwtService.generateToken(any(), any(User.class))).thenReturn("mock-jwt-token");
        RefreshToken mockRefresh = new RefreshToken(1L, testUser, "mock-refresh-token", java.time.Instant.now().plusSeconds(3600), false, java.time.Instant.now());
        when(refreshTokenService.createRefreshToken(any())).thenReturn(mockRefresh);

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock-jwt-token", response.getToken());
        assertEquals("mock-refresh-token", response.getRefreshToken());
        assertEquals(1L, response.getId());
    }

    @Test
    void testLogin_BadCredentials() {
        LoginRequest request = new LoginRequest("alice@example.com", "wrongPassword");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    void testRefreshToken_Success() {
        com.example.inventory.dto.RefreshTokenRequest request = new com.example.inventory.dto.RefreshTokenRequest("valid-refresh-token");
        RefreshToken mockRefresh = new RefreshToken(1L, testUser, "valid-refresh-token", java.time.Instant.now().plusSeconds(3600), false, java.time.Instant.now());

        when(refreshTokenService.findByToken("valid-refresh-token")).thenReturn(Optional.of(mockRefresh));
        when(refreshTokenService.verifyExpiration(mockRefresh)).thenReturn(mockRefresh);
        when(jwtService.generateToken(any(), any(User.class))).thenReturn("new-jwt-access-token");

        com.example.inventory.dto.TokenRefreshResponse response = authService.refreshToken(request);

        assertNotNull(response);
        assertEquals("new-jwt-access-token", response.getAccessToken());
        assertEquals("valid-refresh-token", response.getRefreshToken());
    }

    @Test
    void testRefreshToken_NotFound() {
        com.example.inventory.dto.RefreshTokenRequest request = new com.example.inventory.dto.RefreshTokenRequest("invalid-token");

        when(refreshTokenService.findByToken("invalid-token")).thenReturn(Optional.empty());

        assertThrows(com.example.inventory.exception.TokenRefreshException.class, () -> authService.refreshToken(request));
    }
}
