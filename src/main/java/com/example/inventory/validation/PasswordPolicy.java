package com.example.inventory.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * StockWise password rules, shared by API validation and startup checks.
 * The frontend mirrors these rules to show a live checklist.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    /** BCrypt only uses the first 72 bytes, so longer passwords are rejected rather than silently truncated. */
    public static final int MAX_LENGTH = 64;

    private PasswordPolicy() {
    }

    /** @return human-readable descriptions of the unmet rules; empty when the password is acceptable. */
    public static List<String> violations(String password) {
        List<String> problems = new ArrayList<>();
        if (password == null || password.isEmpty()) {
            problems.add("a password is required");
            return problems;
        }
        if (password.length() < MIN_LENGTH) problems.add("at least " + MIN_LENGTH + " characters");
        if (password.length() > MAX_LENGTH) problems.add("at most " + MAX_LENGTH + " characters");
        if (!password.chars().anyMatch(Character::isUpperCase)) problems.add("an uppercase letter");
        if (!password.chars().anyMatch(Character::isLowerCase)) problems.add("a lowercase letter");
        if (!password.chars().anyMatch(Character::isDigit)) problems.add("a number");
        if (password.chars().allMatch(Character::isLetterOrDigit)) problems.add("a special character (e.g. ! @ # $ %)");
        if (password.chars().anyMatch(Character::isWhitespace)) problems.add("no spaces");
        return problems;
    }

    public static String describe(List<String> violations) {
        return "Password must contain " + String.join(", ", violations) + ".";
    }
}
