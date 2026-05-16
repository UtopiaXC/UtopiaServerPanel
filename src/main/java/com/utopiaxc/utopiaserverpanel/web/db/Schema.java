package com.utopiaxc.utopiaserverpanel.web.db;

import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import com.utopiaxc.utopiaserverpanel.auth.JwtUtil;
import com.utopiaxc.utopiaserverpanel.auth.PasswordUtil;
import org.apache.ibatis.session.SqlSession;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * Database schema initialization and seed data.
 * Uses raw JDBC for DDL (CREATE TABLE), MyBatis mappers for seed data.
 */
public final class Schema {

    private Schema() {}

    public static void initialize() {
        try (Connection conn = MyBatisFactory.getRawConnection()) {
            createTables(conn);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create database tables", e);
        }

        MyBatisFactory.doWork(session -> {
            seedPermissionDefinitions(session);
            seedRoles(session);
            seedAdminUser(session);
            ensureJwtKey(session);
        });

        UtopiaServerPanel.LOGGER.info("Database schema initialized");
    }

    private static void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS roles (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE NOT NULL, description TEXT DEFAULT '', is_system INTEGER DEFAULT 0, is_immutable INTEGER DEFAULT 0, created_at INTEGER NOT NULL DEFAULT (strftime('%s','now')), updated_at INTEGER NOT NULL DEFAULT (strftime('%s','now')))");
            stmt.execute("CREATE TABLE IF NOT EXISTS permission_definitions (id INTEGER PRIMARY KEY AUTOINCREMENT, permission_key TEXT UNIQUE NOT NULL, module TEXT NOT NULL, group_name TEXT NOT NULL, permission_type TEXT NOT NULL, description TEXT NOT NULL)");
            stmt.execute("CREATE TABLE IF NOT EXISTS role_permissions (role_id INTEGER NOT NULL, permission_key TEXT NOT NULL, PRIMARY KEY (role_id, permission_key), FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE)");
            stmt.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE NOT NULL, password_hash TEXT NOT NULL, role_id INTEGER NOT NULL DEFAULT 2, must_change_password INTEGER DEFAULT 0, is_active INTEGER DEFAULT 1, binding_status TEXT DEFAULT 'unbound', created_at INTEGER NOT NULL DEFAULT (strftime('%s','now')), updated_at INTEGER NOT NULL DEFAULT (strftime('%s','now')), FOREIGN KEY (role_id) REFERENCES roles(id))");
            stmt.execute("CREATE TABLE IF NOT EXISTS player_bindings (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER UNIQUE NOT NULL, player_uuid TEXT UNIQUE NOT NULL, player_name TEXT NOT NULL, bound_at INTEGER NOT NULL DEFAULT (strftime('%s','now')), FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE)");
            stmt.execute("CREATE TABLE IF NOT EXISTS binding_codes (id INTEGER PRIMARY KEY AUTOINCREMENT, code TEXT UNIQUE NOT NULL, player_uuid TEXT NOT NULL, player_name TEXT NOT NULL, created_at INTEGER NOT NULL DEFAULT (strftime('%s','now')), expires_at INTEGER NOT NULL, used INTEGER DEFAULT 0)");
            stmt.execute("CREATE TABLE IF NOT EXISTS refresh_tokens (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, token_hash TEXT UNIQUE NOT NULL, expires_at INTEGER NOT NULL, created_at INTEGER NOT NULL DEFAULT (strftime('%s','now')), FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE)");
            stmt.execute("CREATE TABLE IF NOT EXISTS server_config (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }
    }

    private static void seedPermissionDefinitions(SqlSession session) {
        var mapper = session.getMapper(
                com.utopiaxc.utopiaserverpanel.web.db.mapper.PermissionMapper.class);
        String[][] defs = {
                {"dashboard.status.read", "dashboard", "status", "read", "View server status"},
                {"dashboard.config.read", "dashboard", "config", "read", "View server configuration"},
                {"dashboard.config.edit", "dashboard", "config", "edit", "Modify server configuration"},
                {"terminal.logs.read", "terminal", "logs", "read", "View console logs"},
                {"terminal.commands.execute", "terminal", "commands", "execute", "Execute server commands"},
                {"terminal.commands.completions", "terminal", "commands", "read", "Get command tab completions"},
                {"admin.users.read", "admin", "users", "read", "View user list"},
                {"admin.users.edit", "admin", "users", "edit", "Create, update, delete users"},
                {"admin.roles.read", "admin", "roles", "read", "View role list and permissions"},
                {"admin.roles.edit", "admin", "roles", "edit", "Create, update, delete roles"},
                {"auth.profile.read", "auth", "profile", "read", "View own profile"},
                {"auth.profile.edit", "auth", "profile", "edit", "Change own password and settings"},
                {"auth.binding.manage", "auth", "binding", "edit", "Manage player binding"},
        };
        for (String[] d : defs) {
            Map<String, Object> p = new HashMap<>();
            p.put("key", d[0]); p.put("module", d[1]); p.put("groupName", d[2]);
            p.put("type", d[3]); p.put("description", d[4]);
            mapper.insertDefinition(p);
        }
    }

    private static void seedRoles(SqlSession session) {
        var roleMapper = session.getMapper(
                com.utopiaxc.utopiaserverpanel.web.db.mapper.RoleMapper.class);
        var permMapper = session.getMapper(
                com.utopiaxc.utopiaserverpanel.web.db.mapper.PermissionMapper.class);
        long now = System.currentTimeMillis() / 1000;

        Map<String, Object> admin = new HashMap<>();
        admin.put("id", 1); admin.put("name", "admin");
        admin.put("description", "Administrator with full access");
        admin.put("isSystem", 1); admin.put("isImmutable", 1);
        admin.put("createdAt", now); admin.put("updatedAt", now);
        roleMapper.insertSeed(admin);
        permMapper.grantAllToRole(1);

        Map<String, Object> guest = new HashMap<>();
        guest.put("id", 2); guest.put("name", "guest");
        guest.put("description", "Default role for new and unauthenticated users");
        guest.put("isSystem", 1); guest.put("isImmutable", 0);
        guest.put("createdAt", now); guest.put("updatedAt", now);
        roleMapper.insertSeed(guest);
        if (permMapper.getKeysByRoleId(2).isEmpty()) {
            permMapper.insertRolePermission(2, "dashboard.status.read");
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
