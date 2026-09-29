package com.example.inventory.exception;

/** Refresh failed; the message is shown to the user, so it must never contain the token itself. */
public class TokenRefreshException extends RuntimeException {
    public TokenRefreshException(String message) {
        super(message);
    }
}
