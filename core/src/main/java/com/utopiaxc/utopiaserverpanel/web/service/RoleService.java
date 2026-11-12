package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.logging.PanelLog;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.PermissionMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.RoleMapper;
import com.utopiaxc.utopiaserverpanel.web.db.model.PermissionDef;
import com.utopiaxc.utopiaserverpanel.web.db.model.RoleInfo;

import java.util.*;

/**
 * Role management service with dynamic 3-level extensible permissions.
 */
public final class RoleService {

    private RoleService() {}

    public static List<RoleInfo> listRoles() {
        return MyBatisFactory.doWorkWithResult(session -> {
            RoleMapper roleMapper = session.getMapper(RoleMapper.class);
            PermissionMapper permMapper = session.getMapper(PermissionMapper.class);
            List<RoleInfo> roles = roleMapper.findAll();
            List<PermissionDef> defs = permMapper.listActiveDefinitions();
            List<Map<String, Object>> allLevels = permMapper.getAllRoleLevels();

            Map<Integer, Map<String, Integer>> levelsByRole = new HashMap<>();
            for (Map<String, Object> row : allLevels) {
                int rid = ((Number) row.get("roleId")).intValue();
                String key = (String) row.get("permKey");
                int lvl = ((Number) row.get("level")).intValue();
                levelsByRole.computeIfAbsent(rid, k -> new LinkedHashMap<>()).put(key, lvl);
            }

            for (RoleInfo role : roles) {
                Map<String, Integer> map = new LinkedHashMap<>();
                Map<String, Integer> saved = levelsByRole.getOrDefault(role.getId(), Collections.<String, Integer>emptyMap());
                for (PermissionDef def : defs) {
                    map.put(def.getPermKey(), saved.getOrDefault(def.getPermKey(), 0));
                }
                role.setPermissionLevels(map);
            }
            return roles;
        });
    }

    public static RoleInfo getRole(int roleId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            RoleInfo role = session.getMapper(RoleMapper.class).findById(roleId);
            if (role == null) return null;
            role.setPermissionLevels(PermissionService.getRoleLevels(roleId));
            return role;
        });
    }

    public static final class CreateRoleResult {
        private final boolean success;
        private final String errorCode;
        private final String message;
        private final RoleInfo role;

        public CreateRoleResult(boolean success, String errorCode, String message, RoleInfo role) {
            this.success = success;
            this.errorCode = errorCode;
            this.message = message;
            this.role = role;
        }

        public boolean success() { return success; }
        public String errorCode() { return errorCode; }
        public String message() { return message; }
        public RoleInfo role() { return role; }
    }

    public static CreateRoleResult createRole(String name, String displayName, String description, Map<String, Integer> permissionLevels) {
        if (name == null || !name.trim().matches("^[a-zA-Z0-9_-]{2,32}$")) {
            return new CreateRoleResult(false, "INVALID_ROLE_NAME", "Role identifier must be 2-32 letters, numbers, underscores or hyphens", null);
        }
        String cleanName = name.trim().toLowerCase(Locale.ROOT);
        String cleanDisplay = (displayName != null && !displayName.trim().isEmpty()) ? displayName.trim() : name.trim();
        String cleanDesc = description != null ? description.trim() : "";

        return MyBatisFactory.doWorkWithResult(session -> {
            RoleMapper roleMapper = session.getMapper(RoleMapper.class);
            if (roleMapper.findByName(cleanName) != null) {
                return new CreateRoleResult(false, "ROLE_EXISTS", "Role name already exists", null);
            }

            long now = System.currentTimeMillis() / 1000L;
            RoleInfo role = new RoleInfo();
            role.setName(cleanName);
            role.setDisplayName(cleanDisplay);
            role.setDescription(cleanDesc);
            role.setCreatedAt(now);
            role.setUpdatedAt(now);
            roleMapper.insert(role);

            PermissionMapper permMapper = session.getMapper(PermissionMapper.class);
            List<PermissionDef> defs = permMapper.listActiveDefinitions();
            for (PermissionDef def : defs) {
                int lvl = permissionLevels != null ? permissionLevels.getOrDefault(def.getPermKey(), 0) : 0;
                lvl = Math.max(0, Math.min(2, lvl));
                permMapper.setRolePermissionLevel(role.getId(), def.getPermKey(), lvl);
            }

            RoleInfo created = getRole(role.getId());
            PanelLog.get().info("Created role '{}' (id={})", cleanName, Integer.valueOf(role.getId()));
            return new CreateRoleResult(true, null, "Role created", created);
        });
    }

    public enum UpdateRoleResult { SUCCESS, NOT_FOUND, IMMUTABLE, DUPLICATE_NAME }

    public static final class UpdateResult {
        private final UpdateRoleResult status;
        private final RoleInfo role;

        public UpdateResult(UpdateRoleResult status, RoleInfo role) {
            this.status = status;
            this.role = role;
        }

        public UpdateRoleResult status() { return status; }
        public RoleInfo role() { return role; }
    }

    public static UpdateResult updateRole(int roleId, String name, String displayName, String description, Map<String, Integer> permissionLevels) {
        RoleInfo existing = MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(RoleMapper.class).findById(roleId));
        if (existing == null) return new UpdateResult(UpdateRoleResult.NOT_FOUND, null);
        if (existing.isImmutable()) return new UpdateResult(UpdateRoleResult.IMMUTABLE, null);

        String nextName = existing.getName();
        if (!existing.isSystemRole() && name != null && !name.trim().isEmpty()) {
            String candidate = name.trim().toLowerCase(Locale.ROOT);
            RoleInfo byName = MyBatisFactory.doWorkWithResult(session ->
                    session.getMapper(RoleMapper.class).findByName(candidate));
            if (byName != null && byName.getId() != roleId) {
                return new UpdateResult(UpdateRoleResult.DUPLICATE_NAME, null);
            }
            nextName = candidate;
        }

        String nextDisplay = (displayName != null && !displayName.trim().isEmpty()) ? displayName.trim() : existing.getDisplayName();
        String nextDesc = description != null ? description.trim() : existing.getDescription();

        final String finalName = nextName;
        MyBatisFactory.doWork(session -> {
            RoleMapper mapper = session.getMapper(RoleMapper.class);
            RoleInfo updated = new RoleInfo();
            updated.setId(roleId);
            updated.setName(finalName);
            updated.setDisplayName(nextDisplay);
            updated.setDescription(nextDesc);
            updated.setUpdatedAt(System.currentTimeMillis() / 1000L);
            mapper.update(updated);
        });

        if (permissionLevels != null) {
            PermissionService.setRoleLevels(roleId, permissionLevels);
        }

        return new UpdateResult(UpdateRoleResult.SUCCESS, getRole(roleId));
    }

    public enum DeleteRoleResult { SUCCESS, NOT_FOUND, SYSTEM_ROLE, HAS_USERS }

    public static DeleteRoleResult deleteRole(int roleId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            RoleMapper roleMapper = session.getMapper(RoleMapper.class);
            RoleInfo role = roleMapper.findById(roleId);
            if (role == null) return DeleteRoleResult.NOT_FOUND;
            if (role.isSystemRole()) return DeleteRoleResult.SYSTEM_ROLE;
            if (roleMapper.countUsersWithRole(roleId) > 0) return DeleteRoleResult.HAS_USERS;

            roleMapper.deleteNonSystem(roleId);
            return DeleteRoleResult.SUCCESS;
        });
    }
}
