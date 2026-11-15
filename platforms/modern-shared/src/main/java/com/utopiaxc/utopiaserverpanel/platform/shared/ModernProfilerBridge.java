package com.utopiaxc.utopiaserverpanel.platform.shared;

import com.utopiaxc.utopiaserverpanel.spi.profiler.ChunkHotspotDto;
import com.utopiaxc.utopiaserverpanel.spi.profiler.DimensionHotspotDto;
import com.utopiaxc.utopiaserverpanel.spi.profiler.ProfilerHotspotSnapshot;
//? if mc >= 1.19.3 {
import net.minecraft.core.registries.BuiltInRegistries;
//?} else {
import net.minecraft.core.Registry;
//?}
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe entity/block-entity/chunk hotspot collector for Minecraft 1.16.5 through 26.3+.
 */
public final class ModernProfilerBridge {

    private ModernProfilerBridge() {}

    public static ProfilerHotspotSnapshot collectHotspots(MinecraftServer server) {
        if (server == null) return new ProfilerHotspotSnapshot();
        try {
            return CompletableFuture.supplyAsync(() -> sampleOnServerThread(server), server)
                    .get(1500L, TimeUnit.MILLISECONDS);
        } catch (Throwable ignored) {
            return new ProfilerHotspotSnapshot();
        }
    }

    private static ProfilerHotspotSnapshot sampleOnServerThread(MinecraftServer server) {
        ProfilerHotspotSnapshot snap = new ProfilerHotspotSnapshot();
        Map<String, Integer> globalEntityTypes = new HashMap<String, Integer>();
        Map<String, Integer> globalBeTypes = new HashMap<String, Integer>();
        List<ChunkBucket> entityChunkBuckets = new ArrayList<ChunkBucket>();
        List<ChunkBucket> beChunkBuckets = new ArrayList<ChunkBucket>();

        int scanRadius = Math.max(2, Math.min(6, server.getPlayerList().getViewDistance()));

        for (ServerLevel level : server.getAllLevels()) {
            String dimId = ModernMapBridge.dimensionId(level);
            int entityCount = 0;
            Map<Long, ChunkBucket> localEntityChunks = new HashMap<Long, ChunkBucket>();

            for (Entity entity : level.getAllEntities()) {
                entityCount++;
                String typeId = ModernMapBridge.entityTypeId(entity);
                incMap(globalEntityTypes, typeId);

                int cx = ((int) Math.floor(entity.getX())) >> 4;
                int cz = ((int) Math.floor(entity.getZ())) >> 4;
                long key = packChunk(cx, cz);
                ChunkBucket bucket = localEntityChunks.get(key);
                if (bucket == null) {
                    bucket = new ChunkBucket(dimId, cx, cz);
                    localEntityChunks.put(key, bucket);
                }
                bucket.entities++;
                incMap(bucket.entityTypes, typeId);
            }
            entityChunkBuckets.addAll(localEntityChunks.values());

            // Collect block entities across player-loaded & force-loaded chunks safely via public ChunkSource
            Set<Long> inspectedChunks = new HashSet<Long>();
            for (ServerPlayer player : level.players()) {
                int pcx = ((int) Math.floor(player.getX())) >> 4;
                int pcz = ((int) Math.floor(player.getZ())) >> 4;
                for (int dx = -scanRadius; dx <= scanRadius; dx++) {
                    for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                        inspectedChunks.add(Long.valueOf(packChunk(pcx + dx, pcz + dz)));
                    }
                }
            }
            //? if mc >= 26.1 {
            for (long forced : level.getForceLoadedChunks()) {
            //?} else {
            for (long forced : level.getForcedChunks()) {
            //?}
                inspectedChunks.add(Long.valueOf(forced));
            }

            int beTotal = 0;
            for (Long key : inspectedChunks) {
                int cx = ChunkPos.getX(key.longValue());
                int cz = ChunkPos.getZ(key.longValue());
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null || chunk.getBlockEntities().isEmpty()) continue;

                ChunkBucket bucket = new ChunkBucket(dimId, cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    beTotal++;
                    bucket.blockEntities++;
                    String beId = blockEntityTypeId(be);
                    incMap(globalBeTypes, beId);
                    incMap(bucket.beTypes, beId);
                }
                beChunkBuckets.add(bucket);
            }

            int loadedChunks = level.getChunkSource().getLoadedChunksCount();
            snap.getDimensions().add(new DimensionHotspotDto(
                    dimId, loadedChunks, entityCount, beTotal, level.players().size()
            ));
        }

