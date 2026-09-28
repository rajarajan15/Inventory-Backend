package com.example.inventory.controller;

import com.example.inventory.dto.AuthResponse;
import com.example.inventory.dto.AuthSessionResponse;
import com.example.inventory.dto.LoginRequest;
import com.example.inventory.dto.RefreshTokenRequest;
import com.example.inventory.dto.RegisterRequest;
import com.example.inventory.dto.TokenRefreshResponse;
import com.example.inventory.exception.BadRequestException;
import com.example.inventory.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Endpoints for user registration and JWT authentication")
public class AuthController {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private final AuthService authService;

    @Value("${jwt.expiration}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpirationMs;

    @Value("${app.security.cookies.secure:false}")
    private boolean secureCookies;

    @Value("${app.security.cookies.same-site:Lax}")
    private String sameSite;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Public endpoint to register a new user (defaults to STAFF role if role is omitted)")
    public ResponseEntity<AuthSessionResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Set-Cookie", buildCookie(ACCESS_TOKEN_COOKIE, response.getAccessToken(), accessTokenExpirationMs, "/").toString())
                .header("Set-Cookie", buildCookie(REFRESH_TOKEN_COOKIE, response.getRefreshToken(), refreshTokenExpirationMs, "/api/auth").toString())
                .body(toSessionResponse(response));
    }

    @PostMapping("/login")
    @Operation(summary = "Login user", description = "Public endpoint to authenticate user credentials and establish an HttpOnly cookie session")
    public ResponseEntity<AuthSessionResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok()
                .header("Set-Cookie", buildCookie(ACCESS_TOKEN_COOKIE, response.getAccessToken(), accessTokenExpirationMs, "/").toString())
                .header("Set-Cookie", buildCookie(REFRESH_TOKEN_COOKIE, response.getRefreshToken(), refreshTokenExpirationMs, "/api/auth").toString())
                .body(toSessionResponse(response));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Generates a fresh access-token cookie using a valid refresh-token cookie")
    public ResponseEntity<Map<String, String>> refreshToken(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadRequestException("Refresh token cookie is required");
        }

        TokenRefreshResponse response = authService.refreshToken(new RefreshTokenRequest(refreshToken));
        return ResponseEntity.ok()
                .header("Set-Cookie", buildCookie(ACCESS_TOKEN_COOKIE, response.getAccessToken(), accessTokenExpirationMs, "/").toString())
                .header("Set-Cookie", buildCookie(REFRESH_TOKEN_COOKIE, response.getRefreshToken(), refreshTokenExpirationMs, "/api/auth").toString())
                .body(Map.of("message", "Access token refreshed"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout user", description = "Revokes the refresh-token cookie and clears the browser session cookies")
    public ResponseEntity<java.util.Map<String, String>> logout(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.ok()
                .header("Set-Cookie", buildCookie(ACCESS_TOKEN_COOKIE, "", 0, "/").toString())
                .header("Set-Cookie", buildCookie(REFRESH_TOKEN_COOKIE, "", 0, "/api/auth").toString())
                .body(Map.of("message", "Logged out successfully"));
    }

    @GetMapping("/csrf")
    @Operation(summary = "Initialize CSRF protection", description = "Sets the readable XSRF-TOKEN cookie required for state-changing browser requests")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok(Map.of("headerName", csrfToken.getHeaderName()));
    }

    private AuthSessionResponse toSessionResponse(AuthResponse response) {
        return new AuthSessionResponse(response.getId(), response.getName(), response.getEmail(), response.getRole());
    }

    private ResponseCookie buildCookie(String name, String value, long maxAgeMs, String path) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secureCookies)
                .sameSite(sameSite)
                .path(path)
                .maxAge(Duration.ofMillis(maxAgeMs))
                .build();
    }
}
