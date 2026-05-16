package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.PermissionMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.RoleMapper;

import java.util.*;

/**
 * Permission service using MyBatis mappers.
 */
public final class PermissionService {

    private PermissionService() {}

    public static Map<String, Map<String, List<Map<String, String>>>> getAllPermissions() {
        return MyBatisFactory.doWorkWithResult(session -> {
            var mapper = session.getMapper(PermissionMapper.class);
            List<Map<String, Object>> defs = mapper.getAllDefinitions();

            Map<String, Map<String, List<Map<String, String>>>> result = new LinkedHashMap<>();
            for (Map<String, Object> d : defs) {
                String module = (String) d.get("module");
                String group = (String) d.get("groupName");
                Map<String, String> perm = new LinkedHashMap<>();
                perm.put("key", (String) d.get("key"));
                perm.put("type", (String) d.get("type"));
                perm.put("description", (String) d.get("description"));
                result.computeIfAbsent(module, k -> new LinkedHashMap<>())
                        .computeIfAbsent(group, k -> new ArrayList<>())
                        .add(perm);
            }
            return result;
        });
    }

    public static Set<String> getUserPermissions(int userId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            var mapper = session.getMapper(PermissionMapper.class);
            return new HashSet<>(mapper.getKeysByUserId(userId));
        });
    }

    public static boolean hasPermission(int userId, String permissionKey) {
        if (permissionKey == null || permissionKey.isEmpty()) return true;
        return MyBatisFactory.doWorkWithResult(session -> {
            var mapper = session.getMapper(PermissionMapper.class);
            return mapper.countUserPermission(userId, permissionKey) > 0;
        });
    }

    public static Set<String> getRolePermissions(int roleId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            var mapper = session.getMapper(PermissionMapper.class);
            return new HashSet<>(mapper.getKeysByRoleId(roleId));
        });
    }

    public static boolean setRolePermissions(int roleId, Set<String> permissionKeys) {
        return MyBatisFactory.doWorkWithResult(session -> {
            var mapper = session.getMapper(PermissionMapper.class);
            mapper.deleteByRoleId(roleId);
            for (String key : permissionKeys) {
                mapper.insertRolePermission(roleId, key);
            }
            Map<String, Object> ts = new HashMap<>();
            ts.put("id", roleId);
            ts.put("updatedAt", System.currentTimeMillis() / 1000);
            mapper.updateRoleTimestamp(ts);
            return true;
        });
    }

    public static List<String> getAllPermissionKeys() {
        return MyBatisFactory.doWorkWithResult(session -> {
            var mapper = session.getMapper(PermissionMapper.class);
            return mapper.getAllKeys();
        });
    }

    public static Map<String, Map<String, List<Map<String, Object>>>> getRolePermissionsGrouped(int roleId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            var permMapper = session.getMapper(PermissionMapper.class);
            Set<String> granted = new HashSet<>(permMapper.getKeysByRoleId(roleId));
            List<Map<String, Object>> defs = permMapper.getAllDefinitions();

            Map<String, Map<String, List<Map<String, Object>>>> result = new LinkedHashMap<>();
            for (Map<String, Object> d : defs) {
                String module = (String) d.get("module");
                String group = (String) d.get("groupName");
                String key = (String) d.get("key");
                Map<String, Object> perm = new LinkedHashMap<>();
                perm.put("key", key);
                perm.put("type", d.get("type"));
                perm.put("description", d.get("description"));
                perm.put("granted", granted.contains(key));
                result.computeIfAbsent(module, k -> new LinkedHashMap<>())
                        .computeIfAbsent(group, k -> new ArrayList<>())
                        .add(perm);
            }
            return result;
        });
    }
}
