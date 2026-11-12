package com.utopiaxc.utopiaserverpanel.web.db.model;

public class UserAccount {
    private int id;
    private String username;
    private String nickname;
    private String email;
    private String passwordHash;
    private int roleId;
    private String roleName;
    private String roleDisplayName;
    private String status;
    private boolean mustChangePassword;
    private boolean require2fa;
    private int failedLoginAttempts;
    private Long lockoutUntil;
    private int tokenVersion;
    private String locale;
    private long createdAt;
    private long updatedAt;
    // Joined from player_bindings -> mc_players & user_totp
    private String playerUuid;
    private String playerName;
    private Long boundAt;
    private boolean totpEnabled;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public int getRoleId() { return roleId; }
    public void setRoleId(int roleId) { this.roleId = roleId; }
    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }
    public String getRoleDisplayName() { return roleDisplayName; }
    public void setRoleDisplayName(String roleDisplayName) { this.roleDisplayName = roleDisplayName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public boolean isRequire2fa() { return require2fa; }
    public void setRequire2fa(boolean require2fa) { this.require2fa = require2fa; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }
    public Long getLockoutUntil() { return lockoutUntil; }
    public void setLockoutUntil(Long lockoutUntil) { this.lockoutUntil = lockoutUntil; }
    public int getTokenVersion() { return tokenVersion; }
    public void setTokenVersion(int tokenVersion) { this.tokenVersion = tokenVersion; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public String getPlayerUuid() { return playerUuid; }
    public void setPlayerUuid(String playerUuid) { this.playerUuid = playerUuid; }
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public Long getBoundAt() { return boundAt; }
    public void setBoundAt(Long boundAt) { this.boundAt = boundAt; }
    public boolean isTotpEnabled() { return totpEnabled; }
    public void setTotpEnabled(boolean totpEnabled) { this.totpEnabled = totpEnabled; }
    public boolean isBound() { return playerUuid != null && !playerUuid.isEmpty(); }
}

