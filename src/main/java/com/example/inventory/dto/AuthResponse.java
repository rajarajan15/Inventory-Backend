package com.example.inventory.dto;

import com.example.inventory.entity.Role;

public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String token; // Alias to accessToken for backwards compatibility
    private String type = "Bearer";
    private Long id;
    private String name;
    private String email;
    private Role role;

    public AuthResponse() {
    }

    public AuthResponse(String accessToken, String refreshToken, Long id, String name, String email, Role role) {
        this.accessToken = accessToken;
        this.token = accessToken;
        this.refreshToken = refreshToken;
        this.type = "Bearer";
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public AuthResponse(String token, Long id, String name, String email, Role role) {
        this.token = token;
        this.accessToken = token;
        this.type = "Bearer";
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
        this.token = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
