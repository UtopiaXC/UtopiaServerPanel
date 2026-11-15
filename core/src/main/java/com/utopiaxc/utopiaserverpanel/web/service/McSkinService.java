package com.utopiaxc.utopiaserverpanel.web.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Same-origin cached proxy for resolving Minecraft skin textures (PNG) from Mojang SessionServer.
 * Avoids browser CORS tainting on HTML5 canvas rendering and fails gracefully for offline accounts.
 */
public final class McSkinService {
    private static final long POSITIVE_TTL_MS = 15 * 60 * 1000L;
    private static final long NEGATIVE_TTL_MS = 3 * 60 * 1000L;
    private static final int MAX_CACHE_ENTRIES = 128;
    private static final int MAX_SKIN_BYTES = 262144;
    private static final int HTTP_TIMEOUT_MS = 2500;
    private static final Pattern MC_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{1,16}$");

    private static final ConcurrentHashMap<String, CachedSkin> CACHE = new ConcurrentHashMap<String, CachedSkin>();

    private McSkinService() {}

    public static final class SkinPayload {
        private final byte[] pngBytes;
        private final boolean slim;

        public SkinPayload(byte[] pngBytes, boolean slim) {
            this.pngBytes = pngBytes;
            this.slim = slim;
        }

        public byte[] getPngBytes() { return pngBytes; }
        public boolean isSlim() { return slim; }
    }

    private static final class CachedSkin {
        final SkinPayload payload;
        final long expiresAt;

        CachedSkin(SkinPayload payload, long expiresAt) {
            this.payload = payload;
            this.expiresAt = expiresAt;
        }
    }

    public static SkinPayload resolveSkin(String uuid, String name) {
        String cleanUuid = uuid != null ? uuid.replace("-", "").trim().toLowerCase(Locale.ROOT) : "";
        String cleanName = name != null ? name.trim() : "";
        if (cleanUuid.isEmpty() && cleanName.isEmpty()) {
            return null;
        }
        String cacheKey = cleanUuid + "|" + cleanName.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        CachedSkin cached = CACHE.get(cacheKey);
        if (cached != null && cached.expiresAt > now) {
            return cached.payload;
        }

        SkinPayload resolved = fetchSkinInternal(cleanUuid, cleanName);
        if (CACHE.size() >= MAX_CACHE_ENTRIES) {
            CACHE.clear();
        }
        CACHE.put(cacheKey, new CachedSkin(
                resolved,
                now + (resolved != null ? POSITIVE_TTL_MS : NEGATIVE_TTL_MS)));
        return resolved;
    }

    private static SkinPayload fetchSkinInternal(String cleanUuid, String cleanName) {
        if (cleanUuid.length() == 32 && cleanUuid.matches("^[0-9a-f]{32}$")) {
            SkinPayload byUuid = fetchFromSessionServer(cleanUuid);
            if (byUuid != null) return byUuid;
        }

        if (MC_NAME_PATTERN.matcher(cleanName).matches()) {
            String mojangUuid = fetchMojangUuidByName(cleanName);
            if (mojangUuid != null && !mojangUuid.equals(cleanUuid)) {
                return fetchFromSessionServer(mojangUuid);
            }
        }
        return null;
    }

    private static String fetchMojangUuidByName(String playerName) {
        try {
            byte[] raw = httpGet("https://api.mojang.com/users/profiles/minecraft/" + playerName, 8192);
            if (raw == null) return null;
            JsonObject obj = JsonHelper.parseObject(new String(raw, StandardCharsets.UTF_8));
            String id = JsonHelper.str(obj, "id");
            if (id != null && id.matches("^[0-9a-fA-F]{32}$")) {
                return id.toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {
            // fallback gracefully when offline
        }
        return null;
    }

    private static SkinPayload fetchFromSessionServer(String undashedUuid) {
        try {
            byte[] raw = httpGet("https://sessionserver.mojang.com/session/minecraft/profile/" + undashedUuid, 32768);
            if (raw == null) return null;
            JsonObject profile = JsonHelper.parseObject(new String(raw, StandardCharsets.UTF_8));
            if (profile == null || !profile.has("properties") || !profile.get("properties").isJsonArray()) {
                return null;
            }
            JsonArray props = profile.getAsJsonArray("properties");
            for (JsonElement el : props) {
                if (el == null || !el.isJsonObject()) continue;
                JsonObject prop = el.getAsJsonObject();
                if (!"textures".equals(JsonHelper.str(prop, "name"))) continue;
                String valB64 = JsonHelper.str(prop, "value");
                if (valB64 == null || valB64.isEmpty()) continue;

                byte[] decoded = Base64.getDecoder().decode(valB64);
                JsonObject texRoot = JsonHelper.parseObject(new String(decoded, StandardCharsets.UTF_8));
                if (texRoot == null || !texRoot.has("textures") || !texRoot.get("textures").isJsonObject()) {
                    return null;
                }
                JsonObject textures = texRoot.getAsJsonObject("textures");
                if (!textures.has("SKIN") || !textures.get("SKIN").isJsonObject()) {
                    return null;
                }
                JsonObject skinObj = textures.getAsJsonObject("SKIN");
                String skinUrlStr = JsonHelper.str(skinObj, "url");
                if (skinUrlStr == null || skinUrlStr.isEmpty()) return null;

                // Strictly pin texture URL host to Mojang CDN to avoid SSRF
                URL parsedUrl = new URL(skinUrlStr);
                String host = parsedUrl.getHost() != null ? parsedUrl.getHost().toLowerCase(Locale.ROOT) : "";
                if (!"textures.minecraft.net".equals(host)) {
                    return null;
                }

                boolean slim = false;
                if (skinObj.has("metadata") && skinObj.get("metadata").isJsonObject()) {
                    String model = JsonHelper.str(skinObj.getAsJsonObject("metadata"), "model");
                    slim = "slim".equalsIgnoreCase(model);
                }

                byte[] pngBytes = httpGet("https://textures.minecraft.net" + parsedUrl.getPath(), MAX_SKIN_BYTES);
                if (pngBytes == null || !looksLikePng(pngBytes)) {
                    return null;
                }
                return new SkinPayload(pngBytes, slim);
            }
        } catch (Exception ignored) {
            // fallback gracefully
        }
        return null;
    }

    private static boolean looksLikePng(byte[] bytes) {
        return bytes.length > 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4E
                && bytes[3] == 0x47;
    }

    private static byte[] httpGet(String urlStr, int maxBytes) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(HTTP_TIMEOUT_MS);
            conn.setReadTimeout(HTTP_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("User-Agent", "UtopiaServerPanel");
            int code = conn.getResponseCode();
            if (code != 200) {
                return null;
            }
            try (InputStream is = conn.getInputStream()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int total = 0;
                int n;
                while ((n = is.read(buf)) != -1) {
                    total += n;
                    if (total > maxBytes) {
                        return null;
                    }
                    baos.write(buf, 0, n);
                }
                return baos.toByteArray();
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
