package com.utopiaxc.utopiaserverpanel.web.db.model;

public class UserPasskey {
    private int id;
    private int userId;
    private String credentialId;
    private String name;
    private String publicKeyCose;
    private int algorithm;
    private long signCount;
    private String transports;
    private long createdAt;
    private Long lastUsedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getCredentialId() { return credentialId; }
    public void setCredentialId(String credentialId) { this.credentialId = credentialId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPublicKeyCose() { return publicKeyCose; }
    public void setPublicKeyCose(String publicKeyCose) { this.publicKeyCose = publicKeyCose; }
    public int getAlgorithm() { return algorithm; }
    public void setAlgorithm(int algorithm) { this.algorithm = algorithm; }
    public long getSignCount() { return signCount; }
    public void setSignCount(long signCount) { this.signCount = signCount; }
    public String getTransports() { return transports; }
    public void setTransports(String transports) { this.transports = transports; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public Long getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Long lastUsedAt) { this.lastUsedAt = lastUsedAt; }
}
