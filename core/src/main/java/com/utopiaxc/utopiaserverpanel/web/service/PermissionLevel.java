package com.utopiaxc.utopiaserverpanel.web.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Permission levels for role-based access control.
 * Levels: 0 = deny, 1 = readonly, 2 = full.
 */
public enum PermissionLevel {
    DENY(0, "deny"),
    READONLY(1, "readonly"),
    FULL(2, "full");

    public static final List<String> DEFAULT_PERMISSION_KEYS = Collections.unmodifiableList(
            Arrays.asList("admin", "dashboard", "terminal", "logs"));

    private final int level;
    private final String label;

    PermissionLevel(int level, String label) {
        this.level = level;
        this.label = label;
    }

    public int getLevel() { return level; }
    public String getLabel() { return label; }

    public static PermissionLevel fromLevel(int level) {
        for (PermissionLevel p : values()) {
            if (p.level == level) return p;
        }
        return DENY;
    }

    public boolean meetsRequirement(PermissionLevel required) {
        return this.level >= required.level;
    }
}
