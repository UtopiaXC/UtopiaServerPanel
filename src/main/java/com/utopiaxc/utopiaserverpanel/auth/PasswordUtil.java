package com.utopiaxc.utopiaserverpanel.auth;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Utility class for password hashing and verification using BCrypt.
 */
public final class PasswordUtil {
    // BCrypt work factor (cost). 12 is a good balance of security vs speed for a local server panel.
    private static final int BCRYPT_ROUNDS = 12;

    private PasswordUtil() {}

    /**
     * Hash a plaintext password with BCrypt.
     *
     * @param password the plaintext password
     * @return the BCrypt hash string (includes salt)
     */
    public static String hash(String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt(BCRYPT_ROUNDS));
    }

    /**
     * Verify a plaintext password against a BCrypt hash.
     *
     * @param password     the plaintext password to check
     * @param storedHash   the BCrypt hash from the database
     * @return true if the password matches
     */
    public static boolean verify(String password, String storedHash) {
        if (password == null || storedHash == null) return false;
        try {
            return BCrypt.checkpw(password, storedHash);
        } catch (IllegalArgumentException e) {
            // Invalid hash format
            return false;
        }
    }
}
