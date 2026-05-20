package com.utopiaxc.utopiaserverpanel.web.db;

import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import com.utopiaxc.utopiaserverpanel.auth.JwtUtil;
import com.utopiaxc.utopiaserverpanel.auth.PasswordUtil;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionLevel;
import org.apache.ibatis.session.SqlSession;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Database schema initialization, migration, and seed data.
 * Uses raw JDBC for DDL (CREATE TABLE), MyBatis mappers for seed data.
 */
public final class Schema {
    private static final int CURRENT_SCHEMA_VERSION = 2;

    private Schema() {}

    public static void initialize() {
        try (Connection conn = MyBatisFactory.getRawConnection()) {
            createTables(conn);
            migrateIfNeeded(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create database tables", e);
        }

        MyBatisFactory.doWork(session -> {
            seedRoles(session);
            seedAdminUser(session);
            ensureJwtKey(session);
        });

        UtopiaServerPanel.LOGGER.info("Database schema initialized (version {})", CURRENT_SCHEMA_VERSION);
    }

    private static void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS roles (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE NOT NULL, description TEXT DEFAULT '', is_system INTEGER DEFAULT 0, is_immutable INTEGER DEFAULT 0, created_at INTEGER NOT NULL DEFAULT (strftime('%s','now')), updated_at INTEGER NOT NULL DEFAULT (strftime('%s','now')))");
            stmt.execute("CREATE TABLE IF NOT EXISTS role_permission_levels (role_id INTEGER NOT NULL, permission_key TEXT NOT NULL, level INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (role_id, permission_key), FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE)");
            stmt.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL, role_id INTEGER NOT NULL DEFAULT 2, must_change_password INTEGER DEFAULT 0, is_active INTEGER DEFAULT 1, binding_status TEXT DEFAULT 'unbound', created_at INTEGER NOT NULL DEFAULT (strftime('%s','now')), updated_at INTEGER NOT NULL DEFAULT (strftime('%s','now')), FOREIGN KEY (role_id) REFERENCES roles(id))");
            stmt.execute("CREATE TABLE IF NOT EXISTS player_bindings (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER UNIQUE NOT NULL, player_uuid TEXT UNIQUE NOT NULL, player_name TEXT NOT NULL, bound_at INTEGER NOT NULL DEFAULT (strftime('%s','now')), FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE)");
            stmt.execute("CREATE TABLE IF NOT EXISTS binding_codes (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT UNIQUE NOT NULL, player_uuid TEXT NOT NULL, player_name TEXT NOT NULL, created_at INTEGER NOT NULL DEFAULT (strftime('%s','now')), expires_at INTEGER NOT NULL, used INTEGER DEFAULT 0)");
            stmt.execute("CREATE TABLE IF NOT EXISTS refresh_tokens (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, token_hash TEXT UNIQUE NOT NULL, expires_at INTEGER NOT NULL, created_at INTEGER NOT NULL DEFAULT (strftime('%s','now')), FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE)");
            stmt.execute("CREATE TABLE IF NOT EXISTS server_config (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            stmt.execute("CREATE TABLE IF NOT EXISTS login_history (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, login_time INTEGER NOT NULL DEFAULT (strftime('%s','now')), ip_address TEXT, user_agent TEXT, FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE)");
        }
    }

    /**
     * Migrate from old schema (v1) to new schema (v2).
     * v1 -> v2: Replace permission_definitions + role_permissions with role_permission_levels.
     */
    private static void migrateIfNeeded(Connection conn) throws SQLException {
        int currentVersion = 0;
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT value FROM server_config WHERE key = 'schema_version'");
            if (rs.next()) {
                currentVersion = Integer.parseInt(rs.getString("value"));
            }
        } catch (Exception ignored) {
            // Table might not exist yet or key might not exist
        }

        if (currentVersion < CURRENT_SCHEMA_VERSION) {
            try (Statement stmt = conn.createStatement()) {
                // Check if old tables exist and drop them
                try {
                    stmt.execute("DROP TABLE IF EXISTS role_permissions");
                    stmt.execute("DROP TABLE IF EXISTS permission_definitions");
                } catch (Exception ignored) {}

                // Set schema version
                stmt.execute("INSERT OR REPLACE INTO server_config (key, value) VALUES ('schema_version', '" + CURRENT_SCHEMA_VERSION + "')");
            }
            UtopiaServerPanel.LOGGER.info("Database migrated from v{} to v{}", currentVersion, CURRENT_SCHEMA_VERSION);
        }
    }

    private static void seedRoles(SqlSession session) {
        var roleMapper = session.getMapper(
                com.utopiaxc.utopiaserverpanel.web.db.mapper.RoleMapper.class);
        var permMapper = session.getMapper(
                com.utopiaxc.utopiaserverpanel.web.db.mapper.PermissionMapper.class);
        long now = System.currentTimeMillis() / 1000;

        // Admin role: full access to everything, immutable
        Map<String, Object> admin = new HashMap<>();
        admin.put("id", 1); admin.put("name", "admin");
        admin.put("description", "Administrator with full access");
        admin.put("isSystem", 1); admin.put("isImmutable", 1);
        admin.put("createdAt", now); admin.put("updatedAt", now);
        roleMapper.insertSeed(admin);
        // Ensure admin has all permissions at FULL level
        for (String key : PermissionLevel.PERMISSION_KEYS) {
            permMapper.setRolePermissionLevel(1, key, PermissionLevel.FULL.getLevel());
        }

        // Guest role: default for new users and unauthenticated visitors
        // Name is immutable but permissions are editable
        Map<String, Object> guest = new HashMap<>();
        guest.put("id", 2); guest.put("name", "guest");
        guest.put("description", "Default role for new and unauthenticated users");
        guest.put("isSystem", 1); guest.put("isImmutable", 0);
        guest.put("createdAt", now); guest.put("updatedAt", now);
        roleMapper.insertSeed(guest);
        // Default guest permissions: dashboard readonly, everything else deny
        if (permMapper.getRoleLevels(2).isEmpty()) {
            permMapper.setRolePermissionLevel(2, "dashboard", PermissionLevel.READONLY.getLevel());
            permMapper.setRolePermissionLevel(2, "terminal", PermissionLevel.DENY.getLevel());
            permMapper.setRolePermissionLevel(2, "logs", PermissionLevel.DENY.getLevel());
            permMapper.setRolePermissionLevel(2, "admin", PermissionLevel.DENY.getLevel());
        }
    }

    private static void seedAdminUser(SqlSession session) {
        var userMapper = session.getMapper(
                com.utopiaxc.utopiaserverpanel.web.db.mapper.UserMapper.class);
        if (userMapper.countByUsername("admin") == 0) {
            Map<String, Object> user = new HashMap<>();
            long now = System.currentTimeMillis() / 1000;
            user.put("username", "admin");
            user.put("passwordHash", PasswordUtil.hash("admin"));
            user.put("roleId", 1);
            user.put("mustChangePassword", 1);
            user.put("bindingStatus", "unbound");
            user.put("createdAt", now);
            user.put("updatedAt", now);
            userMapper.insert(user);
        }
    }

    private static void ensureJwtKey(SqlSession session) {
        var tokenMapper = session.getMapper(
                com.utopiaxc.utopiaserverpanel.web.db.mapper.TokenMapper.class);
        String key = tokenMapper.getConfig("jwt_secret");
        if (key != null && !key.isEmpty()) {
            JwtUtil.initialize(key);
        } else {
            String newKey = JwtUtil.generateNewKey();
            tokenMapper.setConfig("jwt_secret", newKey);
        }
    }
}
