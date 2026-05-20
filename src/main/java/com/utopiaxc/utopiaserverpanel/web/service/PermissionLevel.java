package com.utopiaxc.utopiaserverpanel.web.service;

/**
 * Permission access levels for the panel.
 * Each permission category (admin, dashboard, terminal, logs) can be set to one of these levels.
 */
public enum PermissionLevel {
    /** No access at all. */
    DENY(0),
    /** Read-only access: can view but not modify. */
    READONLY(1),
    /** Full access: can view and modify. */
    FULL(2);

    private final int level;

    PermissionLevel(int level) {
        this.level = level;
    }

    public int getLevel() { return level; }

    public static PermissionLevel fromLevel(int level) {
        for (PermissionLevel pl : values()) {
            if (pl.level == level) return pl;
        }
        return DENY;
    }

    /** Permission categories used in the system. */
    public static final String[] PERMISSION_KEYS = {"admin", "dashboard", "terminal", "logs"};
}
