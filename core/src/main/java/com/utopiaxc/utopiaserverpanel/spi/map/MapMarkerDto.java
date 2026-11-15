package com.utopiaxc.utopiaserverpanel.spi.map;

/**
 * Unified map overlay marker covering players, non-player entities, and world structures.
 */
public final class MapMarkerDto {
    private final String id;
    private final String kind; // "entity", "structure"
    private final String category; // "hostile", "passive", "boss", "other", or structure type
    private final String name;
    private final String typeId;
    private final String dimension;
    private final double x;
    private final double y;
    private final double z;

    public MapMarkerDto(String id, String kind, String category, String name, String typeId,
                        String dimension, double x, double y, double z) {
        this.id = id;
        this.kind = kind;
        this.category = category;
        this.name = name;
        this.typeId = typeId;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String getId() { return id; }
    public String getKind() { return kind; }
    public String getCategory() { return category; }
    public String getName() { return name; }
    public String getTypeId() { return typeId; }
    public String getDimension() { return dimension; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
}
