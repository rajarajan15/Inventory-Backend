package com.example.inventory.exception;

/** The request conflicts with the current state (duplicate, or data changed by someone else): HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
