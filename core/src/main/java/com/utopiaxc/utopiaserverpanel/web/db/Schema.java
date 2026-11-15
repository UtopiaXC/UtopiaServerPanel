package com.utopiaxc.utopiaserverpanel.web.db;

import com.utopiaxc.utopiaserverpanel.auth.CryptoUtil;
import com.utopiaxc.utopiaserverpanel.auth.JwtUtil;
import com.utopiaxc.utopiaserverpanel.logging.PanelLog;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.PermissionMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.RoleMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.SettingsMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.UserMapper;
import com.utopiaxc.utopiaserverpanel.web.db.model.PermissionDef;
import com.utopiaxc.utopiaserverpanel.web.db.model.RoleInfo;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionLevel;
import org.apache.ibatis.session.SqlSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

/**
 * Clean 3NF SQLite schema initializer and default metadata seeder.
 * Drops legacy pre-refactor tables automatically if an incompatible legacy DB is detected.
 */
public final class Schema {
    private static final int SCHEMA_VERSION = 121;

    private Schema() {}

    public static void initialize() {
        try (Connection conn = MyBatisFactory.getRawConnection()) {
            rebuildIfLegacySchema(conn);
            migratePlayerBindingsToMulti(conn);
            createTables(conn);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize database schema", e);
        }

        MyBatisFactory.doWork(session -> {
            seedPermissions(session);
            seedRoles(session);
            seedDefaultSettings(session);
            ensureCryptoKeys(session);
        });

        PanelLog.get().info("3NF SQLite schema initialized (v{})", Integer.valueOf(SCHEMA_VERSION));
    }

