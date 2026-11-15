package com.utopiaxc.utopiaserverpanel.legacy1122;

import com.utopiaxc.utopiaserverpanel.spi.map.MapChunkRenderData;
import com.utopiaxc.utopiaserverpanel.spi.map.MapDimensionInfo;
import com.utopiaxc.utopiaserverpanel.spi.map.MapMarkerDto;
import com.utopiaxc.utopiaserverpanel.spi.mod.ModEntryDto;
import com.utopiaxc.utopiaserverpanel.spi.profiler.ChunkHotspotDto;
import com.utopiaxc.utopiaserverpanel.spi.profiler.DimensionHotspotDto;
import com.utopiaxc.utopiaserverpanel.spi.profiler.ProfilerHotspotSnapshot;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Isolated map, profiler, mod-scanner, and chat/teleport helpers for Forge 1.12.2.
 */
final class Forge1122FeatureHelper {

    private Forge1122FeatureHelper() {}

    static List<MapDimensionInfo> getMapDimensions(MinecraftServer server) {
        if (server == null || server.worlds == null) return Collections.emptyList();
        List<MapDimensionInfo> dims = new ArrayList<MapDimensionInfo>();
        for (WorldServer ws : server.worlds) {
            if (ws == null || ws.provider == null) continue;
            int dim = ws.provider.getDimension();
            boolean ceiling = ws.provider.isNether();
            String id = Forge1122GameAdapter.dimId(dim);
            String name = ws.provider.getDimensionType() != null ? ws.provider.getDimensionType().getName() : id;
            dims.add(new MapDimensionInfo(id, name, ceiling, 0, ws.getHeight(), ceiling ? 8.0 : 1.0));
        }
        return dims;
    }

    static WorldServer findWorld(MinecraftServer server, String dimId) {
        if (server == null || server.worlds == null) return null;
        for (WorldServer ws : server.worlds) {
            if (ws != null && ws.provider != null && dimId.equals(Forge1122GameAdapter.dimId(ws.provider.getDimension()))) {
                return ws;
            }
        }
        return server.worlds.length > 0 ? server.worlds[0] : null;
    }

    static MapChunkRenderData captureLiveChunk(MinecraftServer server, String dimId, int cx, int cz) {
        try {
            WorldServer ws = findWorld(server, dimId);
            if (ws == null || !ws.getChunkProvider().chunkExists(cx, cz)) return null;
            Chunk chunk = ws.getChunk(cx, cz);
            boolean ceiling = ws.provider.isNether();
            int[] argb = new int[256];
            short[] heights = new short[256];
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int baseX = cx << 4;
            int baseZ = cz << 4;

            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int startY = ceiling ? 120 : Math.min(ws.getHeight() - 1, chunk.getHeightValue(x, z));
                    int floorY = Math.max(0, startY - 24);
                    for (int y = startY; y >= floorY; y--) {
                        pos.setPos(baseX + x, y, baseZ + z);
                        IBlockState state = chunk.getBlockState(pos);
                        if (state.getBlock().isAir(state, ws, pos)) continue;
                        int rgb = state.getMapColor(ws, pos).colorValue;
                        if (rgb == 0 && !ceiling) continue;
                        int idx = (z << 4) | x;
                        argb[idx] = 0xFF000000 | (rgb & 0xFFFFFF);
                        heights[idx] = (short) y;
                        break;
                    }
                }
            }
            return new MapChunkRenderData(dimId, cx, cz, argb, heights, ceiling);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static List<MapMarkerDto> collectLiveMarkers(MinecraftServer server, String dimId) {
        WorldServer ws = findWorld(server, dimId);
        if (ws == null) return Collections.emptyList();
        String actualDim = Forge1122GameAdapter.dimId(ws.provider.getDimension());
        List<MapMarkerDto> list = new ArrayList<MapMarkerDto>();
        int count = 0;
        for (Entity e : new ArrayList<Entity>(ws.loadedEntityList)) {
            if (e instanceof EntityPlayer) continue;
            if (count >= 400) break;
            ResourceLocation rl = EntityList.getKey(e);
            String typeId = rl != null ? rl.toString() : e.getClass().getSimpleName();
            String cat = (e instanceof IMob) ? "hostile" : ((e instanceof EntityAnimal) ? "passive" : "other");
            list.add(new MapMarkerDto("ent:" + e.getUniqueID().toString(), "entity", cat, e.getName(), typeId,
                    actualDim, e.posX, e.posY, e.posZ));
            count++;
        }
        return list;
    }

