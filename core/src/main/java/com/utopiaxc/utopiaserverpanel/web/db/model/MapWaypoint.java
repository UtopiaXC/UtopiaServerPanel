package com.utopiaxc.utopiaserverpanel.web.db.model;

/**
 * Custom map waypoint persisted per save database.
 */
public class MapWaypoint {
    private int id;
    private String dimension;
    private String name;
    private int x;
    private int y;
    private int z;
    private String color;
    private String icon;
    private Integer ownerUserId;
    private boolean global;
    private long createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getX() { return x; }
    public void setX(int x) { this.x = x; }

    public int getY() { return y; }
    public void setY(int y) { this.y = y; }

    public int getZ() { return z; }
    public void setZ(int z) { this.z = z; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public Integer getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Integer ownerUserId) { this.ownerUserId = ownerUserId; }

    public boolean isGlobal() { return global; }
    public void setGlobal(boolean global) { this.global = global; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
