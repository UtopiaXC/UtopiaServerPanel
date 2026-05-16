package com.utopiaxc.utopiaserverpanel.auth;

import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Utility class for JWT creation and validation.
 * <p>
 * Uses HMAC-SHA256. The secret key is generated once on first startup
 * and stored in the database for persistence across restarts.
 * </p>
 */
public final class JwtUtil {
    private static final String ALGORITHM = "HmacSHA256";
    private static final long ACCESS_TOKEN_EXPIRY_MS = 15 * 60 * 1000L; // 15 minutes
    private static final long REFRESH_TOKEN_EXPIRY_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days

    private static SecretKey secretKey;

    private JwtUtil() {}

    /**
     * Initialize with an existing key (from DB) or generate a new one.
     */
    public static void initialize(String existingKeyBase64) {
        if (existingKeyBase64 != null && !existingKeyBase64.isEmpty()) {
            byte[] keyBytes = Base64.getDecoder().decode(existingKeyBase64);
            secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
        } else {
            generateNewKey();
        }
    }

    /**
     * Generate a new random 256-bit secret key.
     * Returns the Base64-encoded key for storage in the database.
     */
    public static String generateNewKey() {
        byte[] keyBytes = new byte[32]; // 256 bits
        new SecureRandom().nextBytes(keyBytes);
        secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
        return Base64.getEncoder().encodeToString(keyBytes);
    }

    /**
     * Check if the JWT secret has been initialized.
     */
    public static boolean isInitialized() {
        return secretKey != null;
    }

    /**
     * Generate an access token (short-lived).
     */
    public static String generateAccessToken(int userId, String username, int roleId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ACCESS_TOKEN_EXPIRY_MS);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("roleId", roleId)
                .issuedAt(now)
                .expiration(expiry)
                .id(UUID.randomUUID().toString())
                .signWith(secretKey)
                .compact();
    }

    /**
     * Generate a refresh token (long-lived, stored in DB).
     * Returns the raw token string; the caller should hash it before storing.
     */
    public static String generateRefreshToken(int userId) {
        // Refresh tokens are opaque random strings, not JWTs (to allow revocation)
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Validate an access token and return its claims.
     *
     * @param token the JWT access token
     * @return the parsed claims, or null if invalid/expired
     */
    public static Claims validateToken(String token) {
        if (token == null || token.isEmpty()) return null;
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            UtopiaServerPanel.LOGGER.debug("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract user ID from a valid token's claims.
     */
    public static int getUserId(Claims claims) {
        return Integer.parseInt(claims.getSubject());
    }

    /**
     * Extract username from a valid token's claims.
     */
    public static String getUsername(Claims claims) {
        return claims.get("username", String.class);
    }

    /**
     * Extract role ID from a valid token's claims.
     */
    public static int getRoleId(Claims claims) {
        return claims.get("roleId", Integer.class);
    }

    /**
     * Get the current secret key (for diagnostic purposes only).
     */
    static SecretKey getSecretKey() {
        return secretKey;
    }
}
