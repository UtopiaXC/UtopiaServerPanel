package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.PermissionMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.RoleMapper;
import com.utopiaxc.utopiaserverpanel.web.db.model.PermissionDef;

import java.util.*;

/**
 * Dynamic level-based permission service driven by the database permissions catalog.
 */
public final class PermissionService {
    public static final int ADMIN_ROLE_ID = 1;
    public static final int GUEST_ROLE_ID = 2;
    public static final int USER_ROLE_ID = 3;

    private PermissionService() {}

    public static List<PermissionDef> listPermissionDefinitions() {
        return MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(PermissionMapper.class).listActiveDefinitions());
    }

    public static int getUserLevel(int userId, String permKey) {
        return MyBatisFactory.doWorkWithResult(session -> {
            Integer level = session.getMapper(PermissionMapper.class).getUserPermissionLevel(userId, permKey);
            return level != null ? level : 0;
        });
    }

    public static boolean meetsRequirement(int userId, String requirement) {
        if (requirement == null || requirement.isEmpty()) return true;
        String[] parts = requirement.split(":");
        if (parts.length != 2) return true;
        try {
            int minLevel = Integer.parseInt(parts[1]);
            return getUserLevel(userId, parts[0]) >= minLevel;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    public static boolean guestMeetsRequirement(String requirement) {
        if (requirement == null || requirement.isEmpty()) return true;
        String[] parts = requirement.split(":");
        if (parts.length != 2) return true;
        try {
            int minLevel = Integer.parseInt(parts[1]);
            return getRoleLevel(GUEST_ROLE_ID, parts[0]) >= minLevel;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    public static Map<String, Integer> getUserPermissionLevels(int userId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            List<Map<String, Object>> rows = session.getMapper(PermissionMapper.class).getUserPermissionLevels(userId);
            return rowsToLevelMap(rows);
        });
    }

    public static Map<String, Integer> getRoleLevels(int roleId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            List<Map<String, Object>> rows = session.getMapper(PermissionMapper.class).getRoleLevels(roleId);
            return rowsToLevelMap(rows);
        });
    }

    public static Map<String, Integer> getGuestPermissionLevels() {
        return getRoleLevels(GUEST_ROLE_ID);
    }

    public static int getRoleLevel(int roleId, String permKey) {
        return getRoleLevels(roleId).getOrDefault(permKey, 0);
    }

    public static void setRoleLevels(int roleId, Map<String, Integer> requestedLevels) {
        MyBatisFactory.doWork(session -> {
            PermissionMapper permMapper = session.getMapper(PermissionMapper.class);
            RoleMapper roleMapper = session.getMapper(RoleMapper.class);
            List<PermissionDef> defs = permMapper.listActiveDefinitions();

            permMapper.deleteByRoleId(roleId);
            for (PermissionDef def : defs) {
                int lvl = requestedLevels.getOrDefault(def.getPermKey(), 0);
                if (lvl < 0) lvl = 0;
                if (lvl > 2) lvl = 2;
                permMapper.setRolePermissionLevel(roleId, def.getPermKey(), lvl);
            }
            roleMapper.touchUpdatedAt(roleId, System.currentTimeMillis() / 1000L);
        });
    }

    private static Map<String, Integer> rowsToLevelMap(List<Map<String, Object>> rows) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Map<String, Object> entry : rows) {
            String key = (String) entry.get("permissionKey");
            Number lvl = (Number) entry.get("level");
            if (key != null) {
                result.put(key, lvl != null ? lvl.intValue() : 0);
            }
        }
        return result;
    }
}
