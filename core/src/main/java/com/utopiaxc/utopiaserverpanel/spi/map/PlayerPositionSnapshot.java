package com.utopiaxc.utopiaserverpanel.spi.map;

/**
 * Pure Java immutable realtime player coordinate snapshot for live web map rendering.
 */
public final class PlayerPositionSnapshot {
    private final String uuid;
    private final String name;
    private final String dimension;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final float health;

    public PlayerPositionSnapshot(
            String uuid,
            String name,
            String dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            float health
    ) {
        this.uuid = uuid;
        this.name = name;
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.health = health;
    }

    public String getUuid() { return uuid; }
    public String getName() { return name; }
    public String getDimension() { return dimension; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public float getHealth() { return health; }
}

