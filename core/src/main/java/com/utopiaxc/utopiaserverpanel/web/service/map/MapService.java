package com.utopiaxc.utopiaserverpanel.web.service.map;

import com.google.gson.Gson;
import com.utopiaxc.utopiaserverpanel.adapter.AdapterRegistry;
import com.utopiaxc.utopiaserverpanel.adapter.GameAdapter;
import com.utopiaxc.utopiaserverpanel.logging.PanelLog;
import com.utopiaxc.utopiaserverpanel.spi.map.MapChunkRenderData;
import com.utopiaxc.utopiaserverpanel.spi.map.MapDimensionInfo;
import com.utopiaxc.utopiaserverpanel.spi.map.MapMarkerDto;
import com.utopiaxc.utopiaserverpanel.spi.map.PlayerPositionSnapshot;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.MapWaypointMapper;
import com.utopiaxc.utopiaserverpanel.web.db.model.MapWaypoint;
import com.utopiaxc.utopiaserverpanel.web.db.model.PlayerBindingInfo;
import com.utopiaxc.utopiaserverpanel.web.service.BindingService;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionLevel;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionService;
import com.utopiaxc.utopiaserverpanel.web.service.SettingsService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Isolated JourneyMap-style web map engine.
 * <p>
 * Combines live budgeted dirty-chunk captures with cached Anvil (.mca) region rendering
 * and isolates every entry point behind a Throwable circuit-breaker so any MC version quirk
 * never destabilizes gameplay or other panel modules.
 * </p>
 */
public final class MapService {
    private static final Gson GSON = new Gson();
    private static final int REGION_PX = 512;
    private static final int MAX_DIRTY_QUEUE = 2048;
    private static final int MAX_REGION_CACHE = 16;

    private static volatile boolean mapHealthy = true;
    private static volatile String lastMapError = null;
    private static ScheduledExecutorService flushScheduler;

    private static final ConcurrentLinkedQueue<ChunkKey> DIRTY_QUEUE = new ConcurrentLinkedQueue<ChunkKey>();
    private static final Set<ChunkKey> DIRTY_SET = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger DIRTY_COUNT = new AtomicInteger(0);

