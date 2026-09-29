package com.example.inventory.security;

import com.example.inventory.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * The only place that knows how the authenticated user is represented in the security context,
 * so switching the authentication mechanism (e.g. to OIDC) only changes this class.
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static Optional<User> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof User user ? Optional.of(user) : Optional.empty();
    }

    public static Optional<Long> id() {
        return get().map(User::getId);
    }
}
