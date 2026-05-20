package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.PermissionMapper;

import java.util.*;

/**
 * Permission service for the level-based permission system.
 * <p>
 * 4 permission categories: admin, dashboard, terminal, logs.
 * 3 levels: 0 (deny), 1 (readonly), 2 (full).
 * </p>
 */
public final class PermissionService {
    /** The guest role ID, used for unauthenticated visitors. */
    public static final int GUEST_ROLE_ID = 2;

    private PermissionService() {}

    /**
     * Get the permission level for a user on a specific permission key.
     * @return 0 (deny), 1 (readonly), or 2 (full). Returns 0 if not found.
     */
    public static int getUserLevel(int userId, String permKey) {
        return MyBatisFactory.doWorkWithResult(session -> {
            var mapper = session.getMapper(PermissionMapper.class);
            Integer level = mapper.getUserPermissionLevel(userId, permKey);
            return level != null ? level : 0;
        });
    }

    /**
     * Check if a user has at least readonly access to a permission.
     */
    public static boolean hasReadAccess(int userId, String permKey) {
        return getUserLevel(userId, permKey) >= PermissionLevel.READONLY.getLevel();
    }

    /**
     * Check if a user has full access to a permission.
     */
    public static boolean hasFullAccess(int userId, String permKey) {
        return getUserLevel(userId, permKey) >= PermissionLevel.FULL.getLevel();
    }

    /**
     * Check if a user meets a minimum permission level requirement.
     * The requirement string is in format "key:level" (e.g., "admin:1" or "terminal:2").
     */
    public static boolean meetsRequirement(int userId, String requirement) {
        if (requirement == null || requirement.isEmpty()) return true;
        String[] parts = requirement.split(":");
        if (parts.length != 2) return true;
        String permKey = parts[0];
        int minLevel;
        try {
            minLevel = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return true;
        }
        return getUserLevel(userId, permKey) >= minLevel;
    }

    /**
     * Check if the guest role meets a minimum permission level requirement.
     */
    public static boolean guestMeetsRequirement(String requirement) {
        if (requirement == null || requirement.isEmpty()) return true;
        String[] parts = requirement.split(":");
        if (parts.length != 2) return true;
        String permKey = parts[0];
        int minLevel;
        try {
            minLevel = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return true;
        }
        return getRoleLevel(GUEST_ROLE_ID, permKey) >= minLevel;
    }

    /**
     * Get all permission levels for a user as a map of {permKey -> level}.
     */
    public static Map<String, Integer> getUserPermissionLevels(int userId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            var mapper = session.getMapper(PermissionMapper.class);
            List<Map<String, Object>> levels = mapper.getUserPermissionLevels(userId);
            Map<String, Integer> result = new LinkedHashMap<>();
            for (Map<String, Object> entry : levels) {
                result.put((String) entry.get("permissionKey"), ((Number) entry.get("level")).intValue());
            }
            return result;
        });
    }

    /**
     * Get all permission levels for a role as a map of {permKey -> level}.
     */
    public static Map<String, Integer> getRoleLevels(int roleId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            var mapper = session.getMapper(PermissionMapper.class);
            List<Map<String, Object>> levels = mapper.getRoleLevels(roleId);
            Map<String, Integer> result = new LinkedHashMap<>();
            for (Map<String, Object> entry : levels) {
                result.put((String) entry.get("permissionKey"), ((Number) entry.get("level")).intValue());
            }
            return result;
        });
    }

    /**
     * Get the guest role's permission levels.
     */
    public static Map<String, Integer> getGuestPermissionLevels() {
        return getRoleLevels(GUEST_ROLE_ID);
    }

    /**
     * Get a specific role's permission level for a key.
     */
    public static int getRoleLevel(int roleId, String permKey) {
        Map<String, Integer> levels = getRoleLevels(roleId);
        return levels.getOrDefault(permKey, 0);
    }

    /**
     * Set all permission levels for a role.
     * @param roleId the role ID
     * @param levels map of {permKey -> level}
     */
    public static boolean setRoleLevels(int roleId, Map<String, Integer> levels) {
        return MyBatisFactory.doWorkWithResult(session -> {
            var mapper = session.getMapper(PermissionMapper.class);
            mapper.deleteByRoleId(roleId);
            for (var entry : levels.entrySet()) {
                mapper.setRolePermissionLevel(roleId, entry.getKey(), entry.getValue());
            }
            Map<String, Object> ts = new HashMap<>();
            ts.put("id", roleId);
            ts.put("updatedAt", System.currentTimeMillis() / 1000);
            mapper.updateRoleTimestamp(ts);
            return true;
        });
    }
}
