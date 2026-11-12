package com.utopiaxc.utopiaserverpanel.adapter.util;

import com.utopiaxc.utopiaserverpanel.adapter.model.Position;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * Minimal zero-dependency binary NBT reader for offline {@code playerdata/<uuid>.dat} files.
 * Works uniformly across Minecraft 1.7.10 through 1.21.1+ without referencing version-specific
 * {@code CompoundTag}/{@code NbtIo}/{@code NbtAccounter} classes.
 */
public final class NbtLiteReader {
    private static final int TAG_END = 0;
    private static final int TAG_BYTE = 1;
    private static final int TAG_SHORT = 2;
    private static final int TAG_INT = 3;
    private static final int TAG_LONG = 4;
    private static final int TAG_FLOAT = 5;
    private static final int TAG_DOUBLE = 6;
    private static final int TAG_BYTE_ARRAY = 7;
    private static final int TAG_STRING = 8;
    private static final int TAG_LIST = 9;
    private static final int TAG_COMPOUND = 10;
    private static final int TAG_INT_ARRAY = 11;
    private static final int TAG_LONG_ARRAY = 12;

    private static final int MAX_DEPTH = 64;

    private NbtLiteReader() {}

    public static final class OfflinePlayerNbt {
        private Position position;
        private String dimension;
        private String gamemode;
        private Float health;

        public Position getPosition() { return position; }
        public String getDimension() { return dimension; }
        public String getGamemode() { return gamemode; }
        public Float getHealth() { return health; }
    }

    public static OfflinePlayerNbt readPlayerDat(Path playerDatFile) throws IOException {
        if (playerDatFile == null || !Files.isRegularFile(playerDatFile)) {
            return null;
        }
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(playerDatFile))) {
            raw.mark(2);
            int b1 = raw.read();
            int b2 = raw.read();
            raw.reset();

            InputStream nbtStream = (b1 == 0x1f && b2 == 0x8b)
                    ? new GZIPInputStream(raw)
                    : raw;

            DataInputStream in = new DataInputStream(nbtStream);
            int rootType = in.readUnsignedByte();
            if (rootType != TAG_COMPOUND) {
                return null;
            }
            readString(in); // skip root name

            OfflinePlayerNbt result = new OfflinePlayerNbt();
            while (true) {
                int tagType = in.readUnsignedByte();
                if (tagType == TAG_END) {
                    break;
                }
                String name = readString(in);
                if ("Pos".equals(name) && tagType == TAG_LIST) {
                    result.position = readPosList(in);
                } else if ("Dimension".equals(name) && (tagType == TAG_STRING || tagType == TAG_INT)) {
                    if (tagType == TAG_STRING) {
                        result.dimension = readString(in);
                    } else {
                        result.dimension = legacyDimName(in.readInt());
                    }
                } else if ("playerGameType".equals(name) && tagType == TAG_INT) {
                    result.gamemode = gameTypeName(in.readInt());
                } else if ("Health".equals(name)) {
                    if (tagType == TAG_FLOAT) {
                        result.health = Float.valueOf(in.readFloat());
                    } else if (tagType == TAG_SHORT) {
                        result.health = Float.valueOf(in.readShort());
                    } else if (tagType == TAG_DOUBLE) {
                        result.health = Float.valueOf((float) in.readDouble());
                    } else {
                        skipPayload(in, tagType, 0);
                    }
                } else {
                    skipPayload(in, tagType, 0);
                }
            }
            return result;
        }
    }

    private static Position readPosList(DataInputStream in) throws IOException {
        int elemType = in.readUnsignedByte();
        int count = in.readInt();
        if (elemType == TAG_DOUBLE && count >= 3) {
            int x = (int) in.readDouble();
            int y = (int) in.readDouble();
            int z = (int) in.readDouble();
            for (int i = 3; i < count; i++) {
                in.readDouble();
            }
            return new Position(x, y, z);
        }
        if (elemType == TAG_FLOAT && count >= 3) {
            int x = (int) in.readFloat();
            int y = (int) in.readFloat();
            int z = (int) in.readFloat();
            for (int i = 3; i < count; i++) {
                in.readFloat();
            }
            return new Position(x, y, z);
        }
        for (int i = 0; i < count; i++) {
            skipPayload(in, elemType, 1);
        }
        return null;
    }

    private static void skipPayload(DataInputStream in, int tagType, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT nesting depth exceeded");
        }
        switch (tagType) {
            case TAG_END:
                break;
            case TAG_BYTE:
                in.readByte();
                break;
            case TAG_SHORT:
                in.readShort();
                break;
            case TAG_INT:
                in.readInt();
                break;
            case TAG_LONG:
                in.readLong();
                break;
            case TAG_FLOAT:
                in.readFloat();
                break;
            case TAG_DOUBLE:
                in.readDouble();
                break;
            case TAG_BYTE_ARRAY: {
                int len = in.readInt();
                if (len < 0) throw new IOException("Negative byte array length");
                skipExact(in, len);
                break;
            }
            case TAG_STRING: {
                int len = in.readUnsignedShort();
                skipExact(in, len);
                break;
            }
            case TAG_LIST: {
                int elemType = in.readUnsignedByte();
                int count = in.readInt();
                if (count < 0) throw new IOException("Negative list length");
                for (int i = 0; i < count; i++) {
                    skipPayload(in, elemType, depth + 1);
                }
                break;
            }
            case TAG_COMPOUND: {
                while (true) {
                    int childType = in.readUnsignedByte();
                    if (childType == TAG_END) break;
                    int nameLen = in.readUnsignedShort();
                    skipExact(in, nameLen);
                    skipPayload(in, childType, depth + 1);
                }
                break;
            }
            case TAG_INT_ARRAY: {
                int count = in.readInt();
                if (count < 0) throw new IOException("Negative int array length");
                skipExact(in, (long) count * 4L);
                break;
            }
            case TAG_LONG_ARRAY: {
                int count = in.readInt();
                if (count < 0) throw new IOException("Negative long array length");
                skipExact(in, (long) count * 8L);
                break;
            }
            default:
                throw new IOException("Unknown NBT tag type: " + tagType);
        }
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readUnsignedShort();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void skipExact(DataInputStream in, long bytesToSkip) throws IOException {
        long remaining = bytesToSkip;
        while (remaining > 0) {
            int skipped = in.skipBytes((int) Math.min(remaining, Integer.MAX_VALUE));
            if (skipped <= 0) {
                if (in.read() == -1) {
                    throw new EOFException("Unexpected EOF while skipping NBT payload");
                }
                remaining--;
            } else {
                remaining -= skipped;
            }
        }
    }

    private static String gameTypeName(int gameType) {
        switch (gameType) {
            case 0: return "survival";
            case 1: return "creative";
            case 2: return "adventure";
            case 3: return "spectator";
            default: return "unknown";
        }
    }

    private static String legacyDimName(int dimId) {
        switch (dimId) {
            case 0: return "minecraft:overworld";
            case -1: return "minecraft:the_nether";
            case 1: return "minecraft:the_end";
            default: return "dimension:" + dimId;
        }
    }
}

