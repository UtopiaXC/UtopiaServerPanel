package com.utopiaxc.utopiaserverpanel;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.adapter.AdapterRegistry;
import com.utopiaxc.utopiaserverpanel.adapter.GameAdapter;
import com.utopiaxc.utopiaserverpanel.adapter.model.OnlinePlayerSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.PlayerInfoSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.Position;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerPropertiesSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerStatusSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.util.McPlayerProfileReader;
import com.utopiaxc.utopiaserverpanel.auth.PasswordUtil;
import com.utopiaxc.utopiaserverpanel.auth.UsernamePolicy;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.Schema;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.PlayerBindingMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.UserMapper;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserAccount;
import com.utopiaxc.utopiaserverpanel.web.service.AuthService;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionService;
import com.utopiaxc.utopiaserverpanel.web.service.ProfileService;
import com.utopiaxc.utopiaserverpanel.web.service.SettingsService;
import com.utopiaxc.utopiaserverpanel.web.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class Phase4PlayerProfileTest {

    @AfterEach
    void cleanup() {
        try {
            MyBatisFactory.shutdown();
        } catch (Throwable ignored) {}
        try {
            AdapterRegistry.shutdown();
        } catch (Throwable ignored) {}
    }

    @Test
    void testUsernameMinimumLengthAndReservedRoutes(@TempDir Path tempDir) throws Exception {
        initEnv(tempDir, null);

        // Policy checks
        assertFalse(UsernamePolicy.validate("abc").isValid());
        assertEquals("INVALID_USERNAME", UsernamePolicy.validate("abc").getErrorCode());
        assertFalse(UsernamePolicy.validate("login").isValid());
        assertEquals("RESERVED_USERNAME", UsernamePolicy.validate("login").getErrorCode());
        assertFalse(UsernamePolicy.validate("settings").isValid());
        assertTrue(UsernamePolicy.validate("admin").isValid());
        assertTrue(UsernamePolicy.validate("UtopiaXC").isValid());

        // Registration rejects < 4 chars and reserved routes
        SettingsService.set("allow_registration", "true");
        SettingsService.set("captcha_policy", "off");
        AuthService.ActionResult shortReg = AuthService.register(
                new AuthService.RegisterRequest("bob", "Bob", null, "secret123", null, null),
                "http://localhost", "en-US");
        assertFalse(shortReg.success());
        assertEquals("INVALID_USERNAME", shortReg.errorCode());

        AuthService.ActionResult reservedReg = AuthService.register(
                new AuthService.RegisterRequest("messages", "Msg", null, "secret123", null, null),
                "http://localhost", "en-US");
        assertFalse(reservedReg.success());
        assertEquals("RESERVED_USERNAME", reservedReg.errorCode());

        // Admin creation rejects < 4 chars and reserved routes, accepts >= 4 chars
        UserService.CreateUserResult shortAdminCreate = UserService.createUser(
                "joe", "Joe", null, "secret123", PermissionService.USER_ROLE_ID, "active", false, false);
        assertFalse(shortAdminCreate.success());
        assertEquals("INVALID_USERNAME", shortAdminCreate.errorCode());

        UserService.CreateUserResult okCreate = UserService.createUser(
                "PlayerOne", "Player One", "player1@example.com", "secret123",
                PermissionService.USER_ROLE_ID, "active", false, false);
        assertTrue(okCreate.success());

        int userId = ((Number) okCreate.user().get("id")).intValue();
        ProfileService.SimpleResult renameShort = ProfileService.changeUsername(userId, "p1", "secret123");
        assertFalse(renameShort.success());
        assertEquals("INVALID_USERNAME", renameShort.errorCode());

        ProfileService.SimpleResult renameOk = ProfileService.changeUsername(userId, "PlayerTwo", "secret123");
        assertTrue(renameOk.success());
        assertEquals("PlayerTwo", renameOk.data().get("username"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testExtendedProfilePrivacyDefaultsAndMcStats(@TempDir Path tempDir) throws Exception {
        Path worldDir = tempDir.resolve("server").resolve("world");
        String onlineUuid = "11111111-1111-4111-8111-111111111111";
        String legacyUuid = "22222222-2222-4222-8222-222222222222";

        initEnv(tempDir, onlineUuid);

        // Create user and bind two characters (modern + legacy world JSON fixtures)
        UserService.CreateUserResult created = UserService.createUser(
                "AlexBuilder", "Alex", "alex@example.com", "secret123",
                PermissionService.USER_ROLE_ID, "active", false, false);
        assertTrue(created.success());
        int userId = ((Number) created.user().get("id")).intValue();

        long now = System.currentTimeMillis() / 1000L;
        MyBatisFactory.doWork(session -> {
            PlayerBindingMapper bm = session.getMapper(PlayerBindingMapper.class);
            bm.upsertMcPlayer(onlineUuid, "AlexMC", now);
            bm.insertBinding(userId, onlineUuid, 1, now);
            bm.upsertMcPlayer(legacyUuid, "AlexOld", now);
            bm.insertBinding(userId, legacyUuid, 0, now);
        });

        // Write modern 1.21.1 stats + advancements
        Files.createDirectories(worldDir.resolve("stats"));
        Files.createDirectories(worldDir.resolve("advancements"));
        String modernStatsJson = "{"
                + "\"stats\":{"
                + "\"minecraft:custom\":{\"minecraft:play_time\":72000,\"minecraft:deaths\":4,\"minecraft:walk_one_cm\":250000,\"minecraft:jump\":310,\"minecraft:mob_kills\":19},"
                + "\"minecraft:mined\":{\"minecraft:stone\":120,\"minecraft:diamond_ore\":12},"
                + "\"minecraft:crafted\":{\"minecraft:torch\":64},"
                + "\"minecraft:killed\":{\"minecraft:zombie\":15}"
                + "},\"DataVersion\":3955}";
        Files.write(worldDir.resolve("stats").resolve(onlineUuid + ".json"), modernStatsJson.getBytes(StandardCharsets.UTF_8));

        String modernAdvJson = "{"
                + "\"minecraft:recipes/misc/charcoal\":{\"criteria\":{\"has_log\":\"2026-11-01 10:00:00 +0800\"},\"done\":true},"
                + "\"minecraft:story/mine_diamond\":{\"criteria\":{\"diamond\":\"2026-11-05 14:22:10 +0800\"},\"done\":true},"
                + "\"minecraft:nether/root\":{\"criteria\":{},\"done\":false},"
                + "\"DataVersion\":3955}";
        Files.write(worldDir.resolve("advancements").resolve(onlineUuid + ".json"), modernAdvJson.getBytes(StandardCharsets.UTF_8));

        // Write legacy 1.7.10/1.12.2 stats + achievements
        String legacyStatsJson = "{"
                + "\"stat.playOneMinute\":18000,"
                + "\"stat.deaths\":2,"
                + "\"stat.walkOneCm\":50000,"
                + "\"stat.jump\":44,"
                + "\"stat.mineBlock.minecraft.dirt\":80,"
                + "\"stat.killEntity.Creeper\":5,"
                + "\"achievement.openInventory\":1,"
                + "\"achievement.mineWood\":1"
                + "}";
        Files.write(worldDir.resolve("stats").resolve(legacyUuid + ".json"), legacyStatsJson.getBytes(StandardCharsets.UTF_8));

        // Update extended profile with SVG rejected & valid PNG accepted
        ProfileService.SimpleResult svgReject = ProfileService.updateProfile(
                userId, "Alex", "alex@example.com", "en-US",
                "female", "2000-05-12", "Redstone engineer", null,
                "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString("<svg/>".getBytes(StandardCharsets.UTF_8)),
                true, true, true, true, false);
        assertFalse(svgReject.success());
        assertEquals("INVALID_AVATAR", svgReject.errorCode());

        JsonArray socials = new JsonArray();
        JsonObject s1 = new JsonObject();
        s1.addProperty("platform", "discord");
        s1.addProperty("account", "alex#1234");
        socials.add(s1);
        String pngB64 = Base64.getEncoder().encodeToString(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        ProfileService.SimpleResult profOk = ProfileService.updateProfile(
                userId, "Alex Display", "alex@example.com", "en-US",
                "female", "2000-05-12", "Redstone engineer", socials,
                "data:image/png;base64," + pngB64,
                true, true, true, true, true);
        assertTrue(profOk.success());

        // 1. Default privacy: ONLY username + avatar are public; ALL OTHER fields & MC data MUST be stripped!
        Map<String, Object> publicDefault = ProfileService.getPublicProfile("alexbuilder", null);
        assertNotNull(publicDefault);
        assertEquals("AlexBuilder", publicDefault.get("username"));
        assertTrue(String.valueOf(publicDefault.get("avatarData")).startsWith("data:image/png;base64,"));
        assertFalse(publicDefault.containsKey("nickname"));
        assertFalse(publicDefault.containsKey("bio"));
        assertFalse(publicDefault.containsKey("email"));
        assertFalse(publicDefault.containsKey("gender"));
        assertFalse(publicDefault.containsKey("birthday"));
        assertFalse(publicDefault.containsKey("socialAccounts"));
        assertFalse(publicDefault.containsKey("roleName"));
        assertFalse(publicDefault.containsKey("createdAt"));
        assertFalse(publicDefault.containsKey("mcCharacters"));

        // 2. Disabling public profile blocks anonymous visitors, allows owner preview
        JsonObject disableReq = new JsonObject();
        disableReq.addProperty("publicProfileEnabled", false);
        assertTrue(ProfileService.updatePrivacyConfig(userId, disableReq).success());

        Map<String, Object> blockedView = ProfileService.getPublicProfile("AlexBuilder", null);
        assertEquals(Boolean.FALSE, blockedView.get("publicProfileEnabled"));
        assertFalse(blockedView.containsKey("username"));

        Map<String, Object> ownerPreview = ProfileService.getPublicProfile("AlexBuilder", Integer.valueOf(userId));
        assertEquals(Boolean.TRUE, ownerPreview.get("isOwner"));
        assertEquals("AlexBuilder", ownerPreview.get("username"));

        // 3. Enable full visibility & verify MC stats + achievements (modern & legacy) + coordinates NEVER exposed
        JsonObject fullVis = new JsonObject();
        fullVis.addProperty("publicProfileEnabled", true);
        fullVis.addProperty("showNickname", true);
        fullVis.addProperty("showBio", true);
        fullVis.addProperty("showEmail", true);
        fullVis.addProperty("showGender", true);
        fullVis.addProperty("showBirthday", true);
        fullVis.addProperty("showSocialAccounts", true);
        fullVis.addProperty("showRole", true);
        fullVis.addProperty("showRegisteredAt", true);
        fullVis.addProperty("showMcCharacters", true);
        fullVis.addProperty("showMcOnlineStatus", true);
        fullVis.addProperty("showMcPlaytime", true);
        fullVis.addProperty("showMcAchievements", true);
        fullVis.addProperty("showMcStats", true);
        assertTrue(ProfileService.updatePrivacyConfig(userId, fullVis).success());

        Map<String, Object> exposed = ProfileService.getPublicProfile("AlexBuilder", null);
        assertEquals("Alex Display", exposed.get("nickname"));
        assertEquals("Redstone engineer", exposed.get("bio"));
        assertEquals("alex@example.com", exposed.get("email"));
        assertEquals("female", exposed.get("gender"));
        assertEquals("2000-05-12", exposed.get("birthday"));

        List<Map<String, Object>> chars = (List<Map<String, Object>>) exposed.get("mcCharacters");
        assertNotNull(chars);
        assertEquals(2, chars.size());

        // Primary character (1.21.1 modern)
        Map<String, Object> modernChar = chars.get(0);
        assertEquals("AlexMC", modernChar.get("playerName"));
        assertEquals(Long.valueOf(3600L), modernChar.get("playTimeSeconds"));
        Map<String, Object> onlineSt = (Map<String, Object>) modernChar.get("onlineStatus");
        assertEquals(Boolean.TRUE, onlineSt.get("online"));
        assertFalse(onlineSt.containsKey("position")); // coordinates must never be leaked

        Map<String, Object> modernStats = (Map<String, Object>) modernChar.get("stats");
        assertEquals("modern", modernStats.get("format"));
        Map<String, Object> modernSummary = (Map<String, Object>) modernStats.get("summary");
        assertEquals(Long.valueOf(4L), modernSummary.get("deaths"));
        assertEquals(Long.valueOf(250000L), modernSummary.get("walkDistanceCm"));
        assertEquals(Long.valueOf(132L), modernSummary.get("blocksMinedTotal"));

        Map<String, Object> modernAdv = (Map<String, Object>) modernChar.get("achievements");
        assertEquals(Integer.valueOf(1), modernAdv.get("completedCount"));
        assertEquals(Integer.valueOf(1), modernAdv.get("inProgressCount"));
        assertEquals(Integer.valueOf(1), modernAdv.get("recipesUnlocked"));

        // Secondary character (1.7.10/1.12.2 legacy)
        Map<String, Object> legacyChar = chars.get(1);
        assertEquals("AlexOld", legacyChar.get("playerName"));
        assertEquals(Long.valueOf(900L), legacyChar.get("playTimeSeconds"));
        Map<String, Object> legacyStats = (Map<String, Object>) legacyChar.get("stats");
        assertEquals("legacy", legacyStats.get("format"));
        Map<String, Object> legacySummary = (Map<String, Object>) legacyStats.get("summary");
        assertEquals(Long.valueOf(2L), legacySummary.get("deaths"));
        assertEquals(Long.valueOf(80L), legacySummary.get("blocksMinedTotal"));
        Map<String, Object> legacyAdv = (Map<String, Object>) legacyChar.get("achievements");
        assertEquals(Integer.valueOf(2), legacyAdv.get("completedCount"));

        // Direct reader check with achievements/stats disabled
        Map<String, Object> minimal = McPlayerProfileReader.readCharacterData(
                worldDir, onlineUuid, "AlexMC", AdapterRegistry.getNullable(), false, false, false, false);
        assertFalse(minimal.containsKey("stats"));
        assertFalse(minimal.containsKey("achievements"));
        assertFalse(minimal.containsKey("onlineStatus"));
        assertFalse(minimal.containsKey("playTimeSeconds"));
    }

    @Test
    void testLegacySingleBindingMigrationWithoutIsPrimaryColumn(@TempDir Path tempDir) throws Exception {
        initEnv(tempDir, null);
        MyBatisFactory.shutdown();

        Path dbPath = tempDir.resolve("server").resolve("world").resolve("utopia_server_panel.db");
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             java.sql.Statement st = conn.createStatement()) {
            st.execute("PRAGMA foreign_keys=OFF");
            st.execute("DROP INDEX IF EXISTS idx_player_bindings_user");
            st.execute("DROP TABLE player_bindings");
            st.execute(
                    "CREATE TABLE player_bindings ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "user_id INTEGER UNIQUE NOT NULL,"
                    + "player_uuid TEXT UNIQUE NOT NULL,"
                    + "bound_at INTEGER NOT NULL"
                    + ")");
            st.execute("INSERT INTO mc_players (player_uuid, player_name, updated_at) VALUES ('33333333-3333-4333-8333-333333333333', 'LegacyUser', 100)");
            st.execute("INSERT INTO player_bindings (user_id, player_uuid, bound_at) VALUES (1, '33333333-3333-4333-8333-333333333333', 100)");
            st.execute("PRAGMA foreign_keys=ON");
        }

        // Re-initialize schema on pre-multi-binding DB: must migrate player_bindings BEFORE creating idx_player_bindings_user
        MyBatisFactory.initialize(dbPath);
        Schema.initialize();

        MyBatisFactory.doWork(session -> {
            List<com.utopiaxc.utopiaserverpanel.web.db.model.PlayerBindingInfo> list =
                    session.getMapper(PlayerBindingMapper.class).listByUserId(1);
            assertEquals(1, list.size());
            assertTrue(list.get(0).isPrimary());
            assertEquals("LegacyUser", list.get(0).getPlayerName());
        });
    }

    private static void initEnv(Path tempDir, String onlinePlayerUuid) throws Exception {
        Path serverDir = tempDir.resolve("server");
        Path configDir = serverDir.resolve("config");
        Path worldDir = serverDir.resolve("world");
        Files.createDirectories(configDir);
        Files.createDirectories(worldDir);
        Files.write(serverDir.resolve("server.properties"), "online-mode=false\n".getBytes(StandardCharsets.UTF_8));

        AdapterRegistry.initialize(createMockAdapter(serverDir, configDir, worldDir, onlinePlayerUuid));
        MyBatisFactory.initialize(worldDir.resolve("utopia_server_panel.db"));
        Schema.initialize();
        SettingsService.reload();

        // Seed an admin user so DB mirrors live setup-completed state
        MyBatisFactory.doWork(session -> {
            UserAccount admin = new UserAccount();
            admin.setUsername("admin");
            admin.setNickname("Administrator");
            admin.setEmail("admin@example.com");
            admin.setPasswordHash(PasswordUtil.hash("admin123"));
            admin.setRoleId(PermissionService.ADMIN_ROLE_ID);
            admin.setStatus("active");
            admin.setLocale("en-US");
            long now = System.currentTimeMillis() / 1000L;
            admin.setCreatedAt(now);
            admin.setUpdatedAt(now);
            session.getMapper(UserMapper.class).insert(admin);
        });
    }

    private static GameAdapter createMockAdapter(Path serverDir, Path configDir, Path worldDir, String onlinePlayerUuid) {
        return (GameAdapter) Proxy.newProxyInstance(
                GameAdapter.class.getClassLoader(),
                new Class<?>[]{GameAdapter.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getServerDirectory".equals(name)) return serverDir;
                    if ("getConfigDirectory".equals(name)) return configDir;
                    if ("getWorldDirectory".equals(name)) return worldDir;
                    if ("getWorldName".equals(name)) return "world";
                    if ("getMinecraftVersion".equals(name)) return "1.21.1";
                    if ("getLoaderName".equals(name)) return "neoforge";
                    if ("getOnlinePlayers".equals(name)) {
                        if (onlinePlayerUuid == null) return Collections.emptyList();
                        OnlinePlayerSnapshot p = new OnlinePlayerSnapshot(
                                "AlexMC", onlinePlayerUuid, "minecraft:overworld", "survival",
                                18.5f, 20.0f, new Position(100, 64, -200));
                        return Collections.singletonList(p);
                    }
                    if ("getPlayerInfo".equals(name) && args != null && args.length == 1) {
                        PlayerInfoSnapshot snap = new PlayerInfoSnapshot();
                        boolean match = onlinePlayerUuid != null && onlinePlayerUuid.equals(args[0]);
                        snap.setSupported(true);
                        snap.setOnline(match);
                        snap.setDimension("minecraft:overworld");
                        snap.setGamemode("survival");
                        snap.setHealth(Float.valueOf(18.5f));
                        snap.setMaxHealth(Float.valueOf(20.0f));
                        snap.setPosition(new Position(100, 64, -200));
                        snap.setDeathCount(Integer.valueOf(4));
                        return snap;
                    }
                    if ("getServerProperties".equals(name)) {
                        return new ServerPropertiesSnapshot();
                    }
                    if ("getServerStatus".equals(name)) {
                        return new ServerStatusSnapshot(0, 20, "Test", "1.21.1", "world", 20.0, 10.0);
                    }
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    if (method.getReturnType() == double.class) return 0.0;
                    return null;
                }
        );
    }
}
