package com.utopiaxc.utopiaserverpanel.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Pure Java 8 RFC 6238 TOTP (Time-based One-Time Password) generator & verifier.
 */
public final class TotpUtil {
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;
    private static final int DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;
    private static final int ALLOWED_DRIFT_STEPS = 1;
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpUtil() {}

    public static String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        RANDOM.nextBytes(bytes);
        return encodeBase32(bytes);
    }

    public static String buildOtpAuthUri(String issuer, String accountName, String base32Secret) {
        String safeIssuer = issuer != null && !issuer.trim().isEmpty() ? issuer.trim() : "UtopiaServerPanel";
        String label = urlEncode(safeIssuer) + ":" + urlEncode(accountName);
        return "otpauth://totp/" + label
                + "?secret=" + urlEncode(base32Secret)
                + "&issuer=" + urlEncode(safeIssuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    public static boolean verifyCode(String base32Secret, String code) {
        if (base32Secret == null || base32Secret.trim().isEmpty() || code == null) return false;
        String clean = code.trim();
        if (!clean.matches("^\\d{6}$")) return false;

        byte[] keyBytes;
        try {
            keyBytes = decodeBase32(base32Secret);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (keyBytes.length == 0) return false;

        long currentStep = (System.currentTimeMillis() / 1000L) / PERIOD_SECONDS;
        for (int i = -ALLOWED_DRIFT_STEPS; i <= ALLOWED_DRIFT_STEPS; i++) {
            String expected = generateCode(keyBytes, currentStep + i);
            if (PasswordUtil.constantTimeEquals(expected, clean)) {
                return true;
            }
        }
        return false;
    }

    private static String generateCode(byte[] keyBytes, long step) {
        try {
            byte[] data = ByteBuffer.allocate(8).putLong(step).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1_000_000;
            return String.format(Locale.ROOT, "%06d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP calculation error", e);
        }
    }

    public static String encodeBase32(byte[] data) {
        StringBuilder sb = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int idx = (buffer >> (bitsLeft - 5)) & 0x1F;
                bitsLeft -= 5;
                sb.append(BASE32_ALPHABET.charAt(idx));
            }
        }
        if (bitsLeft > 0) {
            int idx = (buffer << (5 - bitsLeft)) & 0x1F;
            sb.append(BASE32_ALPHABET.charAt(idx));
        }
        return sb.toString();
    }

    public static byte[] decodeBase32(String encoded) {
        String clean = encoded.trim().toUpperCase(Locale.ROOT).replace("=", "").replace(" ", "");
        if (clean.isEmpty()) return new byte[0];
        byte[] out = new byte[(clean.length() * 5) / 8];
        int buffer = 0;
        int bitsLeft = 0;
        int count = 0;
        for (int i = 0; i < clean.length(); i++) {
            int val = BASE32_ALPHABET.indexOf(clean.charAt(i));
            if (val < 0) throw new IllegalArgumentException("Invalid Base32 char");
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out[count++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out;
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s != null ? s : "", "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 encoding unavailable", e);
        }
    }
}

