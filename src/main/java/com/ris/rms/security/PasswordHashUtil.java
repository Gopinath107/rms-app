package com.ris.rms.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Utility for hashing passwords using BCrypt.
 *
 * Migration strategy:
 *  - Old passwords stored as SHA-256 hex (64-char hex string) or as plaintext
 *    will be re-hashed to BCrypt on their next login or update.
 *  - {@link #hashIfNeeded(String)} detects BCrypt hashes by their "$2" prefix
 *    and skips re-hashing, ensuring idempotency.
 */
@Component
@RequiredArgsConstructor
public class PasswordHashUtil {

    private final PasswordEncoder passwordEncoder;

    /**
     * Hash a password with BCrypt.
     * This is only called for truly new passwords (not already BCrypt).
     */
    public String hashPassword(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    /**
     * Hash the password only if it is NOT already a BCrypt hash.
     * BCrypt hashes always start with "$2a$", "$2b$", or "$2y$".
     *
     * @param password the raw or already-hashed password
     * @return BCrypt hash
     */
    public String hashIfNeeded(String password) {
        if (password == null || password.isBlank()) return password;
        if (isBCryptHash(password)) return password;   // already BCrypt — skip
        return passwordEncoder.encode(password);       // raw or SHA-256 hex — encode
    }

    /**
     * Verify a raw password against a stored hash.
     * Handles three cases:
     *  1. Stored hash is BCrypt  → delegate to PasswordEncoder.matches()
     *  2. Stored hash is SHA-256 hex → compare SHA-256 of raw against stored
     *     (legacy, will be migrated on next save)
     *  3. Stored hash is plaintext (NoOp era) → direct equals
     */
    public boolean verifyPassword(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) return false;

        if (isBCryptHash(storedHash)) {
            // Modern BCrypt path
            return passwordEncoder.matches(rawPassword, storedHash);
        }

        if (isSha256Hex(storedHash)) {
            // Legacy SHA-256 path — compute SHA-256 of raw and compare
            return sha256Hex(rawPassword).equals(storedHash);
        }

        // Legacy NoOp plaintext path
        return storedHash.equals(rawPassword);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private boolean isBCryptHash(String s) {
        return s != null && (s.startsWith("$2a$") || s.startsWith("$2b$") || s.startsWith("$2y$"));
    }

    private boolean isSha256Hex(String s) {
        return s != null && s.length() == 64 && s.matches("^[0-9a-fA-F]+$");
    }

    private String sha256Hex(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) sb.append('0');
                sb.append(hex);
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
