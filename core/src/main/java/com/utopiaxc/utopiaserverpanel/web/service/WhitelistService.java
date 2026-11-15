package com.utopiaxc.utopiaserverpanel.web.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.utopiaxc.utopiaserverpanel.adapter.AdapterRegistry;
import com.utopiaxc.utopiaserverpanel.adapter.GameAdapter;
import com.utopiaxc.utopiaserverpanel.auth.CaptchaService;
import com.utopiaxc.utopiaserverpanel.logging.PanelLog;
import com.utopiaxc.utopiaserverpanel.mail.EmailTemplateRenderer;
import com.utopiaxc.utopiaserverpanel.mail.SmtpMailer;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.PlayerBindingMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.UserMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.WhitelistMapper;
import com.utopiaxc.utopiaserverpanel.web.db.model.PlayerBindingInfo;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserAccount;
import com.utopiaxc.utopiaserverpanel.web.db.model.WhitelistApplication;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Whitelist application &amp; management service:
 * <ul>
 *     <li>Dual-mode UUID lookup: Mojang/PlayerDB Online UUID + RFC4122 MD5 OfflinePlayer UUID</li>
 *     <li>PoW CAPTCHA + IP/username rate limiting</li>
 *     <li>Auto-bind on approval for the BindView workflow</li>
 *     <li>Direct {@code whitelist.json} / legacy {@code white-list.txt} CRUD + live server reload</li>
 * </ul>
 */
public final class WhitelistService {
    private static final Gson GSON = new Gson();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern MC_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final ConcurrentHashMap<String, long[]> LOOKUP_RATE = new ConcurrentHashMap<String, long[]>();

    private WhitelistService() {}

