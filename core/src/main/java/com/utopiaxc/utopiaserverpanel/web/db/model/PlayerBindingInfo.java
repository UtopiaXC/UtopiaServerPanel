package com.utopiaxc.utopiaserverpanel.web.db.model;

public class PlayerBindingInfo {
    private int id;
    private int userId;
    private String playerUuid;
    private String playerName;
    private long boundAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(String playerUuid) { this.playerUuid = playerUuid; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public long getBoundAt() { return boundAt; }
    public void setBoundAt(long boundAt) { this.boundAt = boundAt; }
}

