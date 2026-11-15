package com.utopiaxc.utopiaserverpanel.platform.shared;

import com.utopiaxc.utopiaserverpanel.spi.map.MapChunkRenderData;
import com.utopiaxc.utopiaserverpanel.spi.map.MapDimensionInfo;
import com.utopiaxc.utopiaserverpanel.spi.map.MapMarkerDto;
import net.minecraft.core.BlockPos;
//? if mc >= 1.19.3 {
import net.minecraft.core.registries.BuiltInRegistries;
//?} else {
import net.minecraft.core.Registry;
//?}
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Version-agnostic map capture &amp; dimension/entity marker helper for Minecraft 1.16.5 through 26.3+.
 * Every public method is circuit-breaker safe and never accesses private internals.
 */
public final class ModernMapBridge {
    private static final int MAX_LIVE_ENTITY_MARKERS = 400;

    private ModernMapBridge() {}

    public static String dimensionId(ServerLevel level) {
        //? if mc >= 1.21.11 {
        return level.dimension().identifier().toString();
        //?} else {
        return level.dimension().location().toString();
        //?}
    }

    public static int getMinY(ServerLevel level) {
        //? if mc >= 1.21.2 {
        return level.getMinY();
        //?} else if mc >= 1.18 {
        return level.getMinBuildHeight();
        //?} else {
        return 0;
        //?}
    }

    public static int getMaxY(ServerLevel level) {
        //? if mc >= 1.21.2 {
        return level.getMaxY();
        //?} else if mc >= 1.18 {
        return level.getMaxBuildHeight();
        //?} else {
        return 256;
        //?}
    }

    public static ServerLevel findLevel(MinecraftServer server, String dimId) {
        if (server == null) return null;
        if (dimId == null || dimId.isEmpty()) return server.overworld();
        for (ServerLevel level : server.getAllLevels()) {
            if (dimId.equals(dimensionId(level))) {
                return level;
            }
        }
        return null;
    }

    public static List<MapDimensionInfo> listDimensions(MinecraftServer server) {
        if (server == null) return Collections.emptyList();
        List<MapDimensionInfo> list = new ArrayList<MapDimensionInfo>();
        try {
            for (ServerLevel level : server.getAllLevels()) {
                String id = dimensionId(level);
                boolean ceiling = level.dimensionType().hasCeiling();
                double scale = ceiling ? 8.0 : 1.0;
                list.add(new MapDimensionInfo(id, prettyDimName(id), ceiling, getMinY(level), getMaxY(level), scale));
            }
        } catch (Throwable ignored) {}
        return list;
    }

    public static MapChunkRenderData captureLiveChunk(MinecraftServer server, String dimId, int chunkX, int chunkZ) {
        if (server == null) return null;
        try {
            ServerLevel level = findLevel(server, dimId);
            if (level == null) return null;
            LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (chunk == null) return null;

            boolean hasCeiling = level.dimensionType().hasCeiling();
            int minY = getMinY(level);
            int maxY = getMaxY(level);
            int[] argb = new int[256];
            short[] heights = new short[256];
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int baseX = chunkX << 4;
            int baseZ = chunkZ << 4;

            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int startY = hasCeiling
                            ? Math.min(120, maxY - 2)
                            : Math.min(maxY - 1, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
                    if (startY < minY) startY = minY;
                    int floorY = Math.max(minY, startY - 24);
                    for (int y = startY; y >= floorY; y--) {
                        pos.set(baseX + x, y, baseZ + z);
                        BlockState state = chunk.getBlockState(pos);
                        if (state.isAir()) continue;
                        int rgb = state.getMapColor(level, pos).col;
                        if (rgb == 0 && !hasCeiling) continue;
                        int idx = (z << 4) | x;
                        argb[idx] = 0xFF000000 | (rgb & 0xFFFFFF);
                        heights[idx] = (short) y;
                        break;
                    }
                }
            }
            return new MapChunkRenderData(dimensionId(level), chunkX, chunkZ, argb, heights, hasCeiling);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static List<MapMarkerDto> collectLiveMarkers(MinecraftServer server, String dimId) {
        if (server == null) return Collections.emptyList();
        try {
            return CompletableFuture.supplyAsync(() -> collectOnServerThread(server, dimId), server)
                    .get(1200L, TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {
            return Collections.emptyList();
        }
    }

    private static List<MapMarkerDto> collectOnServerThread(MinecraftServer server, String dimId) {
        ServerLevel level = findLevel(server, dimId);
        if (level == null) return Collections.emptyList();
        String actualDim = dimensionId(level);
        List<MapMarkerDto> markers = new ArrayList<MapMarkerDto>();
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Player) continue;
            if (count >= MAX_LIVE_ENTITY_MARKERS) break;
            String typeId = entityTypeId(entity);
            String category = classifyEntity(entity);
            String name = entity.getDisplayName() != null ? entity.getDisplayName().getString() : typeId;
            markers.add(new MapMarkerDto(
                    "ent:" + entity.getStringUUID(),
                    "entity",
                    category,
                    name,
                    typeId,
                    actualDim,
                    entity.getX(),
                    entity.getY(),
                    entity.getZ()
            ));
            count++;
        }
        return markers;
    }

    public static String entityTypeId(Entity entity) {
        try {
            //? if mc >= 1.19.3 {
            Object key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            //?} else {
            Object key = Registry.ENTITY_TYPE.getKey(entity.getType());
            //?}
            return key != null ? key.toString() : "minecraft:unknown";
        } catch (Throwable t) {
            return "minecraft:unknown";
        }
    }

    private static String classifyEntity(Entity e) {
        if (e instanceof Enemy) return "hostile";
        if (e instanceof Animal) return "passive";
        return "other";
    }

    private static String prettyDimName(String id) {
        if ("minecraft:overworld".equals(id)) return "Overworld";
        if ("minecraft:the_nether".equals(id)) return "The Nether";
        if ("minecraft:the_end".equals(id)) return "The End";
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }
}
