package com.utopiaxc.utopiaserverpanel.web.service.map;

import com.utopiaxc.utopiaserverpanel.spi.map.MapMarkerDto;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Lightweight, zero-Minecraft-dependency Anvil (.mca) region file parser for Map Service fallback rendering.
 * Supports MC 1.7.10/1.12.2 legacy numeric {@code Blocks} arrays, MC 1.13-1.17 {@code Level.Sections},
 * and MC 1.18+ {@code sections} with palette + bit-packed longs and {@code structures.starts}.
 */
public final class McaRegionReader {
    private static final int SECTOR_BYTES = 4096;
    private static final int REGION_CHUNKS = 32;
    private static final int REGION_SIZE_PX = 512;
    private static final int MAX_NBT_DEPTH = 32;

    private static final Map<String, Integer> BLOCK_COLORS = buildBlockColors();
    private static final int[] LEGACY_BLOCK_COLORS = buildLegacyBlockColors();

    private McaRegionReader() {}

    public static final class RegionRenderResult {
        public final int[] argbPixels = new int[REGION_SIZE_PX * REGION_SIZE_PX];
        public final short[] heights = new short[REGION_SIZE_PX * REGION_SIZE_PX];
        public final List<MapMarkerDto> structures = new ArrayList<MapMarkerDto>();
        public int renderedChunks;
    }

    /**
     * Render a 512x512 region (.mca) tile and collect structure start coordinates cleanly.
     */
    public static RegionRenderResult readRegion(Path mcaFile, String dimension, int regionX, int regionZ, boolean hasCeiling) throws IOException {
        RegionRenderResult result = new RegionRenderResult();
        if (mcaFile == null || !Files.isRegularFile(mcaFile)) {
            return result;
        }
        long fileLen = Files.size(mcaFile);
        if (fileLen < SECTOR_BYTES * 2L) {
            return result;
        }

        byte[] header = new byte[SECTOR_BYTES];
        try (RandomAccessFile raf = new RandomAccessFile(mcaFile.toFile(), "r")) {
            raf.readFully(header);
            for (int localZ = 0; localZ < REGION_CHUNKS; localZ++) {
                for (int localX = 0; localX < REGION_CHUNKS; localX++) {
                    int idx = (localZ * REGION_CHUNKS + localX) * 4;
                    int offsetSectors = ((header[idx] & 0xFF) << 16)
                            | ((header[idx + 1] & 0xFF) << 8)
                            | (header[idx + 2] & 0xFF);
                    int sectorCount = header[idx + 3] & 0xFF;
                    if (offsetSectors < 2 || sectorCount == 0) {
                        continue;
                    }
                    long byteOffset = (long) offsetSectors * SECTOR_BYTES;
                    if (byteOffset + 5 >= fileLen) {
                        continue;
                    }
                    raf.seek(byteOffset);
                    int payloadLen = raf.readInt();
                    int compressionType = raf.readUnsignedByte();
                    if (payloadLen <= 1 || byteOffset + 4 + payloadLen > fileLen || payloadLen > 4 * 1024 * 1024) {
                        continue;
                    }
                    byte[] compressed = new byte[payloadLen - 1];
                    raf.readFully(compressed);
                    try (DataInputStream nbtIn = openChunkStream(compressed, compressionType)) {
                        if (nbtIn == null) continue;
                        int chunkX = (regionX << 5) + localX;
                        int chunkZ = (regionZ << 5) + localZ;
                        parseChunkNbt(nbtIn, dimension, chunkX, chunkZ, localX, localZ, hasCeiling, result);
                    } catch (Throwable ignored) {
                        // Skip malformed chunk cleanly so other chunks in the region still render
                    }
                }
            }
        }

        applyJourneyShading(result.argbPixels, result.heights);
        return result;
    }

