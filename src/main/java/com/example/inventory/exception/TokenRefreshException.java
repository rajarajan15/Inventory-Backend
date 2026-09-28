package com.example.inventory.exception;

public class TokenRefreshException extends RuntimeException {
    public TokenRefreshException(String token, String message) {
        super(String.format("Failed for token [%s]: %s", token, message));
    }

    public TokenRefreshException(String message) {
        super(message);
    }
}
