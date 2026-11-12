package com.utopiaxc.utopiaserverpanel.web.db.model;

import com.google.gson.annotations.SerializedName;

public class PermissionDef {
    @SerializedName("key")
    private String permKey;
    private String category;
    private int sortOrder;
    private boolean active;

    public String getPermKey() { return permKey; }
    public void setPermKey(String permKey) { this.permKey = permKey; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}

