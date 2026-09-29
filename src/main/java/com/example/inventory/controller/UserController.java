package com.example.inventory.controller;

import com.example.inventory.dto.InviteUserRequest;
import com.example.inventory.dto.UserResponse;
import com.example.inventory.entity.UserStatus;
import com.example.inventory.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orgs/{orgSlug}/users")
@Tag(name = "Users", description = "Organization admin manages members: approve sign-ups, invite, disable")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List organization users", description = "Optional status filter, e.g. ?status=PENDING for sign-ups awaiting approval")
    public ResponseEntity<List<UserResponse>> getAllUsers(@RequestParam(required = false) UserStatus status) {
        return ResponseEntity.ok(userService.getAllUsers(status));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get user by ID")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve a pending sign-up", description = "User is emailed and can log in")
    public ResponseEntity<UserResponse> approveUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.approveUser(id));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject a pending sign-up", description = "User is emailed and cannot log in")
    public ResponseEntity<UserResponse> rejectUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.rejectUser(id));
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "Disable a user", description = "Blocks login and invalidates access immediately")
    public ResponseEntity<UserResponse> disableUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.disableUser(id));
    }

    @PostMapping("/{id}/enable")
    @Operation(summary = "Re-enable a disabled user")
    public ResponseEntity<UserResponse> enableUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.enableUser(id));
    }

    @PostMapping("/invite")
    @Operation(summary = "Invite a team member by email", description = "Role defaults to STAFF; ADMIN is also allowed")
    public ResponseEntity<Map<String, String>> inviteUser(@Valid @RequestBody InviteUserRequest request) {
        userService.inviteUser(request);
        return ResponseEntity.ok(Map.of("message", "Invitation sent to " + request.email()));
    }
}