    /**
     * Apply JourneyMap-style northwest/southeast slope shading to an unshaded ARGB pixel buffer
     * using height differences between adjacent columns.
     */
    public static void applyJourneyShading(int[] argbPixels, short[] heights) {
        if (argbPixels == null || heights == null || argbPixels.length < REGION_SIZE_PX * REGION_SIZE_PX) {
            return;
        }
        for (int z = 0; z < REGION_SIZE_PX; z++) {
            int pz = Math.max(0, z - 1);
            for (int x = 0; x < REGION_SIZE_PX; x++) {
                int idx = z * REGION_SIZE_PX + x;
                int pxVal = argbPixels[idx];
                if ((pxVal >>> 24) == 0) continue;

                int px = Math.max(0, x - 1);
                short hCur = heights[idx];
                short hNW = heights[pz * REGION_SIZE_PX + px];
                int diff = hCur - hNW;
                if (diff == 0) continue;

                double factor = 1.0 + Math.max(-0.24, Math.min(0.24, diff * 0.045));
                argbPixels[idx] = scaleRgb(pxVal, factor);
            }
        }
    }

    public static int shadeSinglePixel(int baseArgb, int heightDiff) {
        if (heightDiff == 0 || (baseArgb >>> 24) == 0) return baseArgb;
        double factor = 1.0 + Math.max(-0.24, Math.min(0.24, heightDiff * 0.045));
        return scaleRgb(baseArgb, factor);
    }

