package com.utopiaxc.utopiaserverpanel.web.db.model;

public class RecoveryToken {
    private int id;
    private int userId;
    private String tokenHash;
    private String tokenType; // activation, password, 2fa
    private String source; // email, ingame
    private long expiresAt;
    private Long usedAt;
    private long createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }
    public Long getUsedAt() { return usedAt; }
    public void setUsedAt(Long usedAt) { this.usedAt = usedAt; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}