    private static final Object BUFFER_LOCK = new Object();
    private static final Map<RegionKey, RegionBuffer> REGION_BUFFERS =
            new LinkedHashMap<RegionKey, RegionBuffer>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<RegionKey, RegionBuffer> eldest) {
                    if (size() > MAX_REGION_CACHE) {
                        flushRegionSafe(eldest.getKey(), eldest.getValue());
                        return true;
                    }
                    return false;
                }
            };

    private static final ConcurrentHashMap<RegionKey, List<MapMarkerDto>> STRUCTURE_CACHE =
            new ConcurrentHashMap<RegionKey, List<MapMarkerDto>>();
    private static final ConcurrentHashMap<String, CachedMarkers> LIVE_MARKER_CACHE =
            new ConcurrentHashMap<String, CachedMarkers>();

    private static volatile byte[] emptyTilePng;

    private MapService() {}

    public static synchronized void start() {
        mapHealthy = true;
        lastMapError = null;
        if (flushScheduler != null) return;
        flushScheduler = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "UtopiaPanel-MapCacheFlusher");
                t.setDaemon(true);
                return t;
            }
        });
        flushScheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                try {
                    flushDirtyBuffers();
                } catch (Throwable t) {
                    tripCircuitBreaker("Background cache flush failed", t);
                }
            }
        }, 15, 15, TimeUnit.SECONDS);
    }

    public static synchronized void stop() {
        if (flushScheduler != null) {
            flushScheduler.shutdownNow();
            flushScheduler = null;
        }
        try {
            flushDirtyBuffers();
        } catch (Throwable ignored) {}
        synchronized (BUFFER_LOCK) {
            REGION_BUFFERS.clear();
        }
        DIRTY_QUEUE.clear();
        DIRTY_SET.clear();
        DIRTY_COUNT.set(0);
    }

    public static boolean isMapHealthy() { return mapHealthy; }
    public static String getLastMapError() { return lastMapError; }

    public static void resetHealth() {
        mapHealthy = true;
        lastMapError = null;
    }

    /**
     * Queue a chunk for lazy off-peak map cache update when gameplay alters terrain or loads a chunk.
     */
    public static void markChunkDirty(String dimension, int chunkX, int chunkZ) {
        if (!mapHealthy || dimension == null || !SettingsService.isMapEnabled()) return;
        try {
            if (DIRTY_COUNT.get() >= MAX_DIRTY_QUEUE) return;
            ChunkKey key = new ChunkKey(dimension, chunkX, chunkZ);
            if (DIRTY_SET.add(key)) {
                DIRTY_COUNT.incrementAndGet();
                DIRTY_QUEUE.offer(key);
            }
        } catch (Throwable t) {
            tripCircuitBreaker("markChunkDirty error", t);
        }
    }

    /**
     * Drain up to {@code maxChunksPerTick} queued dirty chunks on the server tick thread.
     */
    public static void onServerTickBudgeted() {
        if (!mapHealthy || DIRTY_QUEUE.isEmpty()) return;
        if (!SettingsService.isMapEnabled()) {
            DIRTY_QUEUE.clear();
            DIRTY_SET.clear();
            DIRTY_COUNT.set(0);
            return;
        }
        try {
            GameAdapter adapter = AdapterRegistry.getNullable();
            if (adapter == null) return;
            int budget = Math.max(1, Math.min(8, SettingsService.getInt("map_render_budget", 2)));
            int processed = 0;
            while (processed < budget) {
                ChunkKey key = DIRTY_QUEUE.poll();
                if (key == null) break;
                DIRTY_SET.remove(key);
                DIRTY_COUNT.decrementAndGet();

                MapChunkRenderData data = adapter.captureLiveChunkForMap(key.dimension, key.chunkX, key.chunkZ);
                if (data != null) {
                    applyChunkToRegionBuffer(data);
                }
                processed++;
            }
        } catch (Throwable t) {
            tripCircuitBreaker("Server tick map capture failed", t);
        }
    }

    /**
     * Return bootstrap config, available dimensions, and resolved role/layer permissions.
     */
    public static Map<String, Object> getBootstrap(Integer userId) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        boolean enabled = SettingsService.isMapEnabled();
        result.put("enabled", Boolean.valueOf(enabled));
        result.put("healthy", Boolean.valueOf(mapHealthy));
        result.put("lastError", lastMapError);

        int userMapLevel = getUserMapLevel(userId);
        Map<String, String> configuredLayers = SettingsService.getMapLayerVisibility();
        Map<String, Boolean> visibleLayers = new LinkedHashMap<String, Boolean>();
        visibleLayers.put("terrain", Boolean.valueOf(isLayerAllowed(userMapLevel, configuredLayers.get("terrain"))));
        visibleLayers.put("netherCeiling", Boolean.valueOf(isLayerAllowed(userMapLevel, configuredLayers.get("netherCeiling"))));
        visibleLayers.put("grid", Boolean.valueOf(isLayerAllowed(userMapLevel, configuredLayers.get("grid"))));
        visibleLayers.put("coordinates", Boolean.valueOf(isLayerAllowed(userMapLevel, configuredLayers.get("coordinates"))));
        visibleLayers.put("waterDepth", Boolean.valueOf(isLayerAllowed(userMapLevel, configuredLayers.get("waterDepth"))));
        visibleLayers.put("heightShading", Boolean.valueOf(isLayerAllowed(userMapLevel, configuredLayers.get("heightShading"))));
        visibleLayers.put("biomeTint", Boolean.valueOf(isLayerAllowed(userMapLevel, configuredLayers.get("biomeTint"))));
        visibleLayers.put("players", Boolean.valueOf(isLayerAllowed(userMapLevel, configuredLayers.get("players"))));
        visibleLayers.put("waypoints", Boolean.valueOf(isLayerAllowed(userMapLevel, configuredLayers.get("waypoints"))));
        boolean visHostile = isLayerAllowed(userMapLevel, configuredLayers.get("mobsHostile"));
        boolean visPassive = isLayerAllowed(userMapLevel, configuredLayers.get("mobsPassive"));
        boolean visOther = isLayerAllowed(userMapLevel, configuredLayers.get("entities"));
        visibleLayers.put("mobsHostile", Boolean.valueOf(visHostile));
        visibleLayers.put("mobsPassive", Boolean.valueOf(visPassive));
        visibleLayers.put("entitiesOther", Boolean.valueOf(visOther));
        visibleLayers.put("entities", Boolean.valueOf(visHostile || visPassive || visOther));
        visibleLayers.put("structures", Boolean.valueOf(isLayerAllowed(userMapLevel, configuredLayers.get("structures"))));
        visibleLayers.put("teleport", Boolean.valueOf(isLayerAllowed(userMapLevel, configuredLayers.get("teleport"))));

        result.put("userLevel", Integer.valueOf(userMapLevel));
        result.put("canManageWaypoints", Boolean.valueOf(userId != null && userMapLevel >= 2 && visibleLayers.get("waypoints")));
        result.put("canTeleport", Boolean.valueOf(userId != null && visibleLayers.get("teleport")));
        result.put("visibleLayers", visibleLayers);
        result.put("configuredLayers", configuredLayers);
        result.put("dimensions", enabled && mapHealthy ? listDimensions() : Collections.emptyList());
        result.put("defaultDimension", "minecraft:overworld");
        result.put("tileSize", Integer.valueOf(REGION_PX));
        return result;
    }

    public static List<MapDimensionInfo> listDimensions() {
        List<MapDimensionInfo> dims = new ArrayList<MapDimensionInfo>();
        try {
            GameAdapter adapter = AdapterRegistry.getNullable();
            Set<String> seenIds = new HashSet<String>();
            if (adapter != null) {
                List<MapDimensionInfo> liveDims = adapter.getMapDimensions();
                if (liveDims != null) {
                    for (MapDimensionInfo d : liveDims) {
                        if (d != null && seenIds.add(d.getId())) {
                            dims.add(d);
                        }
                    }
                }
            }
            // Merge any saved dimensions found on disk (even if unloaded dynamically)
            Path worldDir = adapter != null ? adapter.getWorldDirectory() : null;
            if (worldDir != null && Files.isDirectory(worldDir)) {
                addDiskDimensionIfPresent(dims, seenIds, worldDir.resolve("region"),
                        "minecraft:overworld", "Overworld", false, -64, 320, 1.0);
                addDiskDimensionIfPresent(dims, seenIds, worldDir.resolve("DIM-1").resolve("region"),
                        "minecraft:the_nether", "The Nether", true, 0, 128, 8.0);
                addDiskDimensionIfPresent(dims, seenIds, worldDir.resolve("DIM1").resolve("region"),
                        "minecraft:the_end", "The End", false, 0, 256, 1.0);

                Path customDimsDir = worldDir.resolve("dimensions");
                if (Files.isDirectory(customDimsDir)) {
                    try (Stream<Path> walk = Files.walk(customDimsDir, 3)) {
                        walk.filter(p -> "region".equals(p.getFileName().toString()) && Files.isDirectory(p))
                                .forEach(regPath -> {
                                    Path rel = customDimsDir.relativize(regPath.getParent());
                                    if (rel.getNameCount() >= 2) {
                                        String ns = rel.getName(0).toString();
                                        StringBuilder pathPart = new StringBuilder();
                                        for (int i = 1; i < rel.getNameCount(); i++) {
                                            if (i > 1) pathPart.append('/');
                                            pathPart.append(rel.getName(i).toString());
                                        }
                                        String dimId = ns + ":" + pathPart;
                                        if (seenIds.add(dimId)) {
                                            dims.add(new MapDimensionInfo(dimId, pathPart.toString(), false, -64, 320, 1.0));
                                        }
                                    }
                                });
                    } catch (IOException ignored) {}
                }
            }
        } catch (Throwable t) {
            tripCircuitBreaker("listDimensions failed", t);
        }
        return dims;
    }

    private static void addDiskDimensionIfPresent(List<MapDimensionInfo> dims, Set<String> seenIds, Path regionDir,
                                                  String id, String name, boolean ceiling, int minY, int maxY, double scale) {
        if (Files.isDirectory(regionDir) && seenIds.add(id)) {
            dims.add(new MapDimensionInfo(id, name, ceiling, minY, maxY, scale));
        }
    }

    /**
     * Return PNG bytes for tile {@code (tx, tz)} at zoom {@code z} in {@code [0, -1, -2]}.
     * At zoom 0, 1 tile = 1 region (512x512 blocks).
     * At zoom -1, 1 tile = 2x2 regions (1024x1024 blocks) rendered into 512x512 px.
     * At zoom -2, 1 tile = 4x4 regions (2048x2048 blocks) rendered into 512x512 px.
     */
    public static byte[] getTilePng(String dimension, int zoom, int tx, int tz) {
        return getTilePng(dimension, zoom, tx, tz, null);
    }

    public static byte[] getTilePng(String dimension, int zoom, int tx, int tz, Integer userId) {
        if (!SettingsService.isMapEnabled() || !mapHealthy) {
            return getEmptyTilePng();
        }
        if (userId != null) {
            int userLevel = getUserMapLevel(userId);
            String terrainVis = SettingsService.getMapLayerVisibility().get("terrain");
            if (!isLayerAllowed(userLevel, terrainVis)) {
                return getEmptyTilePng();
            }
        } else {
            // Guest request when userId == null
            int guestLevel = getUserMapLevel(null);
            String terrainVis = SettingsService.getMapLayerVisibility().get("terrain");
            if (!isLayerAllowed(guestLevel, terrainVis)) {
                return getEmptyTilePng();
            }
        }
        try {
            int clampedZoom = Math.max(-2, Math.min(0, zoom));
            if (clampedZoom == 0) {
                return getOrRenderBaseRegionPng(dimension, tx, tz);
            }
            return getOrCompositeZoomTilePng(dimension, clampedZoom, tx, tz);
        } catch (Throwable t) {
            tripCircuitBreaker("getTilePng failed", t);
            return getEmptyTilePng();
        }
    }

    /**
     * Enumerate region coordinates {@code {rx, rz}} that exist in cache or on disk for quick jump/bounds.
     */
    public static List<int[]> listAvailableRegions(String dimension) {
        List<int[]> regions = new ArrayList<int[]>();
        try {
            Set<Long> seen = new HashSet<Long>();
            Path regDir = resolveRegionDirectory(dimension);
            collectRegionCoordsFromDir(regDir, ".mca", regions, seen);
            Path cacheDir = resolveCacheDimDir(dimension, 0);
            collectRegionCoordsFromDir(cacheDir, ".png", regions, seen);
        } catch (Throwable t) {
            tripCircuitBreaker("listAvailableRegions failed", t);
        }
        return regions;
    }

    private static void collectRegionCoordsFromDir(Path dir, String ext, List<int[]> out, Set<Long> seen) {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.list(dir)) {
            files.forEach(p -> {
                String name = p.getFileName().toString();
                if (name.startsWith("r.") && name.endsWith(ext)) {
                    String middle = name.substring(2, name.length() - ext.length());
                    int dot = middle.indexOf('.');
                    if (dot > 0) {
                        try {
                            int rx = Integer.parseInt(middle.substring(0, dot));
                            int rz = Integer.parseInt(middle.substring(dot + 1));
                            long packed = (((long) rx) << 32) | (rz & 0xFFFFFFFFL);
                            if (seen.add(Long.valueOf(packed))) {
                                out.add(new int[]{rx, rz});
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            });
        } catch (IOException ignored) {}
    }

    /**
     * Collect live markers filtered strictly by user's resolved layer visibility.
     */
    public static Map<String, Object> getOverlays(String dimension, Integer userId, String query) {
        Map<String, Object> res = new LinkedHashMap<String, Object>();
        if (!SettingsService.isMapEnabled() || !mapHealthy) {
            res.put("players", Collections.emptyList());
            res.put("entities", Collections.emptyList());
            res.put("structures", Collections.emptyList());
            res.put("waypoints", Collections.emptyList());
            return res;
        }
        try {
            int userLevel = getUserMapLevel(userId);
            Map<String, String> configured = SettingsService.getMapLayerVisibility();
            String q = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";

            List<Map<String, Object>> players = new ArrayList<Map<String, Object>>();
            if (isLayerAllowed(userLevel, configured.get("players"))) {
                GameAdapter adapter = AdapterRegistry.getNullable();
                if (adapter != null) {
                    List<PlayerPositionSnapshot> livePlayers = adapter.getRealtimePlayerPositions();
                    if (livePlayers != null) {
                        for (PlayerPositionSnapshot p : livePlayers) {
                            if (dimension != null && !dimension.isEmpty() && !dimension.equals(p.getDimension())) {
                                continue;
                            }
                            if (!q.isEmpty() && !p.getName().toLowerCase(Locale.ROOT).contains(q)) {
                                continue;
                            }
                            Map<String, Object> item = new LinkedHashMap<String, Object>();
                            item.put("id", "player:" + p.getUuid());
                            item.put("uuid", p.getUuid());
                            item.put("kind", "player");
                            item.put("category", "player");
                            item.put("typeId", "minecraft:player");
                            item.put("name", p.getName());
                            item.put("dimension", p.getDimension());
                            item.put("x", Double.valueOf(p.getX()));
                            item.put("y", Double.valueOf(p.getY()));
                            item.put("z", Double.valueOf(p.getZ()));
                            item.put("yaw", Float.valueOf(p.getYaw()));
                            item.put("avatarUrl", "https://mc-heads.net/avatar/" + p.getName() + "/32");
                            players.add(item);
                        }
                    }
                }
            }

            boolean allowHostile = isLayerAllowed(userLevel, configured.get("mobsHostile"));
            boolean allowPassive = isLayerAllowed(userLevel, configured.get("mobsPassive"));
            boolean allowOther = isLayerAllowed(userLevel, configured.get("entities"));
            boolean allowEntities = allowHostile || allowPassive || allowOther;
            boolean allowStructures = isLayerAllowed(userLevel, configured.get("structures"));
            List<MapMarkerDto> entities = new ArrayList<MapMarkerDto>();
            List<MapMarkerDto> structures = new ArrayList<MapMarkerDto>();

            if (allowEntities || allowStructures) {
                List<MapMarkerDto> cachedLive = getCachedLiveMarkers(dimension);
                for (MapMarkerDto m : cachedLive) {
                    if (!matchesMarkerFilter(m, q)) continue;
                    if ("entity".equals(m.getKind())) {
                        String cat = m.getCategory();
                        if ("hostile".equals(cat) && allowHostile) {
                            entities.add(m);
                        } else if ("passive".equals(cat) && allowPassive) {
                            entities.add(m);
                        } else if (!"hostile".equals(cat) && !"passive".equals(cat) && allowOther) {
                            entities.add(m);
                        }
                    } else if ("structure".equals(m.getKind()) && allowStructures) {
                        structures.add(m);
                    }
                }
            }

            if (allowStructures) {
                Set<String> seenStructIds = new HashSet<String>();
                for (MapMarkerDto m : structures) seenStructIds.add(m.getId());
                for (Map.Entry<RegionKey, List<MapMarkerDto>> entry : STRUCTURE_CACHE.entrySet()) {
                    if (dimension == null || dimension.equals(entry.getKey().dimension)) {
                        for (MapMarkerDto sm : entry.getValue()) {
                            if (matchesMarkerFilter(sm, q) && seenStructIds.add(sm.getId())) {
                                structures.add(sm);
                            }
                        }
                    }
                }
                // Also load any persisted structure JSON for this dimension if STRUCTURE_CACHE is empty
                loadCachedStructuresFromDisk(dimension, structures, seenStructIds, q);
            }

            List<Map<String, Object>> waypoints = new ArrayList<Map<String, Object>>();
            if (isLayerAllowed(userLevel, configured.get("waypoints"))) {
                final String dimFilter = dimension != null ? dimension : "minecraft:overworld";
                List<MapWaypoint> dbList = MyBatisFactory.doWorkWithResult(session ->
                        session.getMapper(MapWaypointMapper.class).listByDimension(dimFilter));
                if (dbList != null) {
                    for (MapWaypoint wp : dbList) {
                        boolean visible = wp.isGlobal() || (userId != null && userId.equals(wp.getOwnerUserId()));
                        if (!visible) continue;
                        if (q.isEmpty() || (wp.getName() != null && wp.getName().toLowerCase(Locale.ROOT).contains(q))) {
                            Map<String, Object> item = new LinkedHashMap<String, Object>();
                            item.put("id", "wp:" + wp.getId());
                            item.put("rawId", Integer.valueOf(wp.getId()));
                            item.put("kind", "waypoint");
                            item.put("category", "waypoint");
                            item.put("typeId", "waypoint");
                            item.put("name", wp.getName());
                            item.put("dimension", wp.getDimension());
                            item.put("x", Integer.valueOf(wp.getX()));
                            item.put("y", Integer.valueOf(wp.getY()));
                            item.put("z", Integer.valueOf(wp.getZ()));
                            item.put("color", wp.getColor());
                            item.put("icon", wp.getIcon());
                            item.put("isPublic", Boolean.valueOf(wp.isGlobal()));
                            item.put("ownerId", wp.getOwnerUserId());
                            waypoints.add(item);
                        }
                    }
                }
            }

            res.put("players", players);
            res.put("entities", entities);
            res.put("structures", structures);
            res.put("waypoints", waypoints);
        } catch (Throwable t) {
            tripCircuitBreaker("getOverlays failed", t);
        }
        return res;
    }

    public static MapWaypoint createWaypoint(int userId, String name, String dimension,
                                             int x, int y, int z, String color, String icon, boolean isPublic) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Waypoint name is required");
        }
        MapWaypoint wp = new MapWaypoint();
        wp.setName(name.trim());
        wp.setDimension(dimension != null && !dimension.trim().isEmpty() ? dimension.trim() : "minecraft:overworld");
        wp.setX(x);
        wp.setY(y);
        wp.setZ(z);
        wp.setColor(color != null && !color.trim().isEmpty() ? color.trim() : "#3b82f6");
        wp.setIcon(icon != null && !icon.trim().isEmpty() ? icon.trim() : "pin");
        wp.setGlobal(isPublic);
        wp.setOwnerUserId(Integer.valueOf(userId));
        wp.setCreatedAt(System.currentTimeMillis() / 1000L);
        MyBatisFactory.doWork(session -> session.getMapper(MapWaypointMapper.class).insert(wp));
        return wp;
    }

    public static boolean deleteWaypoint(int waypointId, int userId, boolean isFullMapUser) {
        return MyBatisFactory.doWorkWithResult(session -> {
            MapWaypointMapper mapper = session.getMapper(MapWaypointMapper.class);
            MapWaypoint existing = mapper.findById(waypointId);
            if (existing == null) return Boolean.FALSE;
            if (!isFullMapUser && (existing.getOwnerUserId() == null || existing.getOwnerUserId().intValue() != userId)) {
                return Boolean.FALSE;
            }
            return Boolean.valueOf(mapper.deleteById(waypointId) > 0);
        }).booleanValue();
    }

    public static boolean teleportBoundPlayer(int userId, String dimension, double x, double y, double z) {
        return teleportBoundPlayer(userId, null, dimension, x, y, z);
    }

    public static boolean teleportBoundPlayer(int userId, String playerUuid, String dimension, double x, double y, double z) {
        PlayerBindingInfo binding = BindingService.findByUserAndUuid(userId, playerUuid);
        if (binding == null || binding.getPlayerName() == null) {
            return false;
        }
        GameAdapter adapter = AdapterRegistry.getNullable();
        if (adapter == null) return false;
        return adapter.teleportPlayerTo(binding.getPlayerName(), dimension, x, y, z);
    }

    public static void clearTileCache(String dimension) {
        try {
            Path dir = dimension != null && !dimension.isEmpty()
                    ? resolveCacheDimRoot(dimension)
                    : resolveMapCacheRoot();
            if (dir != null && Files.exists(dir)) {
                try (Stream<Path> walk = Files.walk(dir)) {
                    List<Path> toDelete = new ArrayList<Path>();
                    walk.forEach(toDelete::add);
                    Collections.sort(toDelete, Comparator.reverseOrder());
                    for (Path p : toDelete) {
                        if (!p.equals(dir)) Files.deleteIfExists(p);
                    }
                }
            }
            synchronized (BUFFER_LOCK) {
                REGION_BUFFERS.clear();
            }
            STRUCTURE_CACHE.clear();
        } catch (Throwable t) {
            PanelLog.get().warn("Failed to clear map cache: {}", t.getMessage());
        }
    }

    private static List<MapMarkerDto> getCachedLiveMarkers(String dimension) {
        String dimKey = dimension != null ? dimension : "minecraft:overworld";
        CachedMarkers cached = LIVE_MARKER_CACHE.get(dimKey);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.capturedAt < 2000L) {
            return cached.markers;
        }
        GameAdapter adapter = AdapterRegistry.getNullable();
        if (adapter == null) return Collections.emptyList();
        List<MapMarkerDto> fresh = adapter.collectLiveMapMarkers(dimKey);
        List<MapMarkerDto> safe = fresh != null ? fresh : Collections.emptyList();
        LIVE_MARKER_CACHE.put(dimKey, new CachedMarkers(safe, now));
        return safe;
    }

    private static boolean matchesMarkerFilter(MapMarkerDto m, String q) {
        if (q.isEmpty()) return true;
        return (m.getName() != null && m.getName().toLowerCase(Locale.ROOT).contains(q))
                || (m.getTypeId() != null && m.getTypeId().toLowerCase(Locale.ROOT).contains(q))
                || (m.getCategory() != null && m.getCategory().toLowerCase(Locale.ROOT).contains(q));
    }

    private static int getUserMapLevel(Integer userId) {
        if (userId == null) {
            if (PermissionService.guestMeetsRequirement("map:2")) return 2;
            if (PermissionService.guestMeetsRequirement("map:1")) return 1;
            return 0;
        }
        if (PermissionService.meetsRequirement(userId, "map:2")) return 2;
        if (PermissionService.meetsRequirement(userId, "map:1")) return 1;
        return 0;
    }

    private static boolean isLayerAllowed(int userLevel, String configuredMode) {
        if (userLevel <= 0) return false;
        if ("disabled".equalsIgnoreCase(configuredMode)) return false;
        if ("full".equalsIgnoreCase(configuredMode)) {
            return userLevel >= 2;
        }
        return userLevel >= 1;
    }

    private static byte[] getOrRenderBaseRegionPng(String dimension, int rx, int rz) throws IOException {
        RegionKey key = new RegionKey(dimension, rx, rz);
        synchronized (BUFFER_LOCK) {
            RegionBuffer buf = REGION_BUFFERS.get(key);
            if (buf != null && buf.rendered && buf.pngBytes != null) {
                return buf.pngBytes;
            }
        }

        Path cacheFile = resolveCacheTileFile(dimension, 0, rx, rz);
        if (cacheFile != null && Files.isRegularFile(cacheFile)) {
            return Files.readAllBytes(cacheFile);
        }

        Path mcaFile = resolveMcaFile(dimension, rx, rz);
        if (mcaFile == null || !Files.isRegularFile(mcaFile)) {
            return getEmptyTilePng();
        }

        boolean hasCeiling = isCeilingDimension(dimension);
        McaRegionReader.RegionRenderResult rendered = McaRegionReader.readRegion(mcaFile, dimension, rx, rz, hasCeiling);
        if (!rendered.structures.isEmpty()) {
            STRUCTURE_CACHE.put(key, rendered.structures);
            persistStructuresJson(dimension, rx, rz, rendered.structures);
        }
        if (rendered.renderedChunks == 0) {
            return getEmptyTilePng();
        }

        byte[] png = PurePngEncoder.encodeRgba(REGION_PX, REGION_PX, rendered.argbPixels);
        if (cacheFile != null) {
            Files.createDirectories(cacheFile.getParent());
            Files.write(cacheFile, png);
        }
        synchronized (BUFFER_LOCK) {
            RegionBuffer buf = new RegionBuffer();
            buf.shadedPixels = rendered.argbPixels;
            buf.heights = rendered.heights;
            buf.pngBytes = png;
            buf.rendered = true;
            buf.dirty = false;
            REGION_BUFFERS.put(key, buf);
        }
        return png;
    }

    private static byte[] getOrCompositeZoomTilePng(String dimension, int zoom, int tx, int tz) throws IOException {
        Path zoomFile = resolveCacheTileFile(dimension, zoom, tx, tz);
        if (zoomFile != null && Files.isRegularFile(zoomFile)) {
            return Files.readAllBytes(zoomFile);
        }
        int factor = 1 << (-zoom); // 2 at z=-1, 4 at z=-2
        int subSize = REGION_PX / factor; // 256 at z=-1, 128 at z=-2
        int[] canvas = new int[REGION_PX * REGION_PX];
        boolean anyData = false;

        for (int dz = 0; dz < factor; dz++) {
            for (int dx = 0; dx < factor; dx++) {
                int rx = tx * factor + dx;
                int rz = tz * factor + dz;
                int[] regionPixels = loadRegionPixelsForZoom(dimension, rx, rz);
                if (regionPixels == null) continue;
                anyData = true;
                int[] down = regionPixels;
                int cur = REGION_PX;
                while (cur > subSize) {
                    down = PurePngEncoder.downsampleHalf(cur, down);
                    cur /= 2;
                }
                int dstBaseX = dx * subSize;
                int dstBaseY = dz * subSize;
                for (int y = 0; y < subSize; y++) {
                    System.arraycopy(down, y * subSize, canvas, (dstBaseY + y) * REGION_PX + dstBaseX, subSize);
                }
            }
        }
        if (!anyData) return getEmptyTilePng();

        byte[] png = PurePngEncoder.encodeRgba(REGION_PX, REGION_PX, canvas);
        if (zoomFile != null) {
            Files.createDirectories(zoomFile.getParent());
            Files.write(zoomFile, png);
        }
        return png;
    }

    private static int[] loadRegionPixelsForZoom(String dimension, int rx, int rz) throws IOException {
        RegionKey key = new RegionKey(dimension, rx, rz);
        synchronized (BUFFER_LOCK) {
            RegionBuffer buf = REGION_BUFFERS.get(key);
            if (buf != null && buf.shadedPixels != null) {
                return buf.shadedPixels;
            }
        }
        Path mcaFile = resolveMcaFile(dimension, rx, rz);
        if (mcaFile == null || !Files.isRegularFile(mcaFile)) {
            return null;
        }
        McaRegionReader.RegionRenderResult rendered = McaRegionReader.readRegion(
                mcaFile, dimension, rx, rz, isCeilingDimension(dimension));
        if (rendered.renderedChunks == 0) return null;
        if (!rendered.structures.isEmpty()) {
            STRUCTURE_CACHE.put(key, rendered.structures);
            persistStructuresJson(dimension, rx, rz, rendered.structures);
        }
        byte[] png = PurePngEncoder.encodeRgba(REGION_PX, REGION_PX, rendered.argbPixels);
        Path cacheFile = resolveCacheTileFile(dimension, 0, rx, rz);
        if (cacheFile != null) {
            Files.createDirectories(cacheFile.getParent());
            Files.write(cacheFile, png);
        }
        synchronized (BUFFER_LOCK) {
            RegionBuffer buf = new RegionBuffer();
            buf.shadedPixels = rendered.argbPixels;
            buf.heights = rendered.heights;
            buf.pngBytes = png;
            buf.rendered = true;
            buf.dirty = false;
            REGION_BUFFERS.put(key, buf);
        }
        return rendered.argbPixels;
    }

    private static void applyChunkToRegionBuffer(MapChunkRenderData chunk) {
        if (chunk.getArgbPixels() == null || chunk.getArgbPixels().length < 256) return;
        int rx = Math.floorDiv(chunk.getChunkX(), 32);
        int rz = Math.floorDiv(chunk.getChunkZ(), 32);
        int localCx = Math.floorMod(chunk.getChunkX(), 32);
        int localCz = Math.floorMod(chunk.getChunkZ(), 32);
        RegionKey key = new RegionKey(chunk.getDimension(), rx, rz);

        synchronized (BUFFER_LOCK) {
            RegionBuffer buf = REGION_BUFFERS.get(key);
            if (buf == null) {
                buf = new RegionBuffer();
                buf.shadedPixels = new int[REGION_PX * REGION_PX];
                buf.heights = new short[REGION_PX * REGION_PX];
                // Preload existing MCA terrain if available so isolated updates don't wipe surrounding chunks
                try {
                    Path mca = resolveMcaFile(chunk.getDimension(), rx, rz);
                    if (mca != null && Files.isRegularFile(mca)) {
                        McaRegionReader.RegionRenderResult base = McaRegionReader.readRegion(
                                mca, chunk.getDimension(), rx, rz, chunk.isHasCeiling());
                        buf.shadedPixels = base.argbPixels;
                        buf.heights = base.heights;
                    }
                } catch (Throwable ignored) {}
                REGION_BUFFERS.put(key, buf);
            }

            int baseX = localCx * 16;
            int baseZ = localCz * 16;
            short[] cHeights = chunk.getHeights();
            int[] cPixels = chunk.getArgbPixels();
            for (int z = 0; z < 16; z++) {
                int rxZ = baseZ + z;
                int pZ = Math.max(0, rxZ - 1);
                for (int x = 0; x < 16; x++) {
                    int rxX = baseX + x;
                    int pX = Math.max(0, rxX - 1);
                    int srcIdx = z * 16 + x;
                    int dstIdx = rxZ * REGION_PX + rxX;
                    short h = (cHeights != null && cHeights.length > srcIdx) ? cHeights[srcIdx] : 64;
                    buf.heights[dstIdx] = h;
                    int hNW = buf.heights[pZ * REGION_PX + pX];
                    buf.shadedPixels[dstIdx] = McaRegionReader.shadeSinglePixel(cPixels[srcIdx], h - hNW);
                }
            }
            buf.rendered = true;
            buf.dirty = true;
            buf.pngBytes = null;
        }
    }

    private static void flushDirtyBuffers() {
        List<Map.Entry<RegionKey, RegionBuffer>> snapshot;
        synchronized (BUFFER_LOCK) {
            snapshot = new ArrayList<Map.Entry<RegionKey, RegionBuffer>>(REGION_BUFFERS.entrySet());
        }
        for (Map.Entry<RegionKey, RegionBuffer> e : snapshot) {
            if (e.getValue().dirty) {
                flushRegionSafe(e.getKey(), e.getValue());
            }
        }
    }

    private static void flushRegionSafe(RegionKey key, RegionBuffer buf) {
        if (buf == null || !buf.dirty || buf.shadedPixels == null) return;
        try {
            byte[] png = PurePngEncoder.encodeRgba(REGION_PX, REGION_PX, buf.shadedPixels);
            buf.pngBytes = png;
            buf.dirty = false;
            Path tileFile = resolveCacheTileFile(key.dimension, 0, key.rx, key.rz);
            if (tileFile != null) {
                Files.createDirectories(tileFile.getParent());
                Files.write(tileFile, png);
            }
            // Invalidate parent zoom-out composite PNGs covering this region
            for (int z = -1; z >= -2; z--) {
                int factor = 1 << (-z);
                int tx = Math.floorDiv(key.rx, factor);
                int tz = Math.floorDiv(key.rz, factor);
                Path zoomFile = resolveCacheTileFile(key.dimension, z, tx, tz);
                if (zoomFile != null) Files.deleteIfExists(zoomFile);
            }
        } catch (Throwable t) {
            tripCircuitBreaker("Failed writing region cache tile", t);
        }
    }

    private static Path resolveMapCacheRoot() {
        GameAdapter adapter = AdapterRegistry.getNullable();
        if (adapter == null || adapter.getWorldDirectory() == null) return null;
        return adapter.getWorldDirectory().resolve("utopia_server_panel").resolve("map_cache");
    }

    private static Path resolveCacheDimRoot(String dimension) {
        Path root = resolveMapCacheRoot();
        if (root == null) return null;
        return root.resolve(sanitizeDimensionSlug(dimension));
    }

    private static Path resolveCacheDimDir(String dimension, int zoom) {
        Path dimRoot = resolveCacheDimRoot(dimension);
        if (dimRoot == null) return null;
        return dimRoot.resolve("z" + zoom);
    }

    private static Path resolveCacheTileFile(String dimension, int zoom, int x, int z) {
        Path dir = resolveCacheDimDir(dimension, zoom);
        if (dir == null) return null;
        return dir.resolve("r." + x + "." + z + ".png");
    }

    private static void persistStructuresJson(String dimension, int rx, int rz, List<MapMarkerDto> list) {
        try {
            Path dir = resolveCacheDimDir(dimension, 0);
            if (dir == null) return;
            Path jsonFile = dir.resolve("r." + rx + "." + rz + ".structs.json");
            Files.createDirectories(jsonFile.getParent());
            Files.write(jsonFile, GSON.toJson(list).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {}
    }

    private static void loadCachedStructuresFromDisk(String dimension, List<MapMarkerDto> out,
                                                     Set<String> seenIds, String query) {
        try {
            Path dir = resolveCacheDimDir(dimension != null ? dimension : "minecraft:overworld", 0);
            if (dir == null || !Files.isDirectory(dir)) return;
            try (Stream<Path> files = Files.list(dir)) {
                files.filter(p -> p.getFileName().toString().endsWith(".structs.json"))
                        .forEach(p -> {
                            try {
                                String raw = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                                MapMarkerDto[] arr = GSON.fromJson(raw, MapMarkerDto[].class);
                                if (arr != null) {
                                    for (MapMarkerDto m : arr) {
                                        if (m != null && matchesMarkerFilter(m, query) && seenIds.add(m.getId())) {
                                            out.add(m);
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        });
            }
        } catch (Exception ignored) {}
    }

    private static Path resolveMcaFile(String dimension, int rx, int rz) {
        Path regDir = resolveRegionDirectory(dimension);
        if (regDir == null) return null;
        return regDir.resolve("r." + rx + "." + rz + ".mca");
    }

    private static Path resolveRegionDirectory(String dimension) {
        GameAdapter adapter = AdapterRegistry.getNullable();
        if (adapter == null || adapter.getWorldDirectory() == null) return null;
        Path world = adapter.getWorldDirectory();
        String dim = (dimension == null || dimension.trim().isEmpty()) ? "minecraft:overworld" : dimension.trim();

        if ("minecraft:overworld".equals(dim) || "overworld".equals(dim) || "0".equals(dim)) {
            return world.resolve("region");
        }
        if ("minecraft:the_nether".equals(dim) || "the_nether".equals(dim) || "-1".equals(dim)) {
            return world.resolve("DIM-1").resolve("region");
        }
        if ("minecraft:the_end".equals(dim) || "the_end".equals(dim) || "1".equals(dim)) {
            return world.resolve("DIM1").resolve("region");
        }
        if (dim.startsWith("dimension:")) {
            return world.resolve("DIM" + dim.substring("dimension:".length())).resolve("region");
        }
        int colon = dim.indexOf(':');
        if (colon > 0) {
            String ns = dim.substring(0, colon);
            String path = dim.substring(colon + 1);
            Path modern = world.resolve("dimensions").resolve(ns).resolve(path).resolve("region");
            if (Files.isDirectory(modern)) return modern;
        }
        return world.resolve("region");
    }

    private static boolean isCeilingDimension(String dimension) {
        if (dimension == null) return false;
        if (dimension.contains("nether")) return true;
        for (MapDimensionInfo d : listDimensions()) {
            if (dimension.equals(d.getId())) return d.isHasCeiling();
        }
        return false;
    }

    private static String sanitizeDimensionSlug(String dimension) {
        String d = (dimension == null || dimension.isEmpty()) ? "minecraft_overworld" : dimension;
        return d.replace(':', '_').replace('/', '_').replace('\\', '_');
    }

    private static byte[] getEmptyTilePng() {
        if (emptyTilePng == null) {
            emptyTilePng = PurePngEncoder.encodeRgba(1, 1, new int[]{0});
        }
        return emptyTilePng;
    }

    private static void tripCircuitBreaker(String context, Throwable t) {
        mapHealthy = false;
        lastMapError = context + ": " + (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName());
        PanelLog.get().error("[Map Isolation] Disabled web map after failure (" + context + ") - server gameplay unaffected", t);
    }

    private static final class ChunkKey {
        final String dimension;
        final int chunkX;
        final int chunkZ;

        ChunkKey(String dimension, int chunkX, int chunkZ) {
            this.dimension = dimension;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkKey)) return false;
            ChunkKey other = (ChunkKey) o;
            return chunkX == other.chunkX && chunkZ == other.chunkZ && dimension.equals(other.dimension);
        }

        @Override
        public int hashCode() {
            int r = dimension.hashCode();
            r = 31 * r + chunkX;
            r = 31 * r + chunkZ;
            return r;
        }
    }

    private static final class RegionKey {
        final String dimension;
        final int rx;
        final int rz;

        RegionKey(String dimension, int rx, int rz) {
            this.dimension = dimension != null ? dimension : "minecraft:overworld";
            this.rx = rx;
            this.rz = rz;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RegionKey)) return false;
            RegionKey other = (RegionKey) o;
            return rx == other.rx && rz == other.rz && dimension.equals(other.dimension);
        }

        @Override
        public int hashCode() {
            int r = dimension.hashCode();
            r = 31 * r + rx;
            r = 31 * r + rz;
            return r;
        }
    }

    private static final class RegionBuffer {
        int[] shadedPixels;
        short[] heights;
        byte[] pngBytes;
        boolean rendered;
        boolean dirty;
    }

    private static final class CachedMarkers {
        final List<MapMarkerDto> markers;
        final long capturedAt;

        CachedMarkers(List<MapMarkerDto> markers, long capturedAt) {
            this.markers = markers;
            this.capturedAt = capturedAt;
        }
    }
}
