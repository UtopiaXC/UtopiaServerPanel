package com.utopiaxc.utopiaserverpanel.auth;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM symmetric encryption helper for sensitive database secrets (e.g., TOTP keys, SMTP credentials).
 */
public final class CryptoUtil {
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static SecretKey aesKey;

    private CryptoUtil() {}

    public static void initialize(String base64Key) {
        byte[] keyBytes;
        if (base64Key != null && !base64Key.isEmpty()) {
            keyBytes = Base64.getDecoder().decode(base64Key);
        } else {
            keyBytes = new byte[32];
            RANDOM.nextBytes(keyBytes);
        }
        aesKey = new SecretKeySpec(keyBytes, "AES");
    }

    public static String generateNewKeyBase64() {
        byte[] keyBytes = new byte[32];
        RANDOM.nextBytes(keyBytes);
        aesKey = new SecretKeySpec(keyBytes, "AES");
        return Base64.getEncoder().encodeToString(keyBytes);
    }

    public static boolean isInitialized() {
        return aesKey != null;
    }

    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return "";
        if (aesKey == null) throw new IllegalStateException("CryptoUtil not initialized");
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + ciphertext.length);
            buf.put(iv);
            buf.put(ciphertext);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public static String decrypt(String encoded) {
        if (encoded == null || encoded.isEmpty()) return "";
        if (aesKey == null) throw new IllegalStateException("CryptoUtil not initialized");
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            if (combined.length <= GCM_IV_LENGTH) return "";
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(ciphertext);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }
}

