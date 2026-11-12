package com.utopiaxc.utopiaserverpanel.web.db.model;

public class BindingCodeInfo {
    private String code;
    private String playerUuid;
    private String playerName;
    private long createdAt;
    private long expiresAt;
    private Long consumedAt;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(String playerUuid) { this.playerUuid = playerUuid; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
    public Long getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Long consumedAt) { this.consumedAt = consumedAt; }
}

