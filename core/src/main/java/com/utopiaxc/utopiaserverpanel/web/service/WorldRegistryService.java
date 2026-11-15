package com.utopiaxc.utopiaserverpanel.web.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.adapter.AdapterRegistry;
import com.utopiaxc.utopiaserverpanel.adapter.GameAdapter;
import com.utopiaxc.utopiaserverpanel.adapter.model.ServerStatusSnapshot;
import com.utopiaxc.utopiaserverpanel.logging.PanelLog;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

/**
 * Manages per-save SQLite database placement, one-time migration from legacy {@code config/}
 * database files, and the lightweight save index stored at {@code config/utopia_server_panel_worlds.json}.
 */
public final class WorldRegistryService {
    public static final String REGISTRY_FILE_NAME = "utopia_server_panel_worlds.json";
    public static final String WORLD_DATA_DIR_NAME = "utopia_server_panel";
    public static final String DB_FILE_NAME = "utopia_server_panel.db";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Object LOCK = new Object();

    private static volatile Path configDir;
    private static volatile Path serverDir;
    private static volatile Path activeWorldDir;
    private static volatile Path activePanelDataDir;
    private static volatile Path activeDbPath;

    private WorldRegistryService() {}

    public static Path initializeForActiveWorld(GameAdapter adapter) {
        synchronized (LOCK) {
            configDir = adapter.getConfigDirectory().toAbsolutePath().normalize();
            serverDir = adapter.getServerDirectory() != null
                    ? adapter.getServerDirectory().toAbsolutePath().normalize()
                    : configDir.getParent();
            Path rawWorldDir = adapter.getWorldDirectory();
            activeWorldDir = rawWorldDir != null ? rawWorldDir.toAbsolutePath().normalize() : null;

            if (activeWorldDir != null) {
                activePanelDataDir = activeWorldDir.resolve(WORLD_DATA_DIR_NAME);
                activeDbPath = activePanelDataDir.resolve(DB_FILE_NAME);
            } else {
                activePanelDataDir = configDir.resolve(WORLD_DATA_DIR_NAME);
                activeDbPath = configDir.resolve(DB_FILE_NAME);
            }

            try {
                Files.createDirectories(activePanelDataDir);
            } catch (IOException e) {
                PanelLog.get().warn("Failed to create per-world panel directory {}: {}", activePanelDataDir, e.getMessage());
            }

            migrateLegacyConfigDbIfNeeded();
            updateRegistryEntry(adapter);
            return activeDbPath;
        }
    }

    public static Path getActiveWorldDir() {
        return activeWorldDir;
    }

    public static Path getActivePanelDataDir() {
        return activePanelDataDir;
    }

    public static Path getActiveDbPath() {
        return activeDbPath;
    }

    public static Path getMapCacheDir() {
        Path base = activePanelDataDir != null ? activePanelDataDir : configDir;
        return base != null ? base.resolve("map_cache") : null;
    }

