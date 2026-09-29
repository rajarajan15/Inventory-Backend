package com.example.inventory.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @Test
    void strongPassword_hasNoViolations() {
        assertTrue(PasswordPolicy.violations("Stock@2026").isEmpty());
    }

    @Test
    void weakPassword_listsEveryMissingRule() {
        assertEquals(
                java.util.List.of("at least 8 characters", "an uppercase letter", "a number", "a special character (e.g. ! @ # $ %)"),
                PasswordPolicy.violations("abc"));
    }

    @Test
    void rejectsSpacesAndOverlongPasswords() {
        assertTrue(PasswordPolicy.violations("Stock @2026").contains("no spaces"));
        assertTrue(PasswordPolicy.violations("Aa1!" + "x".repeat(70)).contains("at most 64 characters"));
    }

    @Test
    void missingPassword() {
        assertEquals(java.util.List.of("a password is required"), PasswordPolicy.violations(""));
    }
}
