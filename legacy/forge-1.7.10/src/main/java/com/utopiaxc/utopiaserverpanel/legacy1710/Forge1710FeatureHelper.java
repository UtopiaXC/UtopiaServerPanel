package com.utopiaxc.utopiaserverpanel.legacy1710;

import com.utopiaxc.utopiaserverpanel.spi.map.MapChunkRenderData;
import com.utopiaxc.utopiaserverpanel.spi.map.MapDimensionInfo;
import com.utopiaxc.utopiaserverpanel.spi.map.MapMarkerDto;
import com.utopiaxc.utopiaserverpanel.spi.mod.ModEntryDto;
import com.utopiaxc.utopiaserverpanel.spi.profiler.ChunkHotspotDto;
import com.utopiaxc.utopiaserverpanel.spi.profiler.DimensionHotspotDto;
import com.utopiaxc.utopiaserverpanel.spi.profiler.ProfilerHotspotSnapshot;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Isolated map, profiler, mod-scanner, and chat/teleport helpers for Forge 1.7.10.
 */
final class Forge1710FeatureHelper {

    private Forge1710FeatureHelper() {}

    static List<MapDimensionInfo> getMapDimensions(MinecraftServer server) {
        if (server == null || server.worldServers == null) return Collections.emptyList();
        List<MapDimensionInfo> dims = new ArrayList<MapDimensionInfo>();
        for (WorldServer ws : server.worldServers) {
            if (ws == null || ws.provider == null) continue;
            int dim = ws.provider.dimensionId;
            boolean ceiling = ws.provider.isHellWorld;
            String id = Forge1710GameAdapter.dimId(dim);
            String name = ws.provider.getDimensionName() != null ? ws.provider.getDimensionName() : id;
            dims.add(new MapDimensionInfo(id, name, ceiling, 0, ws.getHeight(), ceiling ? 8.0 : 1.0));
        }
        return dims;
    }

    static WorldServer findWorld(MinecraftServer server, String dimId) {
        if (server == null || server.worldServers == null) return null;
        for (WorldServer ws : server.worldServers) {
            if (ws != null && ws.provider != null && dimId.equals(Forge1710GameAdapter.dimId(ws.provider.dimensionId))) {
                return ws;
            }
        }
        return server.worldServers.length > 0 ? server.worldServers[0] : null;
    }

    static MapChunkRenderData captureLiveChunk(MinecraftServer server, String dimId, int cx, int cz) {
        try {
            WorldServer ws = findWorld(server, dimId);
            if (ws == null || !ws.getChunkProvider().chunkExists(cx, cz)) return null;
            Chunk chunk = ws.getChunkFromChunkCoords(cx, cz);
            boolean ceiling = ws.provider.isHellWorld;
            int[] argb = new int[256];
            short[] heights = new short[256];

            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int startY = ceiling ? 120 : Math.min(255, chunk.getHeightValue(x, z));
                    int floorY = Math.max(0, startY - 24);
                    for (int y = startY; y >= floorY; y--) {
                        Block b = chunk.getBlock(x, y, z);
                        if (b == null || b == Blocks.air) continue;
                        int meta = chunk.getBlockMetadata(x, y, z);
                        int rgb = b.getMapColor(meta).colorValue;
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
        String actualDim = Forge1710GameAdapter.dimId(ws.provider.dimensionId);
        List<MapMarkerDto> list = new ArrayList<MapMarkerDto>();
        int count = 0;
        for (Object obj : new ArrayList<Object>(ws.loadedEntityList)) {
            if (!(obj instanceof Entity) || obj instanceof EntityPlayer) continue;
            Entity e = (Entity) obj;
            if (count >= 400) break;
            String typeId = EntityList.getEntityString(e);
            if (typeId == null) typeId = e.getClass().getSimpleName();
            String cat = (e instanceof IMob) ? "hostile" : ((e instanceof EntityAnimal) ? "passive" : "other");
            list.add(new MapMarkerDto("ent:" + e.getUniqueID().toString(), "entity", cat,
                    e.getCommandSenderName(), typeId, actualDim, e.posX, e.posY, e.posZ));
            count++;
        }
        return list;
    }

    static ProfilerHotspotSnapshot collectHotspots(MinecraftServer server) {
        ProfilerHotspotSnapshot snap = new ProfilerHotspotSnapshot();
        if (server == null || server.worldServers == null) return snap;
        Map<String, Integer> globalEntities = new HashMap<String, Integer>();
        Map<String, Integer> globalBes = new HashMap<String, Integer>();
        List<Bucket> entChunks = new ArrayList<Bucket>();
        List<Bucket> beChunks = new ArrayList<Bucket>();

        for (WorldServer ws : server.worldServers) {
            if (ws == null || ws.provider == null) continue;
            String dim = Forge1710GameAdapter.dimId(ws.provider.dimensionId);
            Map<Long, Bucket> localEnt = new HashMap<Long, Bucket>();
            Map<Long, Bucket> localBe = new HashMap<Long, Bucket>();

            List<Object> ents = new ArrayList<Object>(ws.loadedEntityList);
            for (Object obj : ents) {
                if (!(obj instanceof Entity)) continue;
                Entity e = (Entity) obj;
                String id = EntityList.getEntityString(e);
                if (id == null) id = e.getClass().getSimpleName();
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

            List<Object> tiles = new ArrayList<Object>(ws.loadedTileEntityList);
            for (Object obj : tiles) {
                if (!(obj instanceof TileEntity)) continue;
                TileEntity te = (TileEntity) obj;
                String id = te.getClass().getSimpleName();
                inc(globalBes, id);
                int cx = te.xCoord >> 4;
                int cz = te.zCoord >> 4;
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
        if (server == null || server.getConfigurationManager() == null) return;
        server.getConfigurationManager().sendChatMsg(new ChatComponentText("[Web] <" + sender + "> " + message));
    }

    @SuppressWarnings("unchecked")
    static void sendDirect(MinecraftServer server, String target, String message) {
        if (server == null || server.getConfigurationManager() == null) return;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayerMP) {
                EntityPlayerMP p = (EntityPlayerMP) obj;
                if (target.equalsIgnoreCase(p.getCommandSenderName()) || target.equalsIgnoreCase(p.getUniqueID().toString())) {
                    p.addChatMessage(new ChatComponentText(message));
                    break;
                }
            }
        }
    }

    static boolean teleport(MinecraftServer server, String target, double x, double y, double z) {
        if (server == null || server.getConfigurationManager() == null) return false;
        for (Object obj : server.getConfigurationManager().playerEntityList) {
            if (obj instanceof EntityPlayerMP) {
                EntityPlayerMP p = (EntityPlayerMP) obj;
                if (target.equalsIgnoreCase(p.getCommandSenderName()) || target.equalsIgnoreCase(p.getUniqueID().toString())) {
                    p.setPositionAndUpdate(x, y, z);
                    return true;
                }
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