    static ProfilerHotspotSnapshot collectHotspots(MinecraftServer server) {
        ProfilerHotspotSnapshot snap = new ProfilerHotspotSnapshot();
        if (server == null || server.worlds == null) return snap;
        Map<String, Integer> globalEntities = new HashMap<String, Integer>();
        Map<String, Integer> globalBes = new HashMap<String, Integer>();
        List<Bucket> entChunks = new ArrayList<Bucket>();
        List<Bucket> beChunks = new ArrayList<Bucket>();

        for (WorldServer ws : server.worlds) {
            if (ws == null || ws.provider == null) continue;
            String dim = Forge1122GameAdapter.dimId(ws.provider.getDimension());
            Map<Long, Bucket> localEnt = new HashMap<Long, Bucket>();
            Map<Long, Bucket> localBe = new HashMap<Long, Bucket>();

            List<Entity> ents = new ArrayList<Entity>(ws.loadedEntityList);
            for (Entity e : ents) {
                ResourceLocation rl = EntityList.getKey(e);
                String id = rl != null ? rl.toString() : e.getClass().getSimpleName();
                inc(globalEntities, id);
                int cx = ((int) Math.floor(e.posX)) >> 4;
                int cz = ((int) Math.floor(e.posZ)) >> 4;
                long key = (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
                Bucket b = localEnt.get(key);
                if (b == null) {
                    b = new Bucket(dim, cx, cz);
                    localEnt.put(key, b);
                }
                b.count++;
                inc(b.types, id);
            }
            entChunks.addAll(localEnt.values());

            List<TileEntity> tiles = new ArrayList<TileEntity>(ws.loadedTileEntityList);
            for (TileEntity te : tiles) {
                ResourceLocation rl = TileEntity.getKey(te.getClass());
                String id = rl != null ? rl.toString() : te.getClass().getSimpleName();
                inc(globalBes, id);
                int cx = te.getPos().getX() >> 4;
                int cz = te.getPos().getZ() >> 4;
                long key = (((long) cx) << 32) | (cz & 0xFFFFFFFFL);
                Bucket b = localBe.get(key);
                if (b == null) {
                    b = new Bucket(dim, cx, cz);
                    localBe.put(key, b);
                }
                b.count++;
                inc(b.types, id);
            }
            beChunks.addAll(localBe.values());

            snap.getDimensions().add(new DimensionHotspotDto(
                    dim, ws.getChunkProvider().getLoadedChunkCount(), ents.size(), tiles.size(), ws.playerEntities.size()));
        }

        Collections.sort(entChunks, (a, b) -> Integer.compare(b.count, a.count));
        for (int i = 0; i < Math.min(15, entChunks.size()); i++) {
            Bucket b = entChunks.get(i);
            snap.getTopEntityChunks().add(new ChunkHotspotDto(b.dim, b.cx, b.cz, b.count, 0, topStr(b.types)));
        }

        Collections.sort(beChunks, (a, b) -> Integer.compare(b.count, a.count));
        for (int i = 0; i < Math.min(15, beChunks.size()); i++) {
            Bucket b = beChunks.get(i);
            snap.getTopBlockEntityChunks().add(new ChunkHotspotDto(b.dim, b.cx, b.cz, 0, b.count, topStr(b.types)));
        }

        fillSorted(globalEntities, snap.getTopEntityTypes());
        fillSorted(globalBes, snap.getTopBlockEntityTypes());
        return snap;
    }

    static List<ModEntryDto> getLoadedMods() {
        List<ModEntryDto> list = new ArrayList<ModEntryDto>();
        try {
            for (ModContainer mc : Loader.instance().getModList()) {
                String authors = "";
                String desc = "";
                String url = "";
                if (mc.getMetadata() != null) {
                    if (mc.getMetadata().authorList != null) {
                        authors = String.join(", ", mc.getMetadata().authorList);
                    }
                    desc = mc.getMetadata().description != null ? mc.getMetadata().description : "";
                    url = mc.getMetadata().url != null ? mc.getMetadata().url : "";
                }
                list.add(new ModEntryDto(mc.getModId(), mc.getName(), mc.getDisplayVersion(), desc, authors, url, null));
            }
        } catch (Throwable ignored) {}
        return list;
    }

    static void broadcastChat(MinecraftServer server, String sender, String message) {
        if (server == null || server.getPlayerList() == null) return;
        server.getPlayerList().sendMessage(new TextComponentString("[Web] <" + sender + "> " + message));
    }

    static void sendDirect(MinecraftServer server, String target, String message) {
        if (server == null || server.getPlayerList() == null) return;
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            if (target.equalsIgnoreCase(p.getName()) || target.equalsIgnoreCase(p.getUniqueID().toString())) {
                p.sendMessage(new TextComponentString(message));
                break;
            }
        }
    }

    static boolean teleport(MinecraftServer server, String target, double x, double y, double z) {
        if (server == null || server.getPlayerList() == null) return false;
        for (EntityPlayerMP p : server.getPlayerList().getPlayers()) {
            if (target.equalsIgnoreCase(p.getName()) || target.equalsIgnoreCase(p.getUniqueID().toString())) {
                p.setPositionAndUpdate(x, y, z);
                return true;
            }
        }
        return false;
    }

    private static void inc(Map<String, Integer> map, String key) {
        Integer cur = map.get(key);
        map.put(key, Integer.valueOf(cur != null ? cur.intValue() + 1 : 1));
    }

    private static void fillSorted(Map<String, Integer> map, List<ProfilerHotspotSnapshot.TypeCountDto> target) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<Map.Entry<String, Integer>>(map.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return Integer.compare(b.getValue().intValue(), a.getValue().intValue());
            }
        });
        for (int i = 0; i < Math.min(15, entries.size()); i++) {
            target.add(new ProfilerHotspotSnapshot.TypeCountDto(entries.get(i).getKey(), entries.get(i).getValue().intValue()));
        }
    }

    private static String topStr(Map<String, Integer> map) {
        List<Map.Entry<String, Integer>> list = new ArrayList<Map.Entry<String, Integer>>(map.entrySet());
        Collections.sort(list, (a, b) -> Integer.compare(b.getValue().intValue(), a.getValue().intValue()));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(3, list.size()); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i).getKey()).append(" x").append(list.get(i).getValue());
        }
        return sb.toString();
    }

    private static final class Bucket {
        final String dim;
        final int cx;
        final int cz;
        int count;
        final Map<String, Integer> types = new HashMap<String, Integer>();

        Bucket(String dim, int cx, int cz) {
            this.dim = dim;
            this.cx = cx;
            this.cz = cz;
        }
    }
}
