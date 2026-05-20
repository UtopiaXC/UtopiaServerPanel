package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.PermissionMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.RoleMapper;

import java.util.*;

/**
 * Role management service using MyBatis mappers.
 * Updated for level-based permission system.
 */
public final class RoleService {

    private RoleService() {}

    public static List<Map<String, Object>> listRoles() {
        return MyBatisFactory.doWorkWithResult(session -> {
            var mapper = session.getMapper(RoleMapper.class);
            List<Map<String, Object>> roles = mapper.listAll();
            var permMapper = session.getMapper(PermissionMapper.class);
            for (Map<String, Object> r : roles) {
                int roleId = toInt(r.get("id"));
                Map<String, Integer> levels = PermissionService.getRoleLevels(roleId);
                r.put("permissionLevels", levels);
            }
            return roles;
        });
    }

    public static Map<String, Object> getRole(int roleId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            var mapper = session.getMapper(RoleMapper.class);
            Map<String, Object> role = mapper.findById(roleId);
            if (role == null) return null;
            role.put("userCount", mapper.countUsersByRoleId(roleId));
            role.put("permissionLevels", PermissionService.getRoleLevels(roleId));
            return role;
        });
    }

    public static int createRole(String name, String description, Map<String, Integer> permissionLevels) {
        if (name == null || name.isBlank()) return -1;
        final String finalName = name.trim();

        return MyBatisFactory.doWorkWithResult(session -> {
            var mapper = session.getMapper(RoleMapper.class);
            if (mapper.countByName(finalName) > 0) return -1;

            long now = System.currentTimeMillis() / 1000;
            Map<String, Object> role = new HashMap<>();
            role.put("name", finalName);
            role.put("description", description != null ? description : "");
            role.put("createdAt", now);
            role.put("updatedAt", now);
            mapper.insert(role);

            int roleId = toInt(role.get("id"));
            if (permissionLevels != null && !permissionLevels.isEmpty()) {
                PermissionService.setRoleLevels(roleId, permissionLevels);
            } else {
                // Default: all permissions to deny
                Map<String, Integer> defaults = new LinkedHashMap<>();
                for (String key : PermissionLevel.PERMISSION_KEYS) {
                    defaults.put(key, 0);
                }
                PermissionService.setRoleLevels(roleId, defaults);
            }
            UtopiaServerPanel.LOGGER.info("Role created: {} (id={})", finalName, roleId);
            return roleId;
        });
    }

    public static UpdateRoleResult updateRole(int roleId, String name, String description, Map<String, Integer> permissionLevels) {
        return MyBatisFactory.doWorkWithResult(session -> {
            var mapper = session.getMapper(RoleMapper.class);
            Map<String, Object> existing = mapper.findById(roleId);
            if (existing == null) return UpdateRoleResult.NOT_FOUND;

            boolean isImmutable = toInt(existing.get("isImmutable")) == 1;
            boolean isSystem = toInt(existing.get("isSystem")) == 1;

            // Admin role (immutable) cannot have permissions changed
            if (isImmutable && permissionLevels != null) {
                return UpdateRoleResult.IMMUTABLE;
            }

            long now = System.currentTimeMillis() / 1000;
            Map<String, Object> params = new HashMap<>();
            params.put("id", roleId);
            
            // System roles (admin, guest) cannot have their name changed
            if (isSystem) {
                params.put("name", existing.get("name")); // Keep existing name
            } else {
                params.put("name", name != null && !name.isBlank() ? name.trim() : existing.get("name"));
            }
            
            params.put("description", description);
            params.put("updatedAt", now);
            mapper.update(params);

            if (permissionLevels != null) {
                PermissionService.setRoleLevels(roleId, permissionLevels);
            }
            return UpdateRoleResult.SUCCESS;
        });
    }

    public static DeleteRoleResult deleteRole(int roleId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            var mapper = session.getMapper(RoleMapper.class);
            Map<String, Object> role = mapper.findById(roleId);
            if (role == null) return DeleteRoleResult.NOT_FOUND;
            if (toInt(role.get("isSystem")) == 1) return DeleteRoleResult.SYSTEM_ROLE;
            if (mapper.countUsersByRoleId(roleId) > 0) return DeleteRoleResult.HAS_USERS;

            mapper.delete(roleId);
            UtopiaServerPanel.LOGGER.info("Role deleted: id={}", roleId);
            return DeleteRoleResult.SUCCESS;
        });
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) return Integer.parseInt(s);
        return 0;
    }

    public enum UpdateRoleResult { SUCCESS, NOT_FOUND, IMMUTABLE, ERROR }
    public enum DeleteRoleResult { SUCCESS, NOT_FOUND, SYSTEM_ROLE, HAS_USERS, ERROR }
}
