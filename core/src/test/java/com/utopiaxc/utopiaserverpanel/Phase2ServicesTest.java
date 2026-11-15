package com.utopiaxc.utopiaserverpanel;

import com.utopiaxc.utopiaserverpanel.adapter.AdapterRegistry;
import com.utopiaxc.utopiaserverpanel.adapter.GameAdapter;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerPropertiesSnapshot;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerStatusSnapshot;
import com.utopiaxc.utopiaserverpanel.spi.mod.ModEntryDto;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.Schema;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.UserMapper;
import com.utopiaxc.utopiaserverpanel.web.db.model.InGameChatEntry;
import com.utopiaxc.utopiaserverpanel.web.db.model.InSiteMessage;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserAccount;
import com.utopiaxc.utopiaserverpanel.web.service.MessageService;
import com.utopiaxc.utopiaserverpanel.web.service.ModConfigService;
import com.utopiaxc.utopiaserverpanel.web.service.SettingsService;
import com.utopiaxc.utopiaserverpanel.web.service.WhitelistService;
import com.utopiaxc.utopiaserverpanel.web.service.WorldRegistryService;
import com.utopiaxc.utopiaserverpanel.web.service.map.MapService;
import com.utopiaxc.utopiaserverpanel.web.service.map.McaRegionReader;
import com.utopiaxc.utopiaserverpanel.web.service.map.PurePngEncoder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class Phase2ServicesTest {

    @AfterEach
    void cleanup() {
        ModConfigService.clearPendingRestart();
        try {
            MyBatisFactory.shutdown();
        } catch (Throwable ignored) {}
        try {
            AdapterRegistry.shutdown();
        } catch (Throwable ignored) {}
    }

    @Test
    void testPurePngEncoderAndDownsample() throws Exception {
        int size = 16;
        int[] pixels = new int[size * size];
        for (int i = 0; i < pixels.length; i++) {
            pixels[i] = (i % 2 == 0) ? 0xFF336699 : 0xFF11AA22;
        }
        byte[] png = PurePngEncoder.encodeRgba(size, size, pixels);
        assertTrue(png.length > 32);
        assertEquals((byte) 0x89, png[0]);
        assertEquals((byte) 0x50, png[1]);
        assertEquals((byte) 0x4E, png[2]);
        assertEquals((byte) 0x47, png[3]);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(png));
        assertNotNull(decoded);
        assertEquals(size, decoded.getWidth());
        assertEquals(size, decoded.getHeight());
        assertEquals(0xFF336699, decoded.getRGB(0, 0));
        assertEquals(0xFF11AA22, decoded.getRGB(1, 0));

        int[] half = PurePngEncoder.downsampleHalf(size, pixels);
        assertEquals(8 * 8, half.length);
        int avgPixel = half[0];
        assertEquals(0xFF, (avgPixel >>> 24) & 0xFF);
        int r = (avgPixel >>> 16) & 0xFF;
        assertEquals((0x33 + 0x11) / 2, r, 1);
    }

    @Test
    void testMcaRegionReaderFallbackMissingAndSyntheticChunk(@TempDir Path tempDir) throws Exception {
        // Missing file returns clean empty result
        McaRegionReader.RegionRenderResult missing = McaRegionReader.readRegion(
                tempDir.resolve("missing.mca"), "minecraft:overworld", 0, 0, false);
        assertEquals(0, missing.renderedChunks);

        // Construct minimal valid Anvil v1 region file (r.0.0.mca) with 1 chunk at (0, 0)
        ByteArrayOutputStream nbtRaw = new ByteArrayOutputStream();
        DataOutputStream nbt = new DataOutputStream(nbtRaw);
        // Root Compound ""
        nbt.writeByte(10);
        writeUtf(nbt, "");
        // Sub-compound "Level"
        nbt.writeByte(10);
        writeUtf(nbt, "Level");
        // ByteArray "Biomes" (256 bytes)
        nbt.writeByte(7);
        writeUtf(nbt, "Biomes");
        nbt.writeInt(256);
        nbt.write(new byte[256]);
        // List "Sections" of Compound (1 section at Y=4)
        nbt.writeByte(9);
        writeUtf(nbt, "Sections");
        nbt.writeByte(10);
        nbt.writeInt(1);
        // Section Y=4
        nbt.writeByte(1);
        writeUtf(nbt, "Y");
        nbt.writeByte(4);
        // Blocks 4096 bytes filled with ID 2 (grass)
        byte[] blocks = new byte[4096];
        for (int i = 0; i < blocks.length; i++) blocks[i] = 2;
        nbt.writeByte(7);
        writeUtf(nbt, "Blocks");
        nbt.writeInt(blocks.length);
        nbt.write(blocks);
        nbt.writeByte(0); // End section
        nbt.writeByte(0); // End Level
        nbt.writeByte(0); // End Root
        nbt.flush();

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream dos = new DeflaterOutputStream(compressed)) {
            dos.write(nbtRaw.toByteArray());
        }
        byte[] zlibBytes = compressed.toByteArray();

        Path mca = tempDir.resolve("r.0.0.mca");
        try (RandomAccessFile raf = new RandomAccessFile(mca.toFile(), "rw")) {
            byte[] header = new byte[8192];
            // Chunk (0,0) at sector 2, sector count 1
            header[0] = 0;
            header[1] = 0;
            header[2] = 2;
            header[3] = 1;
            raf.write(header);
            raf.seek(2L * 4096L);
            raf.writeInt(zlibBytes.length + 1);
            raf.writeByte(2); // Zlib compression type
            raf.write(zlibBytes);
            raf.setLength(3L * 4096L);
        }

        McaRegionReader.RegionRenderResult parsed = McaRegionReader.readRegion(
                mca, "minecraft:overworld", 0, 0, false);
        assertEquals(1, parsed.renderedChunks);
        assertNotEquals(0, (parsed.argbPixels[0] >>> 24) & 0xFF);
    }

    @Test
    void testWhitelistUuidAndServerJsonCrud(@TempDir Path tempDir) throws Exception {
        String steveOffline = WhitelistService.computeOfflineUuid("Steve");
        assertEquals("5627dd98-e6be-3c21-b8a8-e92344183641".length(), steveOffline.length());
        assertEquals(steveOffline, WhitelistService.computeOfflineUuid("Steve"));
        assertNotEquals(steveOffline, WhitelistService.computeOfflineUuid("Alex"));

        assertEquals(
                "069a79f4-44e9-4726-a5be-fca90e38aaf5",
                WhitelistService.formatHyphenatedUuid("069a79f444e94726a5befca90e38aaf5")
        );

        Path serverDir = tempDir.resolve("server");
        Path configDir = serverDir.resolve("config");
        Path worldDir = serverDir.resolve("world");
        Files.createDirectories(configDir);
        Files.createDirectories(worldDir);
        GameAdapter adapter = createMockAdapter(serverDir, configDir, worldDir, Collections.<String>emptyList());
        AdapterRegistry.initialize(adapter);

        assertTrue(WhitelistService.listServerWhitelistEntries().isEmpty());
        WhitelistService.addWhitelistEntryToServer("Notch", "069a79f444e94726a5befca90e38aaf5");
        List<Map<String, String>> entries = WhitelistService.listServerWhitelistEntries();
        assertEquals(1, entries.size());
        assertEquals("Notch", entries.get(0).get("name"));
        assertEquals("069a79f4-44e9-4726-a5be-fca90e38aaf5", entries.get(0).get("uuid"));

        assertTrue(WhitelistService.removeWhitelistEntryFromServer("Notch"));
        assertTrue(WhitelistService.listServerWhitelistEntries().isEmpty());
    }

    @Test
    void testWorldMigrationSettingsModConfigAndMessages(@TempDir Path tempDir) throws Exception {
        Path serverDir = tempDir.resolve("server");
        Path configDir = serverDir.resolve("config");
        Path worldDir = serverDir.resolve("world");
        Files.createDirectories(configDir);
        Files.createDirectories(worldDir);
        Files.write(serverDir.resolve("server.properties"),
                "white-list=true\nonline-mode=false\nmotd=Test Server\n".getBytes(StandardCharsets.UTF_8));
        Files.write(configDir.resolve("example_mod.toml"),
                "[general]\nenabled = true\nrate = 10\n".getBytes(StandardCharsets.UTF_8));

        // Seed a legacy DB in config/utopia_server_panel.db to verify one-time migration
        Path legacyDb = configDir.resolve("utopia_server_panel.db");
        Files.write(legacyDb, new byte[0]);
        Files.deleteIfExists(legacyDb);
        // Create minimal valid sqlite file in legacy path using MyBatisFactory first
        MyBatisFactory.initialize(legacyDb);
        Schema.initialize();
        MyBatisFactory.shutdown();
        assertTrue(Files.isRegularFile(legacyDb));

        List<String> executedCommands = new ArrayList<String>();
        GameAdapter adapter = createMockAdapter(serverDir, configDir, worldDir, executedCommands);
        AdapterRegistry.initialize(adapter);

        Path dbPath = WorldRegistryService.initializeForActiveWorld(adapter);
        assertTrue(dbPath.startsWith(worldDir));
        assertTrue(Files.isRegularFile(dbPath));
        assertTrue(Files.isRegularFile(configDir.resolve("utopia_server_panel.db.migrated")));
        assertTrue(Files.isRegularFile(configDir.resolve(WorldRegistryService.REGISTRY_FILE_NAME)));

        MyBatisFactory.initialize(dbPath);
        Schema.initialize();
        SettingsService.reload();

        // Create two users for messaging tests
        final int[] userIds = new int[2];
        MyBatisFactory.doWork(session -> {
            UserMapper um = session.getMapper(UserMapper.class);
            long now = System.currentTimeMillis() / 1000L;
            UserAccount u1 = new UserAccount();
            u1.setUsername("alice");
            u1.setNickname("Alice");
            u1.setEmail("alice@example.com");
            u1.setPasswordHash("x");
            u1.setRoleId(1);
            u1.setStatus("active");
            u1.setLocale("en-US");
            u1.setCreatedAt(now);
            u1.setUpdatedAt(now);
            um.insert(u1);
            userIds[0] = u1.getId();

            UserAccount u2 = new UserAccount();
            u2.setUsername("bob");
            u2.setNickname("Bob");
            u2.setEmail("bob@example.com");
            u2.setPasswordHash("x");
            u2.setRoleId(2);
            u2.setStatus("active");
            u2.setLocale("en-US");
            u2.setCreatedAt(now);
            u2.setUpdatedAt(now);
            um.insert(u2);
            userIds[1] = u2.getId();
        });

        // MessageService tests
        InSiteMessage mail = MessageService.sendDirectMessage(userIds[0], null, "alice", "bob", "Hello", "Test body");
        assertTrue(mail.getId() > 0);
        assertEquals(1, MessageService.getUnreadCount(userIds[1]));
        assertTrue(MessageService.markRead(userIds[1], mail.getId()));
        assertEquals(0, MessageService.getUnreadCount(userIds[1]));

        // Unbound admin cannot send public chat; bind player first and verify
        boolean unboundBlocked = false;
        try {
            MessageService.sendWebPublicChat(userIds[0], true, "Should fail");
        } catch (IllegalStateException expected) {
            unboundBlocked = true;
        }
        assertTrue(unboundBlocked);

        String aliceBindCode = com.utopiaxc.utopiaserverpanel.web.service.BindingService.generateBindingCode(
                "00000000-0000-3000-8000-0000000000aa", "AliceMC");
        assertTrue(com.utopiaxc.utopiaserverpanel.web.service.BindingService
                .bindUserWithCode(userIds[0], aliceBindCode).success());

        // Web public chat sends command & persists
        InGameChatEntry chat = MessageService.sendWebPublicChat(userIds[0], true, "Hello world");
        assertEquals("public", chat.getChannel());
        assertEquals("AliceMC", chat.getSenderName());
        assertFalse(executedCommands.isEmpty());
        List<InGameChatEntry> history = MessageService.listChatHistory(userIds[0], true, "public", 1, 20);
        assertFalse(history.isEmpty());

        // ModConfigService tests
        Map<String, Object> modOverview = ModConfigService.getOverview();
        assertNotNull(modOverview.get("mods"));
        ModConfigService.saveConfigFile("config/example_mod.toml", "[general]\nenabled = false\n", null);
        assertTrue(ModConfigService.isPendingRestart());
        assertTrue(Files.isRegularFile(configDir.resolve("example_mod.toml.bak")));

        // World stats list
        List<Map<String, Object>> worlds = WorldRegistryService.listWorldsWithStats();
        assertFalse(worlds.isEmpty());
        assertEquals(Boolean.TRUE, worlds.get(0).get("active"));
        assertEquals("world", worlds.get(0).get("worldName"));
        assertTrue(worlds.get(0).containsKey("waypointCount"));
        assertTrue(worlds.get(0).containsKey("whitelistPendingCount"));

        // String-array whitelist.json compatibility & live toggle + status keys
        Files.write(serverDir.resolve("whitelist.json"),
                "[\"UtopiaXC\", {\"name\": \"Alice\", \"uuid\": \"00000000-0000-3000-8000-000000000001\"}]".getBytes(StandardCharsets.UTF_8));
        List<Map<String, String>> wlEntries = WhitelistService.listServerWhitelistEntries();
        assertEquals(2, wlEntries.size());
        assertEquals("UtopiaXC", wlEntries.get(0).get("name"));
        assertFalse(wlEntries.get(0).get("uuid").isEmpty());
        assertTrue(WhitelistService.removeWhitelistEntryFromServer("UtopiaXC"));
        assertEquals(1, WhitelistService.listServerWhitelistEntries().size());

        SettingsService.set("whitelist_management_enabled", "true");
        Map<String, Object> pubStatus = WhitelistService.getPublicStatus(userIds[0]);
        assertEquals(Boolean.TRUE, pubStatus.get("featureVisible"));
        assertEquals(Boolean.TRUE, pubStatus.get("visible"));
        assertFalse(WhitelistService.setServerWhitelistEnabled(false));
        assertTrue(executedCommands.contains("whitelist off"));
        assertFalse(SettingsService.isWhitelistManagementEnabled());

        // Map defaults: rendering/players/waypoints readonly, entities/structures/teleport full
        Map<String, String> vis = SettingsService.getMapLayerVisibility();
        assertEquals("readonly", vis.get("terrain"));
        assertEquals("readonly", vis.get("grid"));
        assertEquals("readonly", vis.get("players"));
        assertEquals("readonly", vis.get("waypoints"));
        assertEquals("full", vis.get("mobsHostile"));
        assertEquals("full", vis.get("structures"));
        assertEquals("full", vis.get("teleport"));

        Map<String, Object> guestBoot = MapService.getBootstrap(null);
        @SuppressWarnings("unchecked")
        Map<String, Boolean> guestVis = (Map<String, Boolean>) guestBoot.get("visibleLayers");
        assertEquals(Boolean.TRUE, guestVis.get("terrain"));
        assertEquals(Boolean.FALSE, guestVis.get("structures"));
        assertEquals(Boolean.FALSE, guestVis.get("teleport"));

        MapService.createWaypoint(userIds[0], "Spawn", "minecraft:overworld", 0, 64, 0, "#3b82f6", "pin", true);
        Map<String, Object> overlays = MapService.getOverlays("minecraft:overworld", userIds[0], null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> wps = (List<Map<String, Object>>) overlays.get("waypoints");
        assertEquals(1, wps.size());
        assertEquals("waypoint", wps.get(0).get("category"));
        assertEquals("Spawn", wps.get(0).get("name"));
        List<Map<String, Object>> worldsAfter = WorldRegistryService.listWorldsWithStats();
        assertEquals(Integer.valueOf(1), worldsAfter.get(0).get("waypointCount"));

        // Favicon customization & reset
        assertFalse(SettingsService.hasCustomFavicon());
        assertEquals("default", SettingsService.getFaviconVersion());
        Map<String, Object> defaultSite = SettingsService.getPublicSiteInfo();
        assertEquals(Boolean.FALSE, defaultSite.get("hasCustomFavicon"));
        assertEquals("/api/site/favicon?v=default", defaultSite.get("faviconUrl"));

        SettingsService.saveFavicon("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
        assertTrue(SettingsService.hasCustomFavicon());
        assertNotNull(SettingsService.getCustomFaviconPayload());
        assertEquals("image/png", SettingsService.getCustomFaviconPayload().getContentType());
        assertFalse("default".equals(SettingsService.getFaviconVersion()));

        boolean rejectedBadMime = false;
        try {
            SettingsService.saveFavicon("data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==");
        } catch (IllegalArgumentException expected) {
            rejectedBadMime = true;
        }
        assertTrue(rejectedBadMime);

        SettingsService.resetFavicon();
        assertFalse(SettingsService.hasCustomFavicon());
        assertEquals("default", SettingsService.getFaviconVersion());
        assertNull(SettingsService.getCustomFaviconPayload());
    }

    private static void writeUtf(DataOutputStream dos, String s) throws Exception {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        dos.writeShort(bytes.length);
        dos.write(bytes);
    }

    private static GameAdapter createMockAdapter(Path serverDir, Path configDir, Path worldDir, List<String> cmds) {
        return (GameAdapter) Proxy.newProxyInstance(
                GameAdapter.class.getClassLoader(),
                new Class<?>[]{GameAdapter.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getServerDirectory".equals(name)) return serverDir;
                    if ("getConfigDirectory".equals(name)) return configDir;
                    if ("getWorldDirectory".equals(name)) return worldDir;
                    if ("getWorldName".equals(name)) return "world";
                    if ("getModVersion".equals(name)) return "2.0.0-test";
                    if ("getMinecraftVersion".equals(name)) return "1.21.1";
                    if ("getLoaderName".equals(name)) return "neoforge";
                    if ("executeCommand".equals(name) && args != null && args.length == 1) {
                        cmds.add(String.valueOf(args[0]));
                        return true;
                    }
                    if ("broadcastPublicChat".equals(name) && args != null && args.length == 2) {
                        cmds.add("say [Web] <" + args[0] + "> " + args[1]);
                        return null;
                    }
                    if ("sendDirectPlayerMessage".equals(name) && args != null && args.length == 2) {
                        cmds.add("msg " + args[0] + " " + args[1]);
                        return null;
                    }
                    if ("getServerProperties".equals(name)) {
                        ServerPropertiesSnapshot snap = new ServerPropertiesSnapshot();
                        snap.setWhiteList(true);
                        snap.setOnlineMode(false);
                        return snap;
                    }
                    if ("getServerStatus".equals(name)) {
                        return new ServerStatusSnapshot(0, 20, "Test Server", "1.21.1", "world", 20.0, 10.0);
                    }
                    if ("getLoadedMods".equals(name)) {
                        return Collections.singletonList(new ModEntryDto(
                                "example_mod", "Example Mod", "1.0.0", "Desc", "Dev", "", null));
                    }
                    if ("getMapDimensions".equals(name)
                            || "listMapMarkers".equals(name)
                            || "getRealtimePlayerPositions".equals(name)
                            || "getOnlinePlayers".equals(name)
                            || "getCommandSuggestions".equals(name)) {
                        return Collections.emptyList();
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
