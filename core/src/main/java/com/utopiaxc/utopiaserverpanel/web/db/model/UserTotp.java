package com.utopiaxc.utopiaserverpanel.web.db.model;

public class UserTotp {
    private int userId;
    private String secretEncrypted;
    private String pendingSecretEncrypted;
    private boolean enabled;
    private Long verifiedAt;
    private long updatedAt;

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getSecretEncrypted() { return secretEncrypted; }
    public void setSecretEncrypted(String secretEncrypted) { this.secretEncrypted = secretEncrypted; }
    public String getPendingSecretEncrypted() { return pendingSecretEncrypted; }
    public void setPendingSecretEncrypted(String pendingSecretEncrypted) { this.pendingSecretEncrypted = pendingSecretEncrypted; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Long getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Long verifiedAt) { this.verifiedAt = verifiedAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}

