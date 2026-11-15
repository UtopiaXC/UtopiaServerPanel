package com.utopiaxc.utopiaserverpanel.spi.map;

/**
 * Metadata describing a game dimension available for web map rendering.
 */
public final class MapDimensionInfo {
    private final String id;
    private final String name;
    private final boolean hasCeiling;
    private final int minY;
    private final int maxY;
    private final double coordinateScale;

    public MapDimensionInfo(String id, String name, boolean hasCeiling, int minY, int maxY, double coordinateScale) {
        this.id = id;
        this.name = name;
        this.hasCeiling = hasCeiling;
        this.minY = minY;
        this.maxY = maxY;
        this.coordinateScale = coordinateScale;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isHasCeiling() { return hasCeiling; }
    public int getMinY() { return minY; }
    public int getMaxY() { return maxY; }
    public double getCoordinateScale() { return coordinateScale; }
}