    public static String computeOfflineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static String formatHyphenatedUuid(String raw) {
        if (raw == null) return "";
        String hex = raw.replace("-", "").trim();
        if (hex.length() != 32) return raw.trim();
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20, 32);
    }

    public static Map<String, Object> lookupPlayerIdentity(String username, String clientIp) {
        if (username == null || !MC_NAME_PATTERN.matcher(username.trim()).matches()) {
            throw new IllegalArgumentException("Invalid Minecraft username (must be 3-16 characters: letters, numbers, _)");
        }
        checkLookupRateLimit(clientIp);
        String cleanName = username.trim();
        String offlineUuid = computeOfflineUuid(cleanName);

        String onlineUuid = null;
        String resolvedName = cleanName;
        boolean onlineFound = false;

        // 1. Primary: Official Mojang API
        JsonObject mojang = httpGetJson("https://api.mojang.com/users/profiles/minecraft/" + cleanName);
        if (mojang != null && mojang.has("id") && !mojang.get("id").isJsonNull()) {
            onlineUuid = formatHyphenatedUuid(mojang.get("id").getAsString());
            if (mojang.has("name") && !mojang.get("name").isJsonNull()) {
                resolvedName = mojang.get("name").getAsString();
            }
            onlineFound = true;
        } else {
            // 2. Fallback: PlayerDB API
            JsonObject pdb = httpGetJson("https://playerdb.co/api/player/minecraft/" + cleanName);
            if (pdb != null && pdb.has("success") && pdb.get("success").getAsBoolean()
                    && pdb.has("data") && pdb.get("data").isJsonObject()) {
                JsonObject data = pdb.getAsJsonObject("data");
                if (data.has("player") && data.get("player").isJsonObject()) {
                    JsonObject player = data.getAsJsonObject("player");
                    if (player.has("id") && !player.get("id").isJsonNull()) {
                        onlineUuid = formatHyphenatedUuid(player.get("id").getAsString());
                        if (player.has("username") && !player.get("username").isJsonNull()) {
                            resolvedName = player.get("username").getAsString();
                        }
                        onlineFound = true;
                    }
                }
            }
        }

        boolean onlineMode = SettingsService.isServerOnlineMode();
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("username", resolvedName);
        result.put("onlineFound", Boolean.valueOf(onlineFound));
        result.put("onlineUuid", onlineUuid != null ? onlineUuid : "");
        result.put("offlineUuid", offlineUuid);
        result.put("recommendedUuid", (onlineMode && onlineUuid != null) ? onlineUuid : offlineUuid);
        result.put("serverOnlineMode", Boolean.valueOf(onlineMode));
        result.put("avatarUrl", "https://mc-heads.net/avatar/" + (onlineFound ? resolvedName : "MHF_Steve") + "/64");
        return result;
    }

    public static Map<String, Object> getPublicStatus(Integer userId) {
        boolean visible = SettingsService.isWhitelistFeatureVisible();
        boolean serverWl = SettingsService.isServerWhitelistEnabled();
        boolean panelEnabled = SettingsService.isWhitelistManagementEnabled();
        boolean canApplyGuest = PermissionService.guestMeetsRequirement("whitelist_apply:2");
        boolean canApplyUser = userId != null
                ? PermissionService.meetsRequirement(userId.intValue(), "whitelist_apply:2")
                : canApplyGuest;

        Map<String, Object> res = new LinkedHashMap<String, Object>();
        res.put("featureVisible", Boolean.valueOf(visible));
        res.put("visible", Boolean.valueOf(visible));
        res.put("serverWhitelistEnabled", Boolean.valueOf(serverWl));
        res.put("whitelistManagementEnabled", Boolean.valueOf(panelEnabled));
        res.put("guestCanApply", Boolean.valueOf(visible && canApplyGuest));
        res.put("canGuestApply", Boolean.valueOf(visible && canApplyGuest));
        res.put("canApply", Boolean.valueOf(visible && canApplyUser));
        res.put("whitelistAllowGuest", Boolean.valueOf(canApplyGuest));
        res.put("socialRequired", Boolean.valueOf(SettingsService.isWhitelistSocialRequired()));
        res.put("requiredSocialPlatforms", SettingsService.getWhitelistRequiredSocialPlatforms());
        res.put("serverOnlineMode", Boolean.valueOf(SettingsService.isServerOnlineMode()));
        if (userId != null) {
            res.put("myApplications", MyBatisFactory.doWorkWithResult(session ->
                    session.getMapper(WhitelistMapper.class).listByUserId(userId.intValue())));
            res.put("boundPlayer", BindingService.findByUserId(userId.intValue()));
            res.put("boundPlayers", BindingService.listByUserId(userId.intValue()));
        }
        return res;
    }

    public static WhitelistApplication submitApplication(
            Integer userId,
            String username,
            String onlineUuid,
            String offlineUuid,
            String preferredUuidMode,
            String email,
            String reason,
            String socialAccount,
            boolean bindOnApprove,
            String captchaPayload,
            String clientIp
    ) {
        if (!SettingsService.isWhitelistFeatureVisible()) {
            throw new IllegalStateException("Whitelist application portal is currently disabled");
        }
        if (userId == null) {
            if (!PermissionService.guestMeetsRequirement("whitelist_apply:2")) {
                throw new SecurityException("Guest whitelist applications are disabled. Please sign in to apply.");
            }
        } else if (!PermissionService.meetsRequirement(userId.intValue(), "whitelist_apply:2")) {
            throw new SecurityException("Whitelist application permission denied");
        }

        if (userId == null && (captchaPayload == null || !CaptchaService.verifyPayload(captchaPayload))) {
            CaptchaService.recordFailure(clientIp, username);
            throw new IllegalArgumentException("Invalid or expired CAPTCHA verification");
        }

        String cleanName = username != null ? username.trim() : "";
        if (!MC_NAME_PATTERN.matcher(cleanName).matches()) {
            throw new IllegalArgumentException("Invalid Minecraft username (3-16 alphanumeric or underscore characters)");
        }
        String cleanEmail = email != null ? email.trim() : "";
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@") || cleanEmail.length() > 160) {
            throw new IllegalArgumentException("A valid email address is required");
        }
        String cleanReason = reason != null ? reason.trim() : "";
        if (cleanReason.length() < 4 || cleanReason.length() > 1000) {
            throw new IllegalArgumentException("Application reason must be 4-1000 characters");
        }
        if (bindOnApprove && userId == null) {
            throw new IllegalArgumentException("Must be signed in to bind account on approval");
        }

        final String normalizedSocial = validateAndNormalizeSocialAccounts(socialAccount);

        final long now = System.currentTimeMillis() / 1000L;
        final String ipKey = clientIp != null ? clientIp : "unknown";
        String computedOffline = computeOfflineUuid(cleanName);
        String normalizedOnline = (onlineUuid != null && !onlineUuid.trim().isEmpty())
                ? formatHyphenatedUuid(onlineUuid) : "";
        String mode = "offline".equalsIgnoreCase(preferredUuidMode) ? "offline"
                : ("online".equalsIgnoreCase(preferredUuidMode) ? "online"
                : (SettingsService.isServerOnlineMode() ? "online" : "offline"));

        if (bindOnApprove) {
            List<PlayerBindingInfo> existingBindings = BindingService.listByUserId(userId.intValue());
            if (existingBindings.size() >= BindingService.MAX_BINDINGS_PER_USER) {
                throw new IllegalStateException("Maximum bound player limit reached (" + BindingService.MAX_BINDINGS_PER_USER + ")");
            }
            String targetUuid = ("online".equals(mode) && !normalizedOnline.isEmpty()) ? normalizedOnline : computedOffline;
            if (BindingService.findByPlayerUuid(targetUuid) != null) {
                throw new IllegalStateException("This Minecraft player is already bound to an account");
            }
        }

        WhitelistApplication created = MyBatisFactory.doWorkWithResult(session -> {
            WhitelistMapper mapper = session.getMapper(WhitelistMapper.class);
            if (mapper.countPendingByUsername(cleanName) > 0) {
                throw new IllegalStateException("A pending application for this Minecraft username already exists");
            }
            if (mapper.countPendingByIp(ipKey) >= 5) {
                throw new IllegalStateException("Too many pending whitelist applications from your network");
            }
            Long lastTs = mapper.getLatestSubmitTsByIp(ipKey);
            if (lastTs != null && (now - lastTs.longValue()) < 30L) {
                throw new IllegalStateException("Please wait before submitting another whitelist application");
            }

            WhitelistApplication app = new WhitelistApplication();
            app.setUsername(cleanName);
            app.setOnlineUuid(normalizedOnline);
            app.setOfflineUuid((offlineUuid != null && !offlineUuid.trim().isEmpty())
                    ? formatHyphenatedUuid(offlineUuid) : computedOffline);
            app.setPreferredUuidMode(mode);
            app.setEmail(cleanEmail);
            app.setReason(cleanReason);
            app.setSocialAccount(normalizedSocial);
            app.setStatus("pending");
            app.setApplicantUserId(userId);
            app.setBindOnApprove(bindOnApprove);
            app.setSubmitIp(ipKey);
            app.setCreatedAt(now);
            mapper.insert(app);
            return app;
        });

        MessageService.notifyAdminsWhitelistSubmitted(created);
        return created;
    }

    public static Map<String, Object> listAdminApplications(String status, String search, int page, int pageSize) {
        final int safeSize = Math.max(1, Math.min(100, pageSize));
        final int offset = Math.max(0, (page - 1) * safeSize);
        return MyBatisFactory.doWorkWithResult(session -> {
            WhitelistMapper mapper = session.getMapper(WhitelistMapper.class);
            List<WhitelistApplication> items = mapper.listApplications(status, search, offset, safeSize);
            int total = mapper.countApplications(status, search);
            Map<String, Object> res = new LinkedHashMap<String, Object>();
            res.put("items", items != null ? items : Collections.emptyList());
            res.put("total", Integer.valueOf(total));
            return res;
        });
    }

    public static WhitelistApplication reviewApplication(int id, int reviewerUserId, boolean approve, String reviewNote) throws IOException {
        WhitelistApplication app = MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(WhitelistMapper.class).findById(id));
        if (app == null) {
            throw new IllegalArgumentException("Whitelist application not found");
        }
        if (!"pending".equals(app.getStatus())) {
            throw new IllegalStateException("Application has already been reviewed (" + app.getStatus() + ")");
        }

        long now = System.currentTimeMillis() / 1000L;
        String newStatus = approve ? "approved" : "rejected";
        String safeNote = reviewNote != null ? reviewNote.trim() : "";

        String resolvedUuid = resolveEffectiveUuid(app);
        if (approve) {
            addWhitelistEntryToServer(app.getUsername(), resolvedUuid);
            if (app.isBindOnApprove() && app.getApplicantUserId() != null) {
                autoBindApprovedApplicant(app.getApplicantUserId().intValue(), resolvedUuid, app.getUsername(), now);
            }
        }

        MyBatisFactory.doWork(session -> session.getMapper(WhitelistMapper.class).updateReview(
                id, newStatus, Integer.valueOf(reviewerUserId), safeNote, now));

        app.setStatus(newStatus);
        app.setReviewerUserId(Integer.valueOf(reviewerUserId));
        app.setReviewNote(safeNote);
        app.setReviewedAt(Long.valueOf(now));

        MessageService.notifyApplicantWhitelistReviewed(app, approve, safeNote);
        if (SettingsService.isWhitelistSendEmail()) {
            sendWhitelistEmailAsync(app, approve, resolvedUuid, safeNote);
        }
        return app;
    }

    public static List<Map<String, String>> listServerWhitelistEntries() {
        GameAdapter adapter = AdapterRegistry.getNullable();
        Path serverDir = adapter != null ? adapter.getServerDirectory() : null;
        if (serverDir == null) return Collections.emptyList();

        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        Path jsonFile = serverDir.resolve("whitelist.json");
        if (Files.isRegularFile(jsonFile)) {
            try {
                String raw = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);
                JsonElement el = new JsonParser().parse(raw);
                if (el != null && el.isJsonArray()) {
                    for (JsonElement item : el.getAsJsonArray()) {
                        if (item.isJsonObject()) {
                            JsonObject obj = item.getAsJsonObject();
                            Map<String, String> row = new LinkedHashMap<String, String>();
                            String name = obj.has("name") && !obj.get("name").isJsonNull() ? obj.get("name").getAsString() : "";
                            String uuid = obj.has("uuid") && !obj.get("uuid").isJsonNull() ? obj.get("uuid").getAsString() : "";
                            if (uuid.isEmpty() && !name.isEmpty()) {
                                uuid = resolveKnownOrOfflineUuid(name);
                            }
                            row.put("name", name);
                            row.put("uuid", uuid);
                            list.add(row);
                        } else if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                            String name = item.getAsString().trim();
                            if (!name.isEmpty()) {
                                Map<String, String> row = new LinkedHashMap<String, String>();
                                row.put("name", name);
                                row.put("uuid", resolveKnownOrOfflineUuid(name));
                                list.add(row);
                            }
                        }
                    }
                    return list;
                }
            } catch (Exception ignored) {}
        }

        Path txtFile = serverDir.resolve("white-list.txt");
        if (Files.isRegularFile(txtFile)) {
            try {
                for (String line : Files.readAllLines(txtFile, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        Map<String, String> row = new LinkedHashMap<String, String>();
                        row.put("name", trimmed);
                        row.put("uuid", computeOfflineUuid(trimmed));
                        list.add(row);
                    }
                }
            } catch (IOException ignored) {}
        }
        return list;
    }

    public static void addWhitelistEntryToServer(String username, String uuidOverride) throws IOException {
        if (username == null || !MC_NAME_PATTERN.matcher(username.trim()).matches()) {
            throw new IllegalArgumentException("Invalid Minecraft username");
        }
        String cleanName = username.trim();
        GameAdapter adapter = AdapterRegistry.getNullable();
        Path serverDir = adapter != null ? adapter.getServerDirectory() : null;
        if (serverDir == null) {
            throw new IllegalStateException("Server directory unavailable");
        }

        String chosenUuid = (uuidOverride != null && !uuidOverride.trim().isEmpty())
                ? formatHyphenatedUuid(uuidOverride)
                : computeBestUuidForServer(cleanName);

        Path jsonFile = serverDir.resolve("whitelist.json");
        Path txtFile = serverDir.resolve("white-list.txt");
        if (Files.exists(jsonFile) || !Files.exists(txtFile)) {
            JsonArray arr = new JsonArray();
            if (Files.isRegularFile(jsonFile)) {
                try {
                    String raw = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);
                    JsonElement parsed = new JsonParser().parse(raw);
                    if (parsed != null && parsed.isJsonArray()) arr = parsed.getAsJsonArray();
                } catch (Exception ignored) {}
            }
            boolean exists = false;
            for (int i = 0; i < arr.size(); i++) {
                JsonElement el = arr.get(i);
                if (matchesWhitelistElement(el, cleanName)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                JsonObject obj = new JsonObject();
                obj.addProperty("uuid", chosenUuid);
                obj.addProperty("name", cleanName);
                arr.add(obj);
                Files.write(jsonFile, PRETTY_GSON.toJson(arr).getBytes(StandardCharsets.UTF_8));
            }
        }
        if (Files.isRegularFile(txtFile)) {
            List<String> lines = new ArrayList<String>(Files.readAllLines(txtFile, StandardCharsets.UTF_8));
            boolean found = false;
            for (String l : lines) {
                if (cleanName.equalsIgnoreCase(l.trim())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                lines.add(cleanName);
                Files.write(txtFile, lines, StandardCharsets.UTF_8);
            }
        }
        if (adapter != null) {
            adapter.reloadServerWhitelist();
        }
    }

    public static boolean removeWhitelistEntryFromServer(String username) throws IOException {
        if (username == null || username.trim().isEmpty()) return false;
        String target = username.trim();
        GameAdapter adapter = AdapterRegistry.getNullable();
        Path serverDir = adapter != null ? adapter.getServerDirectory() : null;
        if (serverDir == null) return false;

        boolean removed = false;
        Path jsonFile = serverDir.resolve("whitelist.json");
        if (Files.isRegularFile(jsonFile)) {
            String raw = new String(Files.readAllBytes(jsonFile), StandardCharsets.UTF_8);
            JsonElement parsed = new JsonParser().parse(raw);
            if (parsed != null && parsed.isJsonArray()) {
                JsonArray oldArr = parsed.getAsJsonArray();
                JsonArray nextArr = new JsonArray();
                for (int i = 0; i < oldArr.size(); i++) {
                    JsonElement el = oldArr.get(i);
                    if (matchesWhitelistElement(el, target)) {
                        removed = true;
                        continue;
                    }
                    nextArr.add(el);
                }
                if (removed) {
                    Files.write(jsonFile, PRETTY_GSON.toJson(nextArr).getBytes(StandardCharsets.UTF_8));
                }
            }
        }
        Path txtFile = serverDir.resolve("white-list.txt");
        if (Files.isRegularFile(txtFile)) {
            List<String> lines = Files.readAllLines(txtFile, StandardCharsets.UTF_8);
            List<String> kept = new ArrayList<String>();
            for (String l : lines) {
                if (target.equalsIgnoreCase(l.trim())) {
                    removed = true;
                } else {
                    kept.add(l);
                }
            }
            if (removed) {
                Files.write(txtFile, kept, StandardCharsets.UTF_8);
            }
        }
        if (removed && adapter != null) {
            adapter.reloadServerWhitelist();
        }
        return removed;
    }

    /**
     * Toggle white-list live on the running server and persist to {@code server.properties}
     * without requiring a server restart. Also unlinks/disables web whitelist management when disabled.
     */
    public static boolean setServerWhitelistEnabled(boolean enabled) throws IOException {
        GameAdapter adapter = AdapterRegistry.getNullable();
        Path serverDir = adapter != null ? adapter.getServerDirectory() : null;
        if (serverDir != null) {
            Path propsFile = serverDir.resolve("server.properties");
            String existing = Files.isRegularFile(propsFile)
                    ? new String(Files.readAllBytes(propsFile), StandardCharsets.UTF_8) : "";
            Map<String, String> upd = new LinkedHashMap<String, String>();
            upd.put("key", "white-list");
            upd.put("value", String.valueOf(enabled));
            String nextContent = ModConfigService.applyStructuredUpdates("properties", existing,
                    Collections.singletonList(upd));
            Files.write(propsFile, nextContent.getBytes(StandardCharsets.UTF_8));
        }
        if (adapter != null) {
            adapter.executeCommand(enabled ? "whitelist on" : "whitelist off");
            adapter.reloadServerWhitelist();
        }
        if (!enabled) {
            SettingsService.set("whitelist_management_enabled", "false");
        }
        return enabled;
    }

    private static boolean matchesWhitelistElement(JsonElement el, String username) {
        if (el == null || username == null) return false;
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            return obj.has("name") && !obj.get("name").isJsonNull()
                    && username.equalsIgnoreCase(obj.get("name").getAsString());
        }
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            return username.equalsIgnoreCase(el.getAsString().trim());
        }
        return false;
    }

    private static String resolveKnownOrOfflineUuid(String username) {
        try {
            if (MyBatisFactory.isInitialized()) {
                PlayerBindingInfo info = MyBatisFactory.doWorkWithResult(session ->
                        session.getMapper(PlayerBindingMapper.class).findByPlayerName(username));
                if (info != null && info.getPlayerUuid() != null && !info.getPlayerUuid().isEmpty()) {
                    return info.getPlayerUuid();
                }
            }
        } catch (Exception ignored) {}
        return computeOfflineUuid(username);
    }

    private static String computeBestUuidForServer(String username) {
        if (SettingsService.isServerOnlineMode()) {
            try {
                Map<String, Object> lookup = lookupPlayerIdentity(username, "127.0.0.1");
                String online = (String) lookup.get("onlineUuid");
                if (online != null && !online.isEmpty()) return online;
            } catch (Exception ignored) {}
        }
        return computeOfflineUuid(username);
    }

    private static String resolveEffectiveUuid(WhitelistApplication app) {
        if (SettingsService.isServerOnlineMode() && "online".equalsIgnoreCase(app.getPreferredUuidMode())
                && app.getOnlineUuid() != null && !app.getOnlineUuid().isEmpty()) {
            return app.getOnlineUuid();
        }
        if ("online".equalsIgnoreCase(app.getPreferredUuidMode()) && app.getOnlineUuid() != null && !app.getOnlineUuid().isEmpty()) {
            return app.getOnlineUuid();
        }
        if (app.getOfflineUuid() != null && !app.getOfflineUuid().isEmpty()) {
            return app.getOfflineUuid();
        }
        return computeOfflineUuid(app.getUsername());
    }

    private static void autoBindApprovedApplicant(final int userId, final String uuid, final String playerName, final long now) {
        try {
            MyBatisFactory.doWork(session -> {
                PlayerBindingMapper mapper = session.getMapper(PlayerBindingMapper.class);
                int currentCount = mapper.countByUserId(userId);
                if (currentCount >= BindingService.MAX_BINDINGS_PER_USER) return;
                if (mapper.countByPlayerUuid(uuid) > 0) return;
                mapper.upsertMcPlayer(uuid, playerName, now);
                mapper.insertBinding(userId, uuid, currentCount == 0 ? 1 : 0, now);
            });
        } catch (Exception e) {
            PanelLog.get().warn("Auto-bind on whitelist approval failed: {}", e.getMessage());
        }
    }

    private static String validateAndNormalizeSocialAccounts(String rawSocial) {
        boolean socialRequired = SettingsService.isWhitelistSocialRequired();
        List<String> requiredPlatforms = SettingsService.getWhitelistRequiredSocialPlatforms();
        List<Map<String, String>> normalizedEntries = new ArrayList<>();
        Set<String> presentPlatforms = new LinkedHashSet<>();

        String trimmed = rawSocial != null ? rawSocial.trim() : "";
        if (trimmed.startsWith("[")) {
            try {
                JsonArray arr = JsonHelper.parseArray(trimmed);
                for (JsonElement el : arr) {
                    if (el != null && el.isJsonObject()) {
                        JsonObject obj = el.getAsJsonObject();
                        String platform = JsonHelper.str(obj, "platform");
                        String customPlatform = JsonHelper.str(obj, "customPlatform");
                        String account = JsonHelper.str(obj, "account");
                        if (account == null || account.trim().isEmpty()) continue;
                        String cleanPlatform = (platform != null && !platform.trim().isEmpty())
                                ? platform.trim() : "custom";
                        String cleanAccount = account.trim();
                        if (cleanAccount.length() > 160) cleanAccount = cleanAccount.substring(0, 160);
                        Map<String, String> entry = new LinkedHashMap<>();
                        entry.put("platform", cleanPlatform);
                        if ("custom".equalsIgnoreCase(cleanPlatform) && customPlatform != null && !customPlatform.trim().isEmpty()) {
                            String cleanCustom = customPlatform.trim();
                            entry.put("customPlatform", cleanCustom.length() > 48 ? cleanCustom.substring(0, 48) : cleanCustom);
                        }
                        entry.put("account", cleanAccount);
                        normalizedEntries.add(entry);
                        presentPlatforms.add(cleanPlatform.toLowerCase(Locale.ROOT));
                    }
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid social accounts format");
            }
        } else if (!trimmed.isEmpty()) {
            Map<String, String> legacyEntry = new LinkedHashMap<>();
            legacyEntry.put("platform", "custom");
            legacyEntry.put("account", trimmed.length() > 160 ? trimmed.substring(0, 160) : trimmed);
            normalizedEntries.add(legacyEntry);
            presentPlatforms.add("custom");
        }

        if ((socialRequired || !requiredPlatforms.isEmpty()) && normalizedEntries.isEmpty()) {
            throw new IllegalArgumentException("Social account contact information is required");
        }
        for (String reqPlatform : requiredPlatforms) {
            if (reqPlatform != null && !reqPlatform.trim().isEmpty()
                    && !presentPlatforms.contains(reqPlatform.trim().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Missing required social platform: " + reqPlatform);
            }
        }
        if (normalizedEntries.isEmpty()) {
            return "";
        }
        return JsonHelper.toJson(normalizedEntries);
    }

    private static void sendWhitelistEmailAsync(final WhitelistApplication app, final boolean approved,
                                                final String resolvedUuid, final String note) {
        if (app.getEmail() == null || app.getEmail().trim().isEmpty()) return;
        final SmtpMailer.SmtpConfig smtpCfg = SettingsService.getSmtpConfig();
        if (smtpCfg.getHost() == null || smtpCfg.getHost().trim().isEmpty()) return;

        String locale = "zh-CN";
        if (app.getApplicantUserId() != null) {
            UserAccount ua = MyBatisFactory.doWorkWithResult(session ->
                    session.getMapper(UserMapper.class).findById(app.getApplicantUserId().intValue()));
            if (ua != null && ua.getLocale() != null) locale = ua.getLocale();
        }

        final EmailTemplateRenderer.RenderedEmail rendered = EmailTemplateRenderer.renderWhitelistReviewEmail(
                locale, SettingsService.getSiteName(), approved, app.getUsername(), resolvedUuid, note);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SmtpMailer.sendHtmlEmail(smtpCfg, app.getEmail(), rendered.getSubject(), rendered.getHtml(), rendered.getText());
                } catch (Exception e) {
                    PanelLog.get().warn("Whitelist review email failed for {}: {}", app.getEmail(), e.getMessage());
                }
            }
        }, "usp-mail-whitelist");
        t.setDaemon(true);
        t.start();
    }

    private static void checkLookupRateLimit(String ip) {
        if (ip == null || ip.isEmpty() || "127.0.0.1".equals(ip)) return;
        long nowSec = System.currentTimeMillis() / 1000L;
        LOOKUP_RATE.compute(ip, (k, rec) -> {
            if (rec == null || (nowSec - rec[0]) >= 60L) {
                return new long[]{nowSec, 1L};
            }
            if (rec[1] >= 25L) {
                throw new IllegalStateException("Too many player lookup requests; please try again shortly");
            }
            rec[1]++;
            return rec;
        });
    }

    private static JsonObject httpGetJson(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "UtopiaServerPanel/1.1");
            int code = conn.getResponseCode();
            if (code != 200) return null;
            try (InputStream is = conn.getInputStream()) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[2048];
                int n;
                while ((n = is.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                    if (baos.size() > 65536) break;
                }
                String raw = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                return GSON.fromJson(raw, JsonObject.class);
            }
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
