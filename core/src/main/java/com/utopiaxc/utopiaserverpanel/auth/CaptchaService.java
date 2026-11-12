package com.utopiaxc.utopiaserverpanel.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Built-in offline ALTCHA-compatible SHA-256 Proof-of-Work CAPTCHA + IP/account rate tracker.
 */
public final class CaptchaService {
    private static final Gson GSON = new Gson();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long CHALLENGE_TTL_SECONDS = 300;
    private static final long TRACKER_WINDOW_SECONDS = 900;

    private static final byte[] HMAC_KEY = new byte[32];
    private static final ConcurrentHashMap<String, Long> USED_CHALLENGES = new ConcurrentHashMap<String, Long>();
    private static final ConcurrentHashMap<String, AttemptRecord> IP_ATTEMPTS = new ConcurrentHashMap<String, AttemptRecord>();
    private static final ConcurrentHashMap<String, AttemptRecord> ACCOUNT_ATTEMPTS = new ConcurrentHashMap<String, AttemptRecord>();

    static {
        RANDOM.nextBytes(HMAC_KEY);
    }

    private CaptchaService() {}

    public static Map<String, Object> createChallenge(int maxNumber) {
        cleanup();
        int effectiveMax = Math.max(10_000, Math.min(maxNumber, 1_000_000));
        long expires = (System.currentTimeMillis() / 1000L) + CHALLENGE_TTL_SECONDS;
        String salt = PasswordUtil.randomHex(12) + "?expires=" + expires;
        int secretNumber = RANDOM.nextInt(effectiveMax);
        String challenge = PasswordUtil.sha256Hex(salt + secretNumber);
        String signature = hmacSha256Hex(challenge);

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("algorithm", "SHA-256");
        result.put("challenge", challenge);
        result.put("maxnumber", Integer.valueOf(effectiveMax));
        result.put("salt", salt);
        result.put("signature", signature);
        return result;
    }

    public static boolean verifyPayload(String base64OrJson) {
        if (base64OrJson == null || base64OrJson.trim().isEmpty()) return false;
        cleanup();

        String jsonStr = base64OrJson.trim();
        if (!jsonStr.startsWith("{")) {
            try {
                jsonStr = new String(Base64.getDecoder().decode(jsonStr), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        JsonObject payload;
        try {
            payload = GSON.fromJson(jsonStr, JsonObject.class);
        } catch (Exception e) {
            return false;
        }
        if (payload == null) return false;

        String algorithm = getString(payload, "algorithm");
        String challenge = getString(payload, "challenge");
        String salt = getString(payload, "salt");
        String signature = getString(payload, "signature");
        if (!"SHA-256".equalsIgnoreCase(algorithm) || challenge == null || salt == null || signature == null) {
            return false;
        }
        if (!payload.has("number") || payload.get("number").isJsonNull()) {
            return false;
        }
        long number;
        try {
            number = payload.get("number").getAsLong();
        } catch (Exception e) {
            return false;
        }

        long now = System.currentTimeMillis() / 1000L;
        long expires = extractExpires(salt);
        if (expires <= 0 || now > expires) {
            return false;
        }

        String expectedSig = hmacSha256Hex(challenge);
        if (!PasswordUtil.constantTimeEquals(expectedSig, signature)) {
            return false;
        }

        String computedChallenge = PasswordUtil.sha256Hex(salt + number);
        if (!PasswordUtil.constantTimeEquals(computedChallenge, challenge)) {
            return false;
        }

        Long previous = USED_CHALLENGES.putIfAbsent(challenge, Long.valueOf(expires));
        return previous == null;
    }

    public static void recordFailure(String ip, String account) {
        final long now = System.currentTimeMillis() / 1000L;
        if (ip != null && !ip.trim().isEmpty()) {
            IP_ATTEMPTS.compute(ip, (k, rec) -> incrementRecord(rec, now));
        }
        if (account != null && !account.trim().isEmpty()) {
            ACCOUNT_ATTEMPTS.compute(account.toLowerCase(Locale.ROOT), (k, rec) -> incrementRecord(rec, now));
        }
    }

    public static void clearFailures(String ip, String account) {
        if (ip != null) IP_ATTEMPTS.remove(ip);
        if (account != null) ACCOUNT_ATTEMPTS.remove(account.toLowerCase(Locale.ROOT));
    }

    public static boolean isCaptchaRequiredAuto(String ip, String account) {
        long now = System.currentTimeMillis() / 1000L;
        int ipCount = getRecentCount(ip != null ? IP_ATTEMPTS.get(ip) : null, now);
        int acctCount = account == null ? 0 : getRecentCount(ACCOUNT_ATTEMPTS.get(account.toLowerCase(Locale.ROOT)), now);
        return ipCount >= 2 || acctCount >= 2;
    }

    public static int getIpRecentFailures(String ip) {
        if (ip == null) return 0;
        return getRecentCount(IP_ATTEMPTS.get(ip), System.currentTimeMillis() / 1000L);
    }

    private static AttemptRecord incrementRecord(AttemptRecord existing, long now) {
        if (existing == null || now - existing.firstAttemptAt > TRACKER_WINDOW_SECONDS) {
            return new AttemptRecord(1, now, now);
        }
        return new AttemptRecord(existing.count + 1, existing.firstAttemptAt, now);
    }

    private static int getRecentCount(AttemptRecord rec, long now) {
        if (rec == null || now - rec.lastAttemptAt > TRACKER_WINDOW_SECONDS) return 0;
        return rec.count;
    }

    private static long extractExpires(String salt) {
        int idx = salt.indexOf("?expires=");
        if (idx < 0) return -1;
        try {
            return Long.parseLong(salt.substring(idx + 9));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void cleanup() {
        final long now = System.currentTimeMillis() / 1000L;
        USED_CHALLENGES.entrySet().removeIf(e -> e.getValue() < now);
        IP_ATTEMPTS.entrySet().removeIf(e -> now - e.getValue().lastAttemptAt > TRACKER_WINDOW_SECONDS);
        ACCOUNT_ATTEMPTS.entrySet().removeIf(e -> now - e.getValue().lastAttemptAt > TRACKER_WINDOW_SECONDS);
    }

    private static String hmacSha256Hex(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HMAC_KEY, "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    private static String getString(JsonObject obj, String key) {
        if (obj.has(key) && !obj.get(key).isJsonNull()) {
            return obj.get(key).getAsString();
        }
        return null;
    }

    private static final class AttemptRecord {
        final int count;
        final long firstAttemptAt;
        final long lastAttemptAt;

        AttemptRecord(int count, long firstAttemptAt, long lastAttemptAt) {
            this.count = count;
            this.firstAttemptAt = firstAttemptAt;
            this.lastAttemptAt = lastAttemptAt;
        }
    }
}

