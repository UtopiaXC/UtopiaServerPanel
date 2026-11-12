package com.utopiaxc.utopiaserverpanel.adapter.model;

/**
 * Detailed snapshot for either an online or offline player.
 */
public final class PlayerInfoSnapshot {
    private boolean supported = true;
    private String error;
    private boolean online;

    private String name;
    private String dimension;
    private String gamemode;
    private Float health;
    private Float maxHealth;
    private Position position;
    private Integer deathCount;

    private Position lastPosition;
    private String lastDimension;
    private String lastGamemode;
    private Float lastHealth;

    public static PlayerInfoSnapshot unsupported(String error) {
        PlayerInfoSnapshot s = new PlayerInfoSnapshot();
        s.supported = false;
        s.error = error;
        return s;
    }

    public boolean isSupported() { return supported; }
    public void setSupported(boolean supported) { this.supported = supported; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDimension() { return dimension; }
    public void setDimension(String dimension) { this.dimension = dimension; }

    public String getGamemode() { return gamemode; }
    public void setGamemode(String gamemode) { this.gamemode = gamemode; }

    public Float getHealth() { return health; }
    public void setHealth(Float health) { this.health = health; }

    public Float getMaxHealth() { return maxHealth; }
    public void setMaxHealth(Float maxHealth) { this.maxHealth = maxHealth; }

    public Position getPosition() { return position; }
    public void setPosition(Position position) { this.position = position; }

    public Integer getDeathCount() { return deathCount; }
    public void setDeathCount(Integer deathCount) { this.deathCount = deathCount; }

    public Position getLastPosition() { return lastPosition; }
    public void setLastPosition(Position lastPosition) { this.lastPosition = lastPosition; }

    public String getLastDimension() { return lastDimension; }
    public void setLastDimension(String lastDimension) { this.lastDimension = lastDimension; }

    public String getLastGamemode() { return lastGamemode; }
    public void setLastGamemode(String lastGamemode) { this.lastGamemode = lastGamemode; }

    public Float getLastHealth() { return lastHealth; }
    public void setLastHealth(Float lastHealth) { this.lastHealth = lastHealth; }
}

