package com.utopiaxc.utopiaserverpanel.adapter.model;

/**
 * Snapshot of an online player.
 */
public final class OnlinePlayerSnapshot {
    private final String uuid;
    private final String name;
    private final String dimension;
    private final String gamemode;
    private final float health;
    private final float maxHealth;
    private final Position position;

    public OnlinePlayerSnapshot(
            String uuid,
            String name,
            String dimension,
            String gamemode,
            float health,
            float maxHealth,
            Position position
    ) {
        this.uuid = uuid;
        this.name = name;
        this.dimension = dimension;
        this.gamemode = gamemode;
        this.health = health;
        this.maxHealth = maxHealth;
        this.position = position;
    }

    public String getUuid() { return uuid; }
    public String getName() { return name; }
    public String getDimension() { return dimension; }
    public String getGamemode() { return gamemode; }
    public float getHealth() { return health; }
    public float getMaxHealth() { return maxHealth; }
    public Position getPosition() { return position; }
}

