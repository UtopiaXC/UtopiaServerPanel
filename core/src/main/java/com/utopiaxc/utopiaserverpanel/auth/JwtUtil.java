package com.utopiaxc.utopiaserverpanel.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.utopiaxc.utopiaserverpanel.logging.PanelLog;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * Pure Java 8 HS256 JWT token signer & validator backed by {@link Mac} and Gson.
 * Eliminates JJWT + Jackson dependencies (~2.15 MB) with constant-time signature checks.
 */
public final class JwtUtil {
    private static final String ALGORITHM = "HmacSHA256";
    private static final long ACCESS_TOKEN_EXPIRY_MS = 15 * 60 * 1000L;
    private static final long MFA_TOKEN_EXPIRY_MS = 5 * 60 * 1000L;
    private static final Base64.Encoder B64_URL_ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_URL_DEC = Base64.getUrlDecoder();
    private static final String HEADER_B64;

    static {
        JsonObject header = new JsonObject();
        header.addProperty("alg", "HS256");
        header.addProperty("typ", "JWT");
        HEADER_B64 = B64_URL_ENC.encodeToString(header.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static volatile SecretKey secretKey;

    private JwtUtil() {}

    public static void initialize(String existingKeyBase64) {
        if (existingKeyBase64 != null && !existingKeyBase64.isEmpty()) {
            byte[] keyBytes = Base64.getDecoder().decode(existingKeyBase64);
            secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
        } else {
            generateNewKey();
        }
    }

    public static String generateNewKey() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
        return Base64.getEncoder().encodeToString(keyBytes);
    }

    public static boolean isInitialized() {
        return secretKey != null;
    }

    public static String generateAccessToken(int userId, String username, int roleId, int tokenVersion, String sessionId) {
        long nowSec = System.currentTimeMillis() / 1000L;
        long expSec = (System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY_MS) / 1000L;

        JsonObject payload = new JsonObject();
        payload.addProperty("sub", String.valueOf(userId));
        payload.addProperty("username", username);
        payload.addProperty("roleId", roleId);
        payload.addProperty("tv", tokenVersion);
        payload.addProperty("sid", sessionId);
        payload.addProperty("purpose", "access");
        payload.addProperty("iat", nowSec);
        payload.addProperty("exp", expSec);
        payload.addProperty("jti", UUID.randomUUID().toString());
        return sign(payload);
    }

    public static String generateMfaToken(int userId, int tokenVersion, String purpose, boolean rememberMe) {
        long nowSec = System.currentTimeMillis() / 1000L;
        long expSec = (System.currentTimeMillis() + MFA_TOKEN_EXPIRY_MS) / 1000L;

        JsonObject payload = new JsonObject();
        payload.addProperty("sub", String.valueOf(userId));
        payload.addProperty("tv", tokenVersion);
        payload.addProperty("purpose", purpose);
        payload.addProperty("rem", rememberMe);
        payload.addProperty("iat", nowSec);
        payload.addProperty("exp", expSec);
        payload.addProperty("jti", UUID.randomUUID().toString());
        return sign(payload);
    }

    public static JwtClaims validateToken(String token) {
        SecretKey key = secretKey;
        if (token == null || token.isEmpty() || key == null) return null;
        try {
            int dot1 = token.indexOf('.');
            if (dot1 <= 0) return null;
            int dot2 = token.indexOf('.', dot1 + 1);
            if (dot2 <= dot1 + 1 || token.indexOf('.', dot2 + 1) >= 0) return null;

            String headerB64 = token.substring(0, dot1);
            String payloadB64 = token.substring(dot1 + 1, dot2);
            String sigB64 = token.substring(dot2 + 1);
            if (sigB64.isEmpty()) return null;

            String headerJson = new String(B64_URL_DEC.decode(headerB64), StandardCharsets.UTF_8);
            @SuppressWarnings("deprecation")
            JsonObject header = new JsonParser().parse(headerJson).getAsJsonObject();
            if (!header.has("alg") || !"HS256".equals(header.get("alg").getAsString())) {
                return null;
            }

            String signingInput = token.substring(0, dot2);
            byte[] expectedSig = hmacSha256(key, signingInput);
            byte[] actualSig = B64_URL_DEC.decode(sigB64);
            if (!MessageDigest.isEqual(expectedSig, actualSig)) {
                return null;
            }

            String payloadJson = new String(B64_URL_DEC.decode(payloadB64), StandardCharsets.UTF_8);
            @SuppressWarnings("deprecation")
            JsonObject payload = new JsonParser().parse(payloadJson).getAsJsonObject();

            JwtClaims claims = new JwtClaims(payload);
            long nowSec = System.currentTimeMillis() / 1000L;
            Long exp = claims.getLong("exp");
            if (exp == null || nowSec >= exp) {
                return null;
            }
            Long nbf = claims.getLong("nbf");
            if (nbf != null && nowSec < nbf) {
                return null;
            }
            String sub = claims.getSubject();
            if (sub == null || sub.isEmpty()) {
                return null;
            }
            Integer.parseInt(sub);
            return claims;
        } catch (Exception e) {
            PanelLog.get().debug("JWT validation failed: {}", e.getMessage());
            return null;
        }
    }

    public static int getUserId(JwtClaims claims) {
        return Integer.parseInt(claims.getSubject());
    }

    public static String getUsername(JwtClaims claims) {
        return claims.getString("username");
    }

    public static int getRoleId(JwtClaims claims) {
        Integer r = claims.getInteger("roleId");
        return r != null ? r : 0;
    }

    public static int getTokenVersion(JwtClaims claims) {
        Integer tv = claims.getInteger("tv");
        return tv != null ? tv : 0;
    }

    public static String getSessionId(JwtClaims claims) {
        return claims.getString("sid");
    }

    public static String getPurpose(JwtClaims claims) {
        return claims.getString("purpose");
    }

    public static boolean getRememberMe(JwtClaims claims) {
        Boolean b = claims.getBoolean("rem");
        return b != null && b;
    }

    private static String sign(JsonObject payload) {
        SecretKey key = secretKey;
        if (key == null) {
            throw new IllegalStateException("JWT secret key not initialized");
        }
        String payloadB64 = B64_URL_ENC.encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        String signingInput = HEADER_B64 + "." + payloadB64;
        byte[] sig = hmacSha256(key, signingInput);
        return signingInput + "." + B64_URL_ENC.encodeToString(sig);
    }

    private static byte[] hmacSha256(SecretKey key, String input) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return mac.doFinal(input.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 signing error", e);
        }
    }
}
