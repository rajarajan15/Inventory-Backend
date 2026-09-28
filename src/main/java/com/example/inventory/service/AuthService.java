package com.example.inventory.service;

import com.example.inventory.dto.AuthResponse;
import com.example.inventory.dto.LoginRequest;
import com.example.inventory.dto.RefreshTokenRequest;
import com.example.inventory.dto.RegisterRequest;
import com.example.inventory.dto.TokenRefreshResponse;
import com.example.inventory.entity.RefreshToken;
import com.example.inventory.entity.Role;
import com.example.inventory.entity.User;
import com.example.inventory.exception.BadRequestException;
import com.example.inventory.exception.TokenRefreshException;
import com.example.inventory.repository.UserRepository;
import com.example.inventory.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
            throw new BadRequestException("Email is already registered: " + request.getEmail());
        }

        Role role = request.getRole() != null ? request.getRole() : Role.STAFF;

        User user = new User(
                request.getName().trim(),
                request.getEmail().trim().toLowerCase(),
                passwordEncoder.encode(request.getPassword()),
                role
        );

        User savedUser = userRepository.save(user);

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", savedUser.getRole().name());
        extraClaims.put("userId", savedUser.getId());
        extraClaims.put("name", savedUser.getName());

        String jwtToken = jwtService.generateToken(extraClaims, savedUser);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(savedUser.getId());

        return new AuthResponse(
                jwtToken,
                refreshToken.getToken(),
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole()
        );
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail().trim().toLowerCase(),
                        request.getPassword()
                )
        );

        User user = userRepository.findByEmail(request.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new BadRequestException("User not found with email: " + request.getEmail()));

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", user.getRole().name());
        extraClaims.put("userId", user.getId());
        extraClaims.put("name", user.getName());

        String jwtToken = jwtService.generateToken(extraClaims, user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

        return new AuthResponse(
                jwtToken,
                refreshToken.getToken(),
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }

    @Transactional
    public TokenRefreshResponse refreshToken(RefreshTokenRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    Map<String, Object> extraClaims = new HashMap<>();
                    extraClaims.put("role", user.getRole().name());
                    extraClaims.put("userId", user.getId());
                    extraClaims.put("name", user.getName());

                    String newAccessToken = jwtService.generateToken(extraClaims, user);
                    return new TokenRefreshResponse(newAccessToken, requestRefreshToken);
                })
                .orElseThrow(() -> new TokenRefreshException(requestRefreshToken, "Refresh token does not exist in database"));
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.trim().isEmpty()) {
            refreshTokenService.revokeByToken(refreshToken.trim());
        }
    }
}
