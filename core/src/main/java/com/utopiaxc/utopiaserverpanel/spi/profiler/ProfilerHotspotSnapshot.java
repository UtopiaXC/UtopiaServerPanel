package com.utopiaxc.utopiaserverpanel.spi.profiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-wide hotspot snapshot collected on the game server thread.
 */
public final class ProfilerHotspotSnapshot {
    private final List<DimensionHotspotDto> dimensions = new ArrayList<>();
    private final List<ChunkHotspotDto> topEntityChunks = new ArrayList<>();
    private final List<ChunkHotspotDto> topBlockEntityChunks = new ArrayList<>();
    private final List<TypeCountDto> topEntityTypes = new ArrayList<>();
    private final List<TypeCountDto> topBlockEntityTypes = new ArrayList<>();
    private final long capturedAt;

    public ProfilerHotspotSnapshot() {
        this.capturedAt = System.currentTimeMillis();
    }

    public List<DimensionHotspotDto> getDimensions() { return dimensions; }
    public List<ChunkHotspotDto> getTopEntityChunks() { return topEntityChunks; }
    public List<ChunkHotspotDto> getTopBlockEntityChunks() { return topBlockEntityChunks; }
    public List<TypeCountDto> getTopEntityTypes() { return topEntityTypes; }
    public List<TypeCountDto> getTopBlockEntityTypes() { return topBlockEntityTypes; }
    public long getCapturedAt() { return capturedAt; }

    public static final class TypeCountDto {
        private final String typeId;
        private final int count;

        public TypeCountDto(String typeId, int count) {
            this.typeId = typeId;
            this.count = count;
        }

        public String getTypeId() { return typeId; }
        public int getCount() { return count; }
    }
}