    private static void migrateLegacyConfigDbIfNeeded() {
        if (configDir == null || activeWorldDir == null || activeDbPath == null) {
            return;
        }
        if (Files.exists(activeDbPath)) {
            return;
        }
        Path legacyDb = configDir.resolve(DB_FILE_NAME);
        if (!Files.isRegularFile(legacyDb)) {
            return;
        }
        try {
            Files.createDirectories(activeDbPath.getParent());
            Files.copy(legacyDb, activeDbPath, StandardCopyOption.REPLACE_EXISTING);
            copySidecarIfPresent(configDir.resolve(DB_FILE_NAME + "-wal"), activeDbPath.getParent().resolve(DB_FILE_NAME + "-wal"));
            copySidecarIfPresent(configDir.resolve(DB_FILE_NAME + "-shm"), activeDbPath.getParent().resolve(DB_FILE_NAME + "-shm"));

            Path backup = configDir.resolve(DB_FILE_NAME + ".migrated");
            if (!Files.exists(backup)) {
                Files.move(legacyDb, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            deleteIfExists(configDir.resolve(DB_FILE_NAME + "-wal"));
            deleteIfExists(configDir.resolve(DB_FILE_NAME + "-shm"));
            PanelLog.get().info("Migrated legacy database from {} to per-save path {}", legacyDb, activeDbPath);
        } catch (Exception e) {
            PanelLog.get().warn("Failed to migrate legacy config database to {}: {}", activeDbPath, e.getMessage());
        }
    }

    private static void copySidecarIfPresent(Path src, Path dst) {
        if (Files.isRegularFile(src)) {
            try {
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {}
        }
    }

    private static void deleteIfExists(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {}
    }

    private static void updateRegistryEntry(GameAdapter adapter) {
        if (configDir == null || activeWorldDir == null) {
            return;
        }
        try {
            Path regPath = configDir.resolve(REGISTRY_FILE_NAME);
            JsonObject root = readRegistryJson(regPath);
            JsonArray worlds = root.has("worlds") && root.get("worlds").isJsonArray()
                    ? root.getAsJsonArray("worlds")
                    : new JsonArray();

            String normWorldPath = normalizePathString(activeWorldDir);
            String folderName = activeWorldDir.getFileName() != null ? activeWorldDir.getFileName().toString() : "world";
            String worldName = folderName;
            String mcVersion = "";
            try {
                ServerStatusSnapshot status = adapter.getServerStatus();
                if (status != null) {
                    if (status.getWorldName() != null && !status.getWorldName().trim().isEmpty()) {
                        worldName = status.getWorldName().trim();
                    }
                    if (status.getVersion() != null) {
                        mcVersion = status.getVersion();
                    }
                }
            } catch (Throwable ignored) {}

            long now = System.currentTimeMillis() / 1000L;
            JsonObject matched = null;
            for (int i = 0; i < worlds.size(); i++) {
                JsonElement el = worlds.get(i);
                if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    String p = obj.has("worldPath") ? obj.get("worldPath").getAsString() : "";
                    if (normWorldPath.equals(p)) {
                        matched = obj;
                        break;
                    }
                }
            }
            if (matched == null) {
                matched = new JsonObject();
                matched.addProperty("worldPath", normWorldPath);
                matched.addProperty("firstSeenAt", now);
                worlds.add(matched);
            }
            matched.addProperty("folderName", folderName);
            matched.addProperty("worldName", worldName);
            matched.addProperty("dbPath", normalizePathString(activeDbPath));
            matched.addProperty("platform", adapter.getPlatformName());
            if (!mcVersion.isEmpty()) {
                matched.addProperty("mcVersion", mcVersion);
            }
            matched.addProperty("lastSeenAt", now);

            root.addProperty("activeWorldPath", normWorldPath);
            root.addProperty("updatedAt", now);
            root.add("worlds", worlds);
            Files.write(regPath, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            PanelLog.get().warn("Failed to update world registry: {}", e.getMessage());
        }
    }

    private static JsonObject readRegistryJson(Path regPath) {
        if (Files.isRegularFile(regPath)) {
            try {
                String raw = new String(Files.readAllBytes(regPath), StandardCharsets.UTF_8);
                JsonObject parsed = GSON.fromJson(raw, JsonObject.class);
                if (parsed != null) {
                    return parsed;
                }
            } catch (Exception ignored) {}
        }
        JsonObject root = new JsonObject();
        root.add("worlds", new JsonArray());
        return root;
    }

    /**
     * Discover all Minecraft saves on this server/client instance plus entries registered in
     * {@code config/utopia_server_panel_worlds.json}, enriched with per-save panel database metrics.
     */
    public static List<Map<String, Object>> listWorldsWithStats() {
        synchronized (LOCK) {
            Map<String, JsonObject> regMap = new LinkedHashMap<>();
            if (configDir != null) {
                JsonObject root = readRegistryJson(configDir.resolve(REGISTRY_FILE_NAME));
                if (root.has("worlds") && root.get("worlds").isJsonArray()) {
                    for (JsonElement el : root.getAsJsonArray("worlds")) {
                        if (el.isJsonObject()) {
                            JsonObject obj = el.getAsJsonObject();
                            String wp = obj.has("worldPath") ? obj.get("worldPath").getAsString() : "";
                            if (!wp.isEmpty()) {
                                regMap.put(wp, obj);
                            }
                        }
                    }
                }
            }

            Set<Path> candidateWorldDirs = new LinkedHashSet<>();
            if (activeWorldDir != null) {
                candidateWorldDirs.add(activeWorldDir);
            }
            for (String p : regMap.keySet()) {
                try {
                    Path path = Paths.get(p).toAbsolutePath().normalize();
                    if (Files.isDirectory(path)) {
                        candidateWorldDirs.add(path);
                    }
                } catch (Exception ignored) {}
            }
            scanSaveDirectories(serverDir, candidateWorldDirs);
            if (serverDir != null) {
                scanSaveDirectories(serverDir.resolve("saves"), candidateWorldDirs);
            }

            String activeNorm = activeWorldDir != null ? normalizePathString(activeWorldDir) : "";
            List<Map<String, Object>> result = new ArrayList<>();
            for (Path dir : candidateWorldDirs) {
                String normPath = normalizePathString(dir);
                boolean isActive = !activeNorm.isEmpty() && activeNorm.equals(normPath);
                JsonObject meta = regMap.get(normPath);

                Map<String, Object> item = new LinkedHashMap<>();
                String folderName = dir.getFileName() != null ? dir.getFileName().toString() : normPath;
                String worldName = meta != null && meta.has("worldName") ? meta.get("worldName").getAsString() : folderName;
                if (isActive) {
                    try {
                        GameAdapter adapter = AdapterRegistry.getAdapter();
                        ServerStatusSnapshot st = adapter != null ? adapter.getServerStatus() : null;
                        if (st != null && st.getWorldName() != null && !st.getWorldName().trim().isEmpty()) {
                            worldName = st.getWorldName().trim();
                        }
                    } catch (Throwable ignored) {}
                }

                Path panelDir = dir.resolve(WORLD_DATA_DIR_NAME);
                Path dbFile = panelDir.resolve(DB_FILE_NAME);
                boolean dbExists = Files.isRegularFile(dbFile);
                long dbSize = dbExists ? safeFileSize(dbFile) : 0L;
                long mapCacheSize = safeDirSize(panelDir.resolve("map_cache"));

                item.put("worldPath", normPath);
                item.put("folderName", folderName);
                item.put("worldName", worldName);
                item.put("active", Boolean.valueOf(isActive));
                item.put("dbPath", normalizePathString(dbFile));
                item.put("dbExists", Boolean.valueOf(dbExists));
                item.put("dbSizeBytes", Long.valueOf(dbSize));
                item.put("mapCacheBytes", Long.valueOf(mapCacheSize));
                item.put("platform", meta != null && meta.has("platform") ? meta.get("platform").getAsString() : "");
                item.put("mcVersion", meta != null && meta.has("mcVersion") ? meta.get("mcVersion").getAsString() : "");
                item.put("lastSeenAt", meta != null && meta.has("lastSeenAt") ? Long.valueOf(meta.get("lastSeenAt").getAsLong()) : null);
                item.put("hasLevelDat", Boolean.valueOf(Files.isRegularFile(dir.resolve("level.dat"))));

                if (dbExists) {
                    populateDbStats(item, dbFile, isActive);
                } else {
                    item.put("userCount", Integer.valueOf(0));
                    item.put("playerCount", Integer.valueOf(0));
                    item.put("bindingCount", Integer.valueOf(0));
                    item.put("perfLogCount", Integer.valueOf(0));
                    item.put("whitelistAppCount", Integer.valueOf(0));
                    item.put("whitelistPendingCount", Integer.valueOf(0));
                    item.put("waypointCount", Integer.valueOf(0));
                    item.put("messageCount", Integer.valueOf(0));
                    item.put("chatLogCount", Integer.valueOf(0));
                }
                result.add(item);
            }

            result.sort((a, b) -> {
                boolean actA = Boolean.TRUE.equals(a.get("active"));
                boolean actB = Boolean.TRUE.equals(b.get("active"));
                if (actA != actB) return actA ? -1 : 1;
                boolean dbA = Boolean.TRUE.equals(a.get("dbExists"));
                boolean dbB = Boolean.TRUE.equals(b.get("dbExists"));
                if (dbA != dbB) return dbA ? -1 : 1;
                return String.valueOf(a.get("worldName")).compareToIgnoreCase(String.valueOf(b.get("worldName")));
            });
            return result;
        }
    }

    private static void scanSaveDirectories(Path parentDir, Set<Path> out) {
        if (parentDir == null || !Files.isDirectory(parentDir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parentDir)) {
            for (Path sub : stream) {
                if (Files.isDirectory(sub)) {
                    if (Files.isRegularFile(sub.resolve("level.dat"))
                            || Files.isRegularFile(sub.resolve(WORLD_DATA_DIR_NAME).resolve(DB_FILE_NAME))) {
                        out.add(sub.toAbsolutePath().normalize());
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    private static void populateDbStats(Map<String, Object> item, Path dbFile, boolean isActive) {
        if (isActive && MyBatisFactory.isInitialized()) {
            try (Connection conn = MyBatisFactory.getRawConnection()) {
                readCounts(conn, item);
                return;
            } catch (Exception ignored) {}
        }
        String url = "jdbc:sqlite:" + normalizePathString(dbFile) + "?mode=ro";
        try (Connection conn = DriverManager.getConnection(url)) {
            readCounts(conn, item);
        } catch (Exception e) {
            item.put("userCount", Integer.valueOf(0));
            item.put("playerCount", Integer.valueOf(0));
            item.put("bindingCount", Integer.valueOf(0));
            item.put("perfLogCount", Integer.valueOf(0));
            item.put("whitelistAppCount", Integer.valueOf(0));
            item.put("whitelistPendingCount", Integer.valueOf(0));
            item.put("waypointCount", Integer.valueOf(0));
            item.put("messageCount", Integer.valueOf(0));
            item.put("chatLogCount", Integer.valueOf(0));
        }
    }

    private static void readCounts(Connection conn, Map<String, Object> item) {
        item.put("userCount", Integer.valueOf(safeCount(conn, "users")));
        item.put("playerCount", Integer.valueOf(safeCount(conn, "mc_players")));
        item.put("bindingCount", Integer.valueOf(safeCount(conn, "player_bindings")));
        item.put("perfLogCount", Integer.valueOf(safeCount(conn, "perf_logs")));
        item.put("whitelistAppCount", Integer.valueOf(safeCount(conn, "whitelist_applications")));
        item.put("whitelistPendingCount", Integer.valueOf(safeQueryCount(conn, "SELECT COUNT(*) FROM whitelist_applications WHERE status = 'pending'")));
        item.put("waypointCount", Integer.valueOf(safeCount(conn, "map_waypoints")));
        item.put("messageCount", Integer.valueOf(safeCount(conn, "in_site_messages")));
        item.put("chatLogCount", Integer.valueOf(safeCount(conn, "ingame_chat_logs")));
    }

    private static int safeCount(Connection conn, String table) {
        return safeQueryCount(conn, "SELECT COUNT(*) FROM " + table);
    }

    private static int safeQueryCount(Connection conn, String sql) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static long safeFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }

    private static long safeDirSize(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return 0L;
        }
        final long[] total = {0L};
        try {
            Files.walk(dir).filter(Files::isRegularFile).forEach(p -> {
                try {
                    total[0] += Files.size(p);
                } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
        return total[0];
    }

    private static String normalizePathString(Path p) {
        return p.toAbsolutePath().normalize().toString().replace("\\", "/");
    }
}