        Collections.sort(entityChunkBuckets, (a, b) -> Integer.compare(b.entities, a.entities));
        for (int i = 0; i < Math.min(15, entityChunkBuckets.size()); i++) {
            ChunkBucket b = entityChunkBuckets.get(i);
            if (b.entities <= 0) break;
            snap.getTopEntityChunks().add(new ChunkHotspotDto(
                    b.dim, b.cx, b.cz, b.entities, b.blockEntities, topSummary(b.entityTypes)));
        }

        Collections.sort(beChunkBuckets, (a, b) -> Integer.compare(b.blockEntities, a.blockEntities));
        for (int i = 0; i < Math.min(15, beChunkBuckets.size()); i++) {
            ChunkBucket b = beChunkBuckets.get(i);
            if (b.blockEntities <= 0) break;
            snap.getTopBlockEntityChunks().add(new ChunkHotspotDto(
                    b.dim, b.cx, b.cz, b.entities, b.blockEntities, topSummary(b.beTypes)));
        }

        fillSortedTypes(globalEntityTypes, snap.getTopEntityTypes(), 15);
        fillSortedTypes(globalBeTypes, snap.getTopBlockEntityTypes(), 15);
        return snap;
    }

    private static String blockEntityTypeId(BlockEntity be) {
        try {
            //? if mc >= 1.19.3 {
            Object key = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType());
            //?} else {
            Object key = Registry.BLOCK_ENTITY_TYPE.getKey(be.getType());
            //?}
            return key != null ? key.toString() : be.getClass().getSimpleName();
        } catch (Throwable t) {
            return be.getClass().getSimpleName();
        }
    }

    private static long packChunk(int cx, int cz) {
        return ((long) cx & 0xffffffffL) | (((long) cz & 0xffffffffL) << 32);
    }

    private static void incMap(Map<String, Integer> map, String key) {
        Integer cur = map.get(key);
        map.put(key, Integer.valueOf(cur != null ? cur.intValue() + 1 : 1));
    }

    private static void fillSortedTypes(Map<String, Integer> counts,
                                        List<ProfilerHotspotSnapshot.TypeCountDto> target, int max) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(counts.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return Integer.compare(b.getValue().intValue(), a.getValue().intValue());
            }
        });
        for (int i = 0; i < Math.min(max, entries.size()); i++) {
            target.add(new ProfilerHotspotSnapshot.TypeCountDto(
                    entries.get(i).getKey(), entries.get(i).getValue().intValue()));
        }
    }

    private static String topSummary(Map<String, Integer> map) {
        if (map.isEmpty()) return "";
        List<Map.Entry<String, Integer>> list = new ArrayList<Map.Entry<String, Integer>>(map.entrySet());
        Collections.sort(list, (a, b) -> Integer.compare(b.getValue().intValue(), a.getValue().intValue()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, list.size()); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i).getKey()).append(" x").append(list.get(i).getValue());
        }
        return sb.toString();
    }

    private static final class ChunkBucket {
        final String dim;
        final int cx;
        final int cz;
        int entities;
        int blockEntities;
        final Map<String, Integer> entityTypes = new HashMap<String, Integer>();
        final Map<String, Integer> beTypes = new HashMap<String, Integer>();

        ChunkBucket(String dim, int cx, int cz) {
            this.dim = dim;
            this.cx = cx;
            this.cz = cz;
        }
    }
}
