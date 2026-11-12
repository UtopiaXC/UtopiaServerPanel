package com.utopiaxc.utopiaserverpanel.web.db.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class RoleInfo {
    private int id;
    private String name;
    private String displayName;
    private String description;
    private boolean systemRole;
    private boolean immutable;
    private long createdAt;
    private long updatedAt;
    private int userCount;
    private Map<String, Integer> permissionLevels = new LinkedHashMap<>();

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isSystemRole() { return systemRole; }
    public void setSystemRole(boolean systemRole) { this.systemRole = systemRole; }
    public boolean isImmutable() { return immutable; }
    public void setImmutable(boolean immutable) { this.immutable = immutable; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
    public int getUserCount() { return userCount; }
    public void setUserCount(int userCount) { this.userCount = userCount; }
    public Map<String, Integer> getPermissionLevels() { return permissionLevels; }
    public void setPermissionLevels(Map<String, Integer> permissionLevels) { this.permissionLevels = permissionLevels; }
}