    private static void rebuildIfLegacySchema(Connection conn) throws SQLException {
        boolean hasSystemSettings = tableExists(conn, "system_settings");
        boolean hasLegacyUsers = tableExists(conn, "users");
        if (hasLegacyUsers && !hasSystemSettings) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("PRAGMA foreign_keys=OFF");
                List<String> legacyTables = Arrays.asList(
                        "role_permission_levels", "role_permissions", "permission_definitions",
                        "player_bindings", "binding_codes", "refresh_tokens", "login_history",
                        "server_config", "users", "roles", "player_events", "perf_logs", "server_lifecycle"
                );
                for (String t : legacyTables) {
                    stmt.execute("DROP TABLE IF EXISTS " + t);
                }
                stmt.execute("PRAGMA foreign_keys=ON");
            }
            PanelLog.get().info("Reset legacy schema to clean 3NF schema");
        }
    }

    private static boolean tableExists(Connection conn, String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static void migratePlayerBindingsToMulti(Connection conn) throws SQLException {
        if (!tableExists(conn, "player_bindings") || columnExists(conn, "player_bindings", "is_primary")) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys=OFF");
            stmt.execute(
                    "CREATE TABLE player_bindings_new ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "user_id INTEGER NOT NULL,"
                    + "player_uuid TEXT UNIQUE NOT NULL,"
                    + "is_primary INTEGER NOT NULL DEFAULT 0,"
                    + "bound_at INTEGER NOT NULL,"
                    + "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,"
                    + "FOREIGN KEY (player_uuid) REFERENCES mc_players(player_uuid)"
                    + ")");
            stmt.execute(
                    "INSERT INTO player_bindings_new (id, user_id, player_uuid, is_primary, bound_at) "
                    + "SELECT id, user_id, player_uuid, 1, bound_at FROM player_bindings");
            stmt.execute("DROP TABLE player_bindings");
            stmt.execute("ALTER TABLE player_bindings_new RENAME TO player_bindings");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_bindings_user ON player_bindings(user_id, is_primary DESC, bound_at ASC)");
            stmt.execute("PRAGMA foreign_keys=ON");
        }
        PanelLog.get().info("Migrated player_bindings table to multi-player schema");
    }

    private static void createTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Extensible permission definitions catalog
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS permissions ("
                    + "perm_key TEXT PRIMARY KEY,"
                    + "category TEXT NOT NULL DEFAULT 'panel',"
                    + "sort_order INTEGER NOT NULL DEFAULT 0,"
                    + "is_active INTEGER NOT NULL DEFAULT 1"
                    + ")");

            // Roles
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS roles ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "name TEXT UNIQUE NOT NULL,"
                    + "display_name TEXT NOT NULL,"
                    + "description TEXT NOT NULL DEFAULT '',"
                    + "is_system INTEGER NOT NULL DEFAULT 0,"
                    + "is_immutable INTEGER NOT NULL DEFAULT 0,"
                    + "created_at INTEGER NOT NULL,"
                    + "updated_at INTEGER NOT NULL"
                    + ")");

            // Role <-> Permission level matrix
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS role_permissions ("
                    + "role_id INTEGER NOT NULL,"
                    + "perm_key TEXT NOT NULL,"
                    + "level INTEGER NOT NULL DEFAULT 0 CHECK (level IN (0, 1, 2)),"
                    + "PRIMARY KEY (role_id, perm_key),"
                    + "FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,"
                    + "FOREIGN KEY (perm_key) REFERENCES permissions(perm_key) ON DELETE CASCADE"
                    + ")");

            // Canonical MC players (3NF: avoids transitive uuid -> name across tables)
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS mc_players ("
                    + "player_uuid TEXT PRIMARY KEY,"
                    + "player_name TEXT NOT NULL,"
                    + "updated_at INTEGER NOT NULL"
                    + ")");

            // Users
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS users ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "username TEXT UNIQUE NOT NULL,"
                    + "nickname TEXT NOT NULL DEFAULT '',"
                    + "email TEXT UNIQUE,"
                    + "password_hash TEXT NOT NULL,"
                    + "role_id INTEGER NOT NULL,"
                    + "status TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'frozen', 'banned', 'inactive')),"
                    + "must_change_password INTEGER NOT NULL DEFAULT 0,"
                    + "require_2fa INTEGER NOT NULL DEFAULT 0,"
                    + "failed_login_attempts INTEGER NOT NULL DEFAULT 0,"
                    + "lockout_until INTEGER,"
                    + "token_version INTEGER NOT NULL DEFAULT 1,"
                    + "locale TEXT NOT NULL DEFAULT 'zh-CN',"
                    + "created_at INTEGER NOT NULL,"
                    + "updated_at INTEGER NOT NULL,"
                    + "FOREIGN KEY (role_id) REFERENCES roles(id)"
                    + ")");

            // Extended user panel profile & public page privacy settings (1:1 optional extension)
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS user_profiles ("
                    + "user_id INTEGER PRIMARY KEY,"
                    + "avatar_data TEXT NOT NULL DEFAULT '',"
                    + "gender TEXT NOT NULL DEFAULT 'unspecified',"
                    + "birthday TEXT NOT NULL DEFAULT '',"
                    + "bio TEXT NOT NULL DEFAULT '',"
                    + "social_accounts TEXT NOT NULL DEFAULT '[]',"
                    + "privacy_json TEXT NOT NULL DEFAULT '{}',"
                    + "updated_at INTEGER NOT NULL DEFAULT 0,"
                    + "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"
                    + ")");

            // Player <-> User bindings (1:N; one panel user may bind multiple MC players)
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS player_bindings ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "user_id INTEGER NOT NULL,"
                    + "player_uuid TEXT UNIQUE NOT NULL,"
                    + "is_primary INTEGER NOT NULL DEFAULT 0,"
                    + "bound_at INTEGER NOT NULL,"
                    + "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,"
                    + "FOREIGN KEY (player_uuid) REFERENCES mc_players(player_uuid)"
                    + ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_bindings_user ON player_bindings(user_id, is_primary DESC, bound_at ASC)");

            // One-time binding codes
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS binding_codes ("
                    + "code TEXT PRIMARY KEY,"
                    + "player_uuid TEXT NOT NULL,"
                    + "created_at INTEGER NOT NULL,"
                    + "expires_at INTEGER NOT NULL,"
                    + "consumed_at INTEGER,"
                    + "FOREIGN KEY (player_uuid) REFERENCES mc_players(player_uuid)"
                    + ")");

            // TOTP 2FA (1:1 optional extension)
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS user_totp ("
                    + "user_id INTEGER PRIMARY KEY,"
                    + "secret_encrypted TEXT NOT NULL DEFAULT '',"
                    + "pending_secret_encrypted TEXT NOT NULL DEFAULT '',"
                    + "enabled INTEGER NOT NULL DEFAULT 0,"
                    + "verified_at INTEGER,"
                    + "updated_at INTEGER NOT NULL,"
                    + "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"
                    + ")");

            // WebAuthn Passkeys (1:N per user)
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS user_passkeys ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "user_id INTEGER NOT NULL,"
                    + "credential_id TEXT UNIQUE NOT NULL,"
                    + "name TEXT NOT NULL,"
                    + "public_key_cose TEXT NOT NULL,"
                    + "algorithm INTEGER NOT NULL,"
                    + "sign_count INTEGER NOT NULL DEFAULT 0,"
                    + "transports TEXT NOT NULL DEFAULT '[]',"
                    + "created_at INTEGER NOT NULL,"
                    + "last_used_at INTEGER,"
                    + "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"
                    + ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_passkeys_user ON user_passkeys(user_id)");

            // Recovery & Activation tokens
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS recovery_tokens ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "user_id INTEGER NOT NULL,"
                    + "token_hash TEXT UNIQUE NOT NULL,"
                    + "token_type TEXT NOT NULL CHECK (token_type IN ('activation', 'password', '2fa')),"
                    + "source TEXT NOT NULL DEFAULT 'email' CHECK (source IN ('email', 'ingame')),"
                    + "expires_at INTEGER NOT NULL,"
                    + "used_at INTEGER,"
                    + "created_at INTEGER NOT NULL,"
                    + "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"
                    + ")");

            // User sessions (refresh & live session tracking)
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS user_sessions ("
                    + "session_id TEXT PRIMARY KEY,"
                    + "user_id INTEGER NOT NULL,"
                    + "token_hash TEXT UNIQUE NOT NULL,"
                    + "token_version INTEGER NOT NULL DEFAULT 1,"
                    + "ip_address TEXT NOT NULL DEFAULT '',"
                    + "user_agent TEXT NOT NULL DEFAULT '',"
                    + "remember_me INTEGER NOT NULL DEFAULT 0,"
                    + "created_at INTEGER NOT NULL,"
                    + "last_seen_at INTEGER NOT NULL,"
                    + "expires_at INTEGER NOT NULL,"
                    + "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"
                    + ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_user_sessions_user ON user_sessions(user_id)");

            // Personal API Access Tokens
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS api_tokens ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "user_id INTEGER NOT NULL,"
                    + "name TEXT NOT NULL,"
                    + "prefix TEXT NOT NULL,"
                    + "token_hash TEXT UNIQUE NOT NULL,"
                    + "scopes TEXT NOT NULL DEFAULT '*',"
                    + "expires_at INTEGER,"
                    + "last_used_at INTEGER,"
                    + "created_at INTEGER NOT NULL,"
                    + "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"
                    + ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_api_tokens_user ON api_tokens(user_id)");

            // Login audit history
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS login_history ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "user_id INTEGER NOT NULL,"
                    + "ip_address TEXT NOT NULL DEFAULT '',"
                    + "user_agent TEXT NOT NULL DEFAULT '',"
                    + "success INTEGER NOT NULL DEFAULT 1,"
                    + "fail_reason TEXT NOT NULL DEFAULT '',"
                    + "login_time INTEGER NOT NULL,"
                    + "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE"
                    + ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_login_history_user ON login_history(user_id, login_time)");

            // Key-value system settings
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS system_settings ("
                    + "config_key TEXT PRIMARY KEY,"
                    + "config_value TEXT NOT NULL DEFAULT '',"
                    + "updated_at INTEGER NOT NULL"
                    + ")");

            // Performance monitoring snapshots
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS perf_logs ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "ts INTEGER NOT NULL,"
                    + "cpu REAL NOT NULL,"
                    + "mem_jvm INTEGER NOT NULL,"
                    + "mem_jvm_max INTEGER NOT NULL,"
                    + "mem_sys_used INTEGER NOT NULL,"
                    + "mem_sys_total INTEGER NOT NULL,"
                    + "tps REAL NOT NULL,"
                    + "disk_used INTEGER NOT NULL,"
                    + "disk_total INTEGER NOT NULL,"
                    + "game_folder INTEGER NOT NULL,"
                    + "online_count INTEGER NOT NULL"
                    + ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_perf_logs_ts ON perf_logs(ts)");

            // Player join/leave events (3NF referencing mc_players)
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS player_events ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "ts INTEGER NOT NULL,"
                    + "player_uuid TEXT NOT NULL,"
                    + "event_type INTEGER NOT NULL,"
                    + "FOREIGN KEY (player_uuid) REFERENCES mc_players(player_uuid)"
                    + ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_player_events_ts ON player_events(ts)");

            // Server lifecycle events
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS server_lifecycle ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "ts INTEGER NOT NULL,"
                    + "event_type INTEGER NOT NULL,"
                    + "detail TEXT NOT NULL DEFAULT ''"
                    + ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_server_lifecycle_ts ON server_lifecycle(ts)");

            // Map waypoints
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS map_waypoints ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "dimension TEXT NOT NULL,"
                    + "name TEXT NOT NULL,"
                    + "x INTEGER NOT NULL,"
                    + "y INTEGER NOT NULL,"
                    + "z INTEGER NOT NULL,"
                    + "color TEXT NOT NULL DEFAULT '#38bdf8',"
                    + "icon TEXT NOT NULL DEFAULT 'pin',"
                    + "owner_user_id INTEGER,"
                    + "is_global INTEGER NOT NULL DEFAULT 1,"
                    + "created_at INTEGER NOT NULL,"
                    + "FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE SET NULL"
                    + ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_map_waypoints_dim ON map_waypoints(dimension)");

            // Whitelist applications
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS whitelist_applications ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "username TEXT NOT NULL,"
                    + "online_uuid TEXT NOT NULL DEFAULT '',"
                    + "offline_uuid TEXT NOT NULL DEFAULT '',"
                    + "preferred_uuid_mode TEXT NOT NULL DEFAULT 'auto',"
                    + "email TEXT NOT NULL,"
                    + "reason TEXT NOT NULL,"
                    + "social_account TEXT NOT NULL DEFAULT '',"
                    + "status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),"
                    + "applicant_user_id INTEGER,"
                    + "bind_on_approve INTEGER NOT NULL DEFAULT 0,"
                    + "submit_ip TEXT NOT NULL DEFAULT '',"
                    + "reviewer_user_id INTEGER,"
                    + "review_note TEXT NOT NULL DEFAULT '',"
                    + "created_at INTEGER NOT NULL,"
                    + "reviewed_at INTEGER,"
                    + "FOREIGN KEY (applicant_user_id) REFERENCES users(id) ON DELETE SET NULL,"
                    + "FOREIGN KEY (reviewer_user_id) REFERENCES users(id) ON DELETE SET NULL"
                    + ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_whitelist_apps_status ON whitelist_applications(status, created_at)");

            // In-site inbox messages & notifications
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS in_site_messages ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "msg_type TEXT NOT NULL DEFAULT 'direct',"
                    + "sender_user_id INTEGER,"
                    + "sender_name TEXT NOT NULL DEFAULT '',"
                    + "sender_uuid TEXT NOT NULL DEFAULT '',"
                    + "recipient_user_id INTEGER,"
                    + "recipient_player_uuid TEXT NOT NULL DEFAULT '',"
                    + "recipient_name TEXT NOT NULL DEFAULT '',"
                    + "title TEXT NOT NULL DEFAULT '',"
                    + "content TEXT NOT NULL,"
                    + "related_type TEXT NOT NULL DEFAULT '',"
                    + "related_id TEXT NOT NULL DEFAULT '',"
                    + "is_read INTEGER NOT NULL DEFAULT 0,"
                    + "delivered_ingame INTEGER NOT NULL DEFAULT 0,"
                    + "created_at INTEGER NOT NULL,"
                    + "FOREIGN KEY (recipient_user_id) REFERENCES users(id) ON DELETE CASCADE"
                    + ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_recipient ON in_site_messages(recipient_user_id, is_read, created_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_messages_player ON in_site_messages(recipient_player_uuid, delivered_ingame, created_at)");

            // Synced in-game public chat & whispers
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS ingame_chat_logs ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "channel TEXT NOT NULL DEFAULT 'public',"
                    + "sender_uuid TEXT NOT NULL DEFAULT '',"
                    + "sender_name TEXT NOT NULL,"
                    + "recipient_uuid TEXT NOT NULL DEFAULT '',"
                    + "recipient_name TEXT NOT NULL DEFAULT '',"
                    + "source TEXT NOT NULL DEFAULT 'game',"
                    + "content TEXT NOT NULL,"
                    + "created_at INTEGER NOT NULL"
                    + ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ingame_chat_channel_ts ON ingame_chat_logs(channel, created_at)");
        }
    }

    private static void seedPermissions(SqlSession session) {
        PermissionMapper permMapper = session.getMapper(PermissionMapper.class);
        List<PermissionDef> defaults = Arrays.asList(
                perm("admin", "system", 10),
                perm("dashboard", "monitor", 20),
                perm("terminal", "server", 30),
                perm("logs", "monitor", 40),
                perm("map", "world", 50),
                perm("profiler", "monitor", 60),
                perm("mods_config", "server", 70),
                perm("whitelist_apply", "whitelist", 80),
                perm("whitelist_manage", "whitelist", 90)
        );
        for (PermissionDef def : defaults) {
            permMapper.upsertDefinition(def);
        }
    }

    private static PermissionDef perm(String key, String category, int order) {
        PermissionDef def = new PermissionDef();
        def.setPermKey(key);
        def.setCategory(category);
        def.setSortOrder(order);
        def.setActive(true);
        return def;
    }

    private static void seedRoles(SqlSession session) {
        RoleMapper roleMapper = session.getMapper(RoleMapper.class);
        PermissionMapper permMapper = session.getMapper(PermissionMapper.class);
        long now = System.currentTimeMillis() / 1000L;

        // 1: Admin (system, immutable, all permissions FULL)
        RoleInfo admin = new RoleInfo();
        admin.setId(1);
        admin.setName("admin");
        admin.setDisplayName("Administrator");
        admin.setDescription("Full system access");
        admin.setSystemRole(true);
        admin.setImmutable(true);
        admin.setCreatedAt(now);
        admin.setUpdatedAt(now);
        roleMapper.insertSeed(admin);
        for (String key : PermissionLevel.DEFAULT_PERMISSION_KEYS) {
            permMapper.setRolePermissionLevel(1, key, PermissionLevel.FULL.getLevel());
        }

        // 2: Guest (system, controls unauthenticated visitor visibility)
        RoleInfo guest = new RoleInfo();
        guest.setId(2);
        guest.setName("guest");
        guest.setDisplayName("Guest");
        guest.setDescription("Permissions for unauthenticated visitors");
        guest.setSystemRole(true);
        guest.setImmutable(false);
        guest.setCreatedAt(now);
        guest.setUpdatedAt(now);
        roleMapper.insertSeed(guest);
        permMapper.setRolePermissionIfMissing(2, "dashboard", PermissionLevel.READONLY.getLevel());
        permMapper.setRolePermissionIfMissing(2, "terminal", PermissionLevel.DENY.getLevel());
        permMapper.setRolePermissionIfMissing(2, "logs", PermissionLevel.DENY.getLevel());
        permMapper.setRolePermissionIfMissing(2, "admin", PermissionLevel.DENY.getLevel());
        permMapper.setRolePermissionIfMissing(2, "map", PermissionLevel.READONLY.getLevel());
        permMapper.setRolePermissionIfMissing(2, "profiler", PermissionLevel.DENY.getLevel());
        permMapper.setRolePermissionIfMissing(2, "mods_config", PermissionLevel.DENY.getLevel());
        permMapper.setRolePermissionIfMissing(2, "whitelist_apply", PermissionLevel.FULL.getLevel());
        permMapper.setRolePermissionIfMissing(2, "whitelist_manage", PermissionLevel.DENY.getLevel());

        // 3: Member/User (default registered account role)
        RoleInfo member = new RoleInfo();
        member.setId(3);
        member.setName("user");
        member.setDisplayName("Member");
        member.setDescription("Default role for registered users");
        member.setSystemRole(true);
        member.setImmutable(false);
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        roleMapper.insertSeed(member);
        permMapper.setRolePermissionIfMissing(3, "dashboard", PermissionLevel.READONLY.getLevel());
        permMapper.setRolePermissionIfMissing(3, "terminal", PermissionLevel.DENY.getLevel());
        permMapper.setRolePermissionIfMissing(3, "logs", PermissionLevel.READONLY.getLevel());
        permMapper.setRolePermissionIfMissing(3, "admin", PermissionLevel.DENY.getLevel());
        permMapper.setRolePermissionIfMissing(3, "map", PermissionLevel.READONLY.getLevel());
        permMapper.setRolePermissionIfMissing(3, "profiler", PermissionLevel.DENY.getLevel());
        permMapper.setRolePermissionIfMissing(3, "mods_config", PermissionLevel.DENY.getLevel());
        permMapper.setRolePermissionIfMissing(3, "whitelist_apply", PermissionLevel.FULL.getLevel());
        permMapper.setRolePermissionIfMissing(3, "whitelist_manage", PermissionLevel.DENY.getLevel());
    }

    private static void seedDefaultSettings(SqlSession session) {
        SettingsMapper settingsMapper = session.getMapper(SettingsMapper.class);
        UserMapper userMapper = session.getMapper(UserMapper.class);
        long now = System.currentTimeMillis() / 1000L;

        setIfMissing(settingsMapper, "schema_version", String.valueOf(SCHEMA_VERSION), now);
        // If users table is empty, setup is required
        boolean hasAnyUser = userMapper.countAllUsers() > 0;
        setIfMissing(settingsMapper, "setup_completed", hasAnyUser ? "true" : "false", now);

        setIfMissing(settingsMapper, "site_name", "Utopia Server Panel", now);
        setIfMissing(settingsMapper, "site_description", "Minecraft Server Management Panel", now);
        setIfMissing(settingsMapper, "base_url", "", now);
        setIfMissing(settingsMapper, "default_locale", "zh-CN", now);
        setIfMissing(settingsMapper, "site_favicon", "", now);
        setIfMissing(settingsMapper, "site_favicon_version", "default", now);

        // Registration & player binding
        setIfMissing(settingsMapper, "allow_registration", "true", now);
        setIfMissing(settingsMapper, "default_register_role_id", "3", now);
        setIfMissing(settingsMapper, "require_email_verification", "false", now);
        setIfMissing(settingsMapper, "require_binding_on_register", "false", now);
        setIfMissing(settingsMapper, "force_player_binding", "false", now);

        // Security policy
        setIfMissing(settingsMapper, "captcha_policy", "auto", now); // auto, always, off
        setIfMissing(settingsMapper, "captcha_difficulty", "80000", now);
        setIfMissing(settingsMapper, "session_timeout_days", "7", now);
        setIfMissing(settingsMapper, "force_2fa", "false", now);
        setIfMissing(settingsMapper, "max_failed_logins", "5", now);
        setIfMissing(settingsMapper, "lockout_minutes", "15", now);

        // SMTP
        setIfMissing(settingsMapper, "smtp_enabled", "false", now);
        setIfMissing(settingsMapper, "smtp_host", "", now);
        setIfMissing(settingsMapper, "smtp_port", "465", now);
        setIfMissing(settingsMapper, "smtp_encryption", "ssl", now);
        setIfMissing(settingsMapper, "smtp_user", "", now);
        setIfMissing(settingsMapper, "smtp_password", "", now);
        setIfMissing(settingsMapper, "smtp_from", "", now);
        setIfMissing(settingsMapper, "smtp_from_name", "Utopia Server Panel", now);

        // Monitoring
        setIfMissing(settingsMapper, "monitor_enabled", "true", now);
        setIfMissing(settingsMapper, "monitor_interval", "60", now);
        setIfMissing(settingsMapper, "monitor_retention_days", "30", now);
        setIfMissing(settingsMapper, "monitor_show_player_names", "true", now);

        // Web Map
        setIfMissing(settingsMapper, "map_enabled", "true", now);
        setIfMissing(settingsMapper, "map_render_mode", "hybrid", now);
        setIfMissing(settingsMapper, "map_max_chunks_per_tick", "2", now);
        setIfMissing(settingsMapper, "map_cache_max_mb", "512", now);
        setIfMissing(settingsMapper, "map_vis_terrain", "readonly", now);
        setIfMissing(settingsMapper, "map_vis_nether_ceiling", "readonly", now);
        setIfMissing(settingsMapper, "map_vis_grid", "readonly", now);
        setIfMissing(settingsMapper, "map_vis_coordinates", "readonly", now);
        setIfMissing(settingsMapper, "map_vis_water_depth", "readonly", now);
        setIfMissing(settingsMapper, "map_vis_height_shading", "readonly", now);
        setIfMissing(settingsMapper, "map_vis_biome_tint", "readonly", now);
        setIfMissing(settingsMapper, "map_vis_players", "readonly", now);
        setIfMissing(settingsMapper, "map_vis_waypoints", "readonly", now);
        setIfMissing(settingsMapper, "map_vis_mobs_hostile", "full", now);
        setIfMissing(settingsMapper, "map_vis_mobs_passive", "full", now);
        setIfMissing(settingsMapper, "map_vis_entities", "full", now);
        setIfMissing(settingsMapper, "map_vis_structures", "full", now);
        setIfMissing(settingsMapper, "map_vis_teleport", "full", now);

        // Whitelist management & review
        setIfMissing(settingsMapper, "whitelist_management_enabled", "false", now);
        setIfMissing(settingsMapper, "whitelist_apply_cooldown_sec", "60", now);
        setIfMissing(settingsMapper, "whitelist_max_pending_per_ip", "5", now);
        setIfMissing(settingsMapper, "whitelist_send_email", "false", now);
        setIfMissing(settingsMapper, "whitelist_social_required", "false", now);
        setIfMissing(settingsMapper, "whitelist_required_social_platforms", "[]", now);

        // Server restart integration
        setIfMissing(settingsMapper, "server_restart_command", "", now);
        setIfMissing(settingsMapper, "server_restart_hint", "", now);

        // Chat & In-site messages
        setIfMissing(settingsMapper, "chat_sync_enabled", "true", now);
        setIfMissing(settingsMapper, "web_chat_send_enabled", "true", now);
        setIfMissing(settingsMapper, "chat_retention_days", "30", now);
    }

    private static void setIfMissing(SettingsMapper mapper, String key, String value, long now) {
        if (mapper.getValue(key) == null) {
            mapper.setValue(key, value, now);
        }
    }

    private static void ensureCryptoKeys(SqlSession session) {
        SettingsMapper mapper = session.getMapper(SettingsMapper.class);
        long now = System.currentTimeMillis() / 1000L;

        String jwtKey = mapper.getValue("jwt_secret");
        if (jwtKey != null && !jwtKey.isEmpty()) {
            JwtUtil.initialize(jwtKey);
        } else {
            String newKey = JwtUtil.generateNewKey();
            mapper.setValue("jwt_secret", newKey, now);
        }

        String aesKey = mapper.getValue("totp_aes_key");
        if (aesKey != null && !aesKey.isEmpty()) {
            CryptoUtil.initialize(aesKey);
        } else {
            String newAesKey = CryptoUtil.generateNewKeyBase64();
            mapper.setValue("totp_aes_key", newAesKey, now);
        }
    }
}