    private static int scaleRgb(int argb, double factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = clampByte((int) Math.round(((argb >>> 16) & 0xFF) * factor));
        int g = clampByte((int) Math.round(((argb >>> 8) & 0xFF) * factor));
        int b = clampByte((int) Math.round((argb & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clampByte(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    public static int resolveColorForBlockName(String blockName) {
        if (blockName == null || blockName.isEmpty()) return 0;
        String lower = blockName.toLowerCase(Locale.ROOT);
        int colon = lower.indexOf(':');
        String shortName = colon >= 0 ? lower.substring(colon + 1) : lower;

        if ("air".equals(shortName) || "cave_air".equals(shortName) || "void_air".equals(shortName)
                || "barrier".equals(shortName) || "structure_void".equals(shortName)) {
            return 0;
        }
        Integer exact = BLOCK_COLORS.get(shortName);
        if (exact != null) return 0xFF000000 | exact.intValue();

        // Heuristic family matches so MC 1.13-26.x & modded blocks have natural colors
        if (shortName.contains("water") || shortName.contains("bubble_column")) return 0xFF335EC4;
        if (shortName.contains("lava")) return 0xFFD9591A;
        if (shortName.contains("leaves") || shortName.contains("foliage")) return 0xFF3F7A28;
        if (shortName.contains("grass") || shortName.contains("moss") || shortName.contains("fern")) return 0xFF5D9C3B;
        if (shortName.contains("snow") || shortName.contains("powder_snow")) return 0xFFEDF2F7;
        if (shortName.contains("ice")) return 0xFF8AB8F0;
        if (shortName.contains("sandstone")) return 0xFFD5C48B;
        if (shortName.contains("red_sand")) return 0xFFB85B2B;
        if (shortName.contains("sand")) return 0xFFDFD299;
        if (shortName.contains("terracotta") || shortName.contains("clay")) return 0xFF9C6148;
        if (shortName.contains("deepslate")) return 0xFF47494E;
        if (shortName.contains("blackstone") || shortName.contains("basalt") || shortName.contains("obsidian")) return 0xFF282430;
        if (shortName.contains("netherrack") || shortName.contains("nylium")) return 0xFF6B2A2B;
        if (shortName.contains("end_stone")) return 0xFFDAD59A;
        if (shortName.contains("purpur") || shortName.contains("chorus")) return 0xFF966796;
        if (shortName.contains("sculk")) return 0xFF0E2933;
        if (shortName.contains("copper")) return 0xFF4C8E75;
        if (shortName.contains("wood") || shortName.contains("log") || shortName.contains("planks")) return 0xFF7D5D38;
        if (shortName.contains("stone") || shortName.contains("cobble") || shortName.contains("andesite")
                || shortName.contains("diorite") || shortName.contains("granite") || shortName.contains("tuff")) return 0xFF787C82;
        if (shortName.contains("dirt") || shortName.contains("farmland") || shortName.contains("mud")) return 0xFF7E583A;

        // Deterministic pleasant color for arbitrary modded blocks
        int h = shortName.hashCode();
        int r = 70 + Math.abs(h & 0x7F);
        int g = 70 + Math.abs((h >>> 8) & 0x7F);
        int b = 70 + Math.abs((h >>> 16) & 0x7F);
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    private static DataInputStream openChunkStream(byte[] payload, int compression) throws IOException {
        InputStream base = new ByteArrayInputStream(payload);
        if (compression == 1) {
            return new DataInputStream(new BufferedInputStream(new GZIPInputStream(base)));
        } else if (compression == 2) {
            return new DataInputStream(new BufferedInputStream(new InflaterInputStream(base)));
        } else if (compression == 3) {
            return new DataInputStream(base);
        }
        return null;
    }

    private static final class SectionData {
        int y;
        // 1.7 / 1.12 legacy byte block IDs (4096 bytes YZX)
        byte[] legacyBlocks;
        // 1.13 - 1.17 BlockStates long array + Palette
        List<String> palette;
        long[] packedBlockStates;
        boolean pre116Packing; // continuous packing across 64-bit longs (1.13-1.15) vs aligned (1.16+)
    }

    private static void parseChunkNbt(DataInputStream in, String dimension, int chunkX, int chunkZ,
                                      int localX, int localZ, boolean hasCeiling, RegionRenderResult result) throws IOException {
        int rootType = in.readUnsignedByte();
        if (rootType != 10) return;
        readString(in); // root name

        int dataVersion = 0;
        List<SectionData> sections = new ArrayList<SectionData>();
        byte[] legacyFlatBlocks = null; // 1.7.10 alpha Level.Blocks? Usually Sections exists in Anvil 1.2+

        while (true) {
            int type = in.readUnsignedByte();
            if (type == 0) break;
            String name = readString(in);
            if ("DataVersion".equals(name) && type == 3) {
                dataVersion = in.readInt();
            } else if ("sections".equals(name) && type == 9) {
                readSectionsList(in, sections, dataVersion);
            } else if ("structures".equals(name) && type == 10) {
                readStructuresCompound(in, dimension, chunkX, chunkZ, result.structures);
            } else if ("Level".equals(name) && type == 10) {
                parseLevelCompound(in, dimension, chunkX, chunkZ, sections, result.structures, dataVersion);
            } else {
                skipPayload(in, type, 0);
            }
        }

        if (sections.isEmpty() && legacyFlatBlocks == null) {
            return;
        }
        result.renderedChunks++;
        renderSectionsToRegion(sections, localX, localZ, hasCeiling, result);
    }

    private static void parseLevelCompound(DataInputStream in, String dimension, int chunkX, int chunkZ,
                                           List<SectionData> sections, List<MapMarkerDto> structures, int dataVersion) throws IOException {
        while (true) {
            int type = in.readUnsignedByte();
            if (type == 0) break;
            String name = readString(in);
            if ("Sections".equals(name) && type == 9) {
                readSectionsList(in, sections, dataVersion);
            } else if ("Structures".equals(name) && type == 10) {
                readStructuresCompound(in, dimension, chunkX, chunkZ, structures);
            } else {
                skipPayload(in, type, 0);
            }
        }
    }

    private static void readSectionsList(DataInputStream in, List<SectionData> out, int dataVersion) throws IOException {
        int elemType = in.readUnsignedByte();
        int count = in.readInt();
        if (count <= 0 || elemType != 10) {
            for (int i = 0; i < Math.max(0, count); i++) skipPayload(in, elemType, 1);
            return;
        }
        for (int i = 0; i < count; i++) {
            SectionData sec = readSingleSection(in, dataVersion);
            if (sec != null) out.add(sec);
        }
    }

    private static SectionData readSingleSection(DataInputStream in, int dataVersion) throws IOException {
        SectionData sec = new SectionData();
        sec.y = Integer.MIN_VALUE;
        sec.pre116Packing = dataVersion > 0 && dataVersion < 2529;
        while (true) {
            int type = in.readUnsignedByte();
            if (type == 0) break;
            String name = readString(in);
            if ("Y".equals(name) && (type == 1 || type == 3)) {
                sec.y = (type == 1) ? in.readByte() : in.readInt();
            } else if ("Blocks".equals(name) && type == 7) {
                int len = in.readInt();
                byte[] buf = new byte[Math.max(0, len)];
                in.readFully(buf);
                sec.legacyBlocks = buf;
            } else if ("Palette".equals(name) && type == 9) {
                sec.palette = readPaletteList(in);
            } else if ("BlockStates".equals(name) && type == 12) {
                sec.packedBlockStates = readLongArray(in);
            } else if ("block_states".equals(name) && type == 10) {
                readModernBlockStates(in, sec);
            } else {
                skipPayload(in, type, 1);
            }
        }
        if (sec.y == Integer.MIN_VALUE) return null;
        return sec;
    }

    private static void readModernBlockStates(DataInputStream in, SectionData sec) throws IOException {
        sec.pre116Packing = false;
        while (true) {
            int type = in.readUnsignedByte();
            if (type == 0) break;
            String name = readString(in);
            if ("palette".equals(name) && type == 9) {
                sec.palette = readPaletteList(in);
            } else if ("data".equals(name) && type == 12) {
                sec.packedBlockStates = readLongArray(in);
            } else {
                skipPayload(in, type, 2);
            }
        }
    }

    private static List<String> readPaletteList(DataInputStream in) throws IOException {
        int elemType = in.readUnsignedByte();
        int count = in.readInt();
        if (count <= 0 || elemType != 10) {
            for (int i = 0; i < Math.max(0, count); i++) skipPayload(in, elemType, 2);
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            String blockName = "minecraft:air";
            while (true) {
                int t = in.readUnsignedByte();
                if (t == 0) break;
                String k = readString(in);
                if ("Name".equals(k) && t == 8) {
                    blockName = readString(in);
                } else {
                    skipPayload(in, t, 3);
                }
            }
            names.add(blockName);
        }
        return names;
    }

    private static long[] readLongArray(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > 65536) return new long[0];
        long[] arr = new long[count];
        for (int i = 0; i < count; i++) arr[i] = in.readLong();
        return arr;
    }

    private static void readStructuresCompound(DataInputStream in, String dimension, int chunkX, int chunkZ,
                                               List<MapMarkerDto> out) throws IOException {
        while (true) {
            int type = in.readUnsignedByte();
            if (type == 0) break;
            String name = readString(in);
            if (("starts".equals(name) || "Starts".equals(name)) && type == 10) {
                while (true) {
                    int st = in.readUnsignedByte();
                    if (st == 0) break;
                    String structId = readString(in);
                    boolean validStart = false;
                    int sx = chunkX, sz = chunkZ;
                    if (st == 10) {
                        while (true) {
                            int ft = in.readUnsignedByte();
                            if (ft == 0) break;
                            String fn = readString(in);
                            if ("id".equals(fn) && ft == 8) {
                                String idVal = readString(in);
                                validStart = idVal != null && !"INVALID".equalsIgnoreCase(idVal);
                            } else if ("ChunkX".equals(fn) && ft == 3) {
                                sx = in.readInt();
                            } else if ("ChunkZ".equals(fn) && ft == 3) {
                                sz = in.readInt();
                            } else if ("Children".equals(fn) && ft == 9) {
                                // Modern worlds have id=structKey or omitted 'id' inside starts when valid
                                skipPayload(in, ft, 2);
                                validStart = true;
                            } else {
                                skipPayload(in, ft, 2);
                            }
                        }
                    } else {
                        skipPayload(in, st, 2);
                    }
                    if (validStart && sx == chunkX && sz == chunkZ) {
                        String normId = structId.toLowerCase(Locale.ROOT);
                        if (!normId.contains("INVALID")) {
                            double bx = (chunkX << 4) + 8.0;
                            double bz = (chunkZ << 4) + 8.0;
                            out.add(new MapMarkerDto(
                                    "struct:" + dimension + ":" + normId + ":" + chunkX + ":" + chunkZ,
                                    "structure",
                                    categorizeStructure(normId),
                                    formatStructureName(normId),
                                    normId,
                                    dimension,
                                    bx, 64.0, bz
                            ));
                        }
                    }
                }
            } else {
                skipPayload(in, type, 1);
            }
        }
    }

    private static String categorizeStructure(String id) {
        if (id.contains("end_city") || id.contains("stronghold") || id.contains("fortress")
                || id.contains("bastion") || id.contains("monument") || id.contains("ancient_city")
                || id.contains("trial_chambers") || id.contains("mansion")) {
            return "major_structure";
        }
        if (id.contains("village") || id.contains("outpost") || id.contains("temple") || id.contains("pyramid")) {
            return "settlement";
        }
        return "structure";
    }

    private static String formatStructureName(String id) {
        int colon = id.indexOf(':');
        String shortName = colon >= 0 ? id.substring(colon + 1) : id;
        return shortName.replace('_', ' ');
    }

    private static void renderSectionsToRegion(List<SectionData> sections, int localX, int localZ,
                                               boolean hasCeiling, RegionRenderResult result) {
        Collections.sort(sections, (a, b) -> Integer.compare(b.y, a.y));

        int basePxX = localX * 16;
        int basePxZ = localZ * 16;
        boolean[] done = new boolean[256];
        int remaining = 256;

        for (SectionData sec : sections) {
            if (remaining == 0) break;
            int minSubY = 0;
            int maxSubY = 15;
            if (hasCeiling && sec.y > 7) {
                // Skip bedrock ceiling sections above Y=127 in Nether/ceiling dims
                continue;
            }
            if (hasCeiling && sec.y == 7) {
                maxSubY = 11; // Peek below Y=124 bedrock roof
            }

            if (sec.legacyBlocks != null && sec.legacyBlocks.length >= 4096) {
                for (int sy = maxSubY; sy >= minSubY && remaining > 0; sy--) {
                    int yBase = sy << 8;
                    int worldY = (sec.y << 4) + sy;
                    for (int z = 0; z < 16; z++) {
                        int zBase = yBase | (z << 4);
                        for (int x = 0; x < 16; x++) {
                            int cIdx = (z << 4) | x;
                            if (done[cIdx]) continue;
                            int blockId = sec.legacyBlocks[zBase | x] & 0xFF;
                            if (blockId == 0) continue;
                            int color = LEGACY_BLOCK_COLORS[blockId];
                            if ((color >>> 24) == 0) continue;
                            int pxIdx = (basePxZ + z) * REGION_SIZE_PX + (basePxX + x);
                            result.argbPixels[pxIdx] = color;
                            result.heights[pxIdx] = (short) worldY;
                            done[cIdx] = true;
                            remaining--;
                        }
                    }
                }
            } else if (sec.palette != null && !sec.palette.isEmpty()) {
                int[] palColors = new int[sec.palette.size()];
                for (int i = 0; i < palColors.length; i++) {
                    palColors[i] = resolveColorForBlockName(sec.palette.get(i));
                }
                if (sec.packedBlockStates == null || sec.packedBlockStates.length == 0) {
                    // Uniform single-block section
                    int uniformColor = palColors[0];
                    if ((uniformColor >>> 24) != 0) {
                        int worldY = (sec.y << 4) + maxSubY;
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                int cIdx = (z << 4) | x;
                                if (!done[cIdx]) {
                                    int pxIdx = (basePxZ + z) * REGION_SIZE_PX + (basePxX + x);
                                    result.argbPixels[pxIdx] = uniformColor;
                                    result.heights[pxIdx] = (short) worldY;
                                    done[cIdx] = true;
                                    remaining--;
                                }
                            }
                        }
                    }
                    continue;
                }

                int bitsPerEntry = Math.max(4, 32 - Integer.numberOfLeadingZeros(palColors.length - 1));
                long mask = (1L << bitsPerEntry) - 1L;
                int entriesPerLong = 64 / bitsPerEntry;

                for (int sy = maxSubY; sy >= minSubY && remaining > 0; sy--) {
                    int yBase = sy << 8;
                    int worldY = (sec.y << 4) + sy;
                    for (int z = 0; z < 16; z++) {
                        int zBase = yBase | (z << 4);
                        for (int x = 0; x < 16; x++) {
                            int cIdx = (z << 4) | x;
                            if (done[cIdx]) continue;
                            int blockIndex = zBase | x;
                            int palIdx = readPackedPaletteIndex(sec.packedBlockStates, blockIndex, bitsPerEntry, mask, entriesPerLong, sec.pre116Packing);
                            if (palIdx < 0 || palIdx >= palColors.length) continue;
                            int color = palColors[palIdx];
                            if ((color >>> 24) == 0) continue;
                            int pxIdx = (basePxZ + z) * REGION_SIZE_PX + (basePxX + x);
                            result.argbPixels[pxIdx] = color;
                            result.heights[pxIdx] = (short) worldY;
                            done[cIdx] = true;
                            remaining--;
                        }
                    }
                }
            }
        }
    }

    private static int readPackedPaletteIndex(long[] data, int index, int bitsPerEntry, long mask,
                                              int entriesPerLong, boolean pre116) {
        if (!pre116) {
            int longIdx = index / entriesPerLong;
            if (longIdx >= data.length) return 0;
            int bitOffset = (index % entriesPerLong) * bitsPerEntry;
            return (int) ((data[longIdx] >>> bitOffset) & mask);
        }
        int startBit = index * bitsPerEntry;
        int startLong = startBit >>> 6;
        int endLong = (startBit + bitsPerEntry - 1) >>> 6;
        if (startLong >= data.length) return 0;
        int startOffset = startBit & 63;
        if (startLong == endLong || endLong >= data.length) {
            return (int) ((data[startLong] >>> startOffset) & mask);
        }
        int lowBits = 64 - startOffset;
        long val = (data[startLong] >>> startOffset) | (data[endLong] << lowBits);
        return (int) (val & mask);
    }

    private static void skipPayload(DataInputStream in, int tagType, int depth) throws IOException {
        if (depth > MAX_NBT_DEPTH) throw new IOException("NBT depth exceeded");
        switch (tagType) {
            case 0: break;
            case 1: in.readByte(); break;
            case 2: in.readShort(); break;
            case 3:
            case 5: in.readInt(); break;
            case 4:
            case 6: in.readLong(); break;
            case 7: skipExact(in, in.readInt()); break;
            case 8: skipExact(in, in.readUnsignedShort()); break;
            case 9: {
                int et = in.readUnsignedByte();
                int cnt = in.readInt();
                for (int i = 0; i < cnt; i++) skipPayload(in, et, depth + 1);
                break;
            }
            case 10: {
                while (true) {
                    int ct = in.readUnsignedByte();
                    if (ct == 0) break;
                    skipExact(in, in.readUnsignedShort());
                    skipPayload(in, ct, depth + 1);
                }
                break;
            }
            case 11: skipExact(in, (long) in.readInt() * 4L); break;
            case 12: skipExact(in, (long) in.readInt() * 8L); break;
            default: throw new IOException("Unknown NBT tag: " + tagType);
        }
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readUnsignedShort();
        byte[] b = new byte[len];
        in.readFully(b);
        return new String(b, StandardCharsets.UTF_8);
    }

    private static void skipExact(DataInputStream in, long bytes) throws IOException {
        long rem = bytes;
        while (rem > 0) {
            int s = in.skipBytes((int) Math.min(rem, Integer.MAX_VALUE));
            if (s <= 0) {
                if (in.read() == -1) throw new EOFException("EOF skipping NBT");
                rem--;
            } else {
                rem -= s;
            }
        }
    }

    private static Map<String, Integer> buildBlockColors() {
        Map<String, Integer> m = new HashMap<String, Integer>();
        m.put("stone", 0x787C82);
        m.put("granite", 0x996E5B);
        m.put("diorite", 0xBCBCBC);
        m.put("andesite", 0x828485);
        m.put("grass_block", 0x5D9C3B);
        m.put("dirt", 0x7E583A);
        m.put("coarse_dirt", 0x704E34);
        m.put("podzol", 0x5E4124);
        m.put("mycelium", 0x6F5E75);
        m.put("cobblestone", 0x6D7075);
        m.put("bedrock", 0x454549);
        m.put("water", 0x335EC4);
        m.put("lava", 0xD9591A);
        m.put("sand", 0xDFD299);
        m.put("gravel", 0x837E7B);
        m.put("snow", 0xEDF2F7);
        m.put("snow_block", 0xEDF2F7);
        m.put("ice", 0x8AB8F0);
        m.put("packed_ice", 0x7CA7DE);
        m.put("blue_ice", 0x6995DB);
        m.put("oak_leaves", 0x3F7A28);
        m.put("spruce_leaves", 0x2F5E34);
        m.put("birch_leaves", 0x528034);
        m.put("jungle_leaves", 0x3B8722);
        m.put("acacia_leaves", 0x487A2A);
        m.put("dark_oak_leaves", 0x2E5C1B);
        m.put("cherry_leaves", 0xE695B8);
        m.put("netherrack", 0x6B2A2B);
        m.put("soul_sand", 0x4D3A2D);
        m.put("soul_soil", 0x45352A);
        m.put("glowstone", 0xE6B84D);
        m.put("end_stone", 0xDAD59A);
        m.put("obsidian", 0x1D1626);
        m.put("clay", 0x999EAD);
        return m;
    }

    private static int[] buildLegacyBlockColors() {
        int[] c = new int[256];
        Arrays.fill(c, 0xFF7A7A7A);
        c[0] = 0; // air
        c[1] = 0xFF787C82; // stone
        c[2] = 0xFF5D9C3B; // grass
        c[3] = 0xFF7E583A; // dirt
        c[4] = 0xFF6D7075; // cobblestone
        c[5] = 0xFF8C6B42; // planks
        c[7] = 0xFF454549; // bedrock
        c[8] = 0xFF335EC4; // flowing_water
        c[9] = 0xFF335EC4; // water
        c[10] = 0xFFD9591A; // flowing_lava
        c[11] = 0xFFD9591A; // lava
        c[12] = 0xFFDFD299; // sand
        c[13] = 0xFF837E7B; // gravel
        c[17] = 0xFF6B502F; // log
        c[18] = 0xFF3F7A28; // leaves
        c[24] = 0xFFD5C48B; // sandstone
        c[45] = 0xFF8A453B; // brick_block
        c[48] = 0xFF577355; // mossy_cobblestone
        c[49] = 0xFF1D1626; // obsidian
        c[78] = 0xFFEDF2F7; // snow_layer
        c[79] = 0xFF8AB8F0; // ice
        c[80] = 0xFFEDF2F7; // snow
        c[82] = 0xFF999EAD; // clay
        c[87] = 0xFF6B2A2B; // netherrack
        c[88] = 0xFF4D3A2D; // soul_sand
        c[89] = 0xFFE6B84D; // glowstone
        c[110] = 0xFF6F5E75; // mycelium
        c[112] = 0xFF341A21; // nether_brick
        c[121] = 0xFFDAD59A; // end_stone
        c[159] = 0xFF9C6148; // stained_hardened_clay
        c[161] = 0xFF3D7527; // leaves2
        c[172] = 0xFF9C6148; // hardened_clay
        c[174] = 0xFF7CA7DE; // packed_ice
        return c;
    }
}
