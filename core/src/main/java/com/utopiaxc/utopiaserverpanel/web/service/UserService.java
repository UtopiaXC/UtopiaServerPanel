package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.auth.PasswordUtil;
import com.utopiaxc.utopiaserverpanel.logging.PanelLog;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.RoleMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.SecurityMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.SessionMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.UserMapper;
import com.utopiaxc.utopiaserverpanel.web.db.model.RoleInfo;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserAccount;

import java.util.*;

/**
 * Administrative user management service.
 */
public final class UserService {

    private static final Set<String> VALID_STATUSES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("active", "frozen", "banned", "inactive")));

    private UserService() {}

    public static Map<String, Object> listUsers(String search, Integer roleId, String status,
                                                String sortBy, String sortOrder, int page, int size) {
        final int safePage = Math.max(1, page);
        final int safeSize = Math.max(1, Math.min(100, size));
        final int offset = (safePage - 1) * safeSize;

        return MyBatisFactory.doWorkWithResult(session -> {
            UserMapper mapper = session.getMapper(UserMapper.class);
            List<UserAccount> accounts = mapper.listFiltered(search, roleId, status, sortBy, sortOrder, offset, safeSize);
            int total = mapper.countFiltered(search, roleId, status);
            List<Map<String, Object>> items = new ArrayList<>();
            for (UserAccount acc : accounts) {
                items.add(toAdminDto(acc));
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("users", items);
            result.put("total", Integer.valueOf(total));
            result.put("page", Integer.valueOf(safePage));
            result.put("size", Integer.valueOf(safeSize));
            return result;
        });
    }

    public static Map<String, Object> getUser(int userId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            UserAccount acc = session.getMapper(UserMapper.class).findById(userId);
            return acc != null ? toAdminDto(acc) : null;
        });
    }

    public static final class CreateUserResult {
        private final boolean success;
        private final String errorCode;
        private final String message;
        private final Map<String, Object> user;

        public CreateUserResult(boolean success, String errorCode, String message, Map<String, Object> user) {
            this.success = success;
            this.errorCode = errorCode;
            this.message = message;
            this.user = user;
        }

        public boolean success() { return success; }
        public String errorCode() { return errorCode; }
        public String message() { return message; }
        public Map<String, Object> user() { return user; }
    }

    public static CreateUserResult createUser(String username, String nickname, String email, String password,
                                              int roleId, String status, boolean mustChangePassword, boolean require2fa) {
        if (username == null || !username.trim().matches("^[a-zA-Z0-9_]{3,32}$")) {
            return new CreateUserResult(false, "INVALID_USERNAME", "Username must be 3-32 alphanumeric or underscore characters", null);
        }
        if (password == null || password.length() < 6) {
            return new CreateUserResult(false, "WEAK_PASSWORD", "Password must be at least 6 characters", null);
        }
        String cleanUser = username.trim();
        String cleanNick = (nickname != null && !nickname.trim().isEmpty()) ? nickname.trim() : cleanUser;
        String cleanEmail = (email != null && !email.trim().isEmpty()) ? email.trim() : null;
        if (cleanEmail != null && !cleanEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return new CreateUserResult(false, "INVALID_EMAIL", "Invalid email format", null);
        }
        String cleanStatus = (status != null && VALID_STATUSES.contains(status)) ? status : "active";

        return MyBatisFactory.doWorkWithResult(session -> {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            RoleMapper roleMapper = session.getMapper(RoleMapper.class);

            if (userMapper.countByUsername(cleanUser) > 0) {
                return new CreateUserResult(false, "USERNAME_EXISTS", "Username already exists", null);
            }
            if (cleanEmail != null && userMapper.countByEmailExclude(cleanEmail, -1) > 0) {
                return new CreateUserResult(false, "EMAIL_EXISTS", "Email already in use", null);
            }
            RoleInfo role = roleMapper.findById(roleId);
            if (role == null || "guest".equalsIgnoreCase(role.getName())) {
                return new CreateUserResult(false, "INVALID_ROLE", "Invalid role selected", null);
            }

            long now = System.currentTimeMillis() / 1000L;
            UserAccount acc = new UserAccount();
            acc.setUsername(cleanUser);
            acc.setNickname(cleanNick);
            acc.setEmail(cleanEmail);
            acc.setPasswordHash(PasswordUtil.hash(password));
            acc.setRoleId(roleId);
            acc.setStatus(cleanStatus);
            acc.setMustChangePassword(mustChangePassword);
            acc.setRequire2fa(require2fa);
            acc.setLocale(SettingsService.getDefaultLocale());
            acc.setCreatedAt(now);
            acc.setUpdatedAt(now);
            userMapper.insert(acc);

            UserAccount saved = userMapper.findById(acc.getId());
            PanelLog.get().info("Admin created user '{}' (id={}, role={})", cleanUser, Integer.valueOf(acc.getId()), role.getName());
            return new CreateUserResult(true, null, "User created", toAdminDto(saved));
        });
    }

    public static final class UpdateUserResult {
        private final boolean success;
        private final String errorCode;
        private final String message;
        private final Map<String, Object> user;

        public UpdateUserResult(boolean success, String errorCode, String message, Map<String, Object> user) {
            this.success = success;
            this.errorCode = errorCode;
            this.message = message;
            this.user = user;
        }

        public boolean success() { return success; }
        public String errorCode() { return errorCode; }
        public String message() { return message; }
        public Map<String, Object> user() { return user; }
    }

    public static UpdateUserResult updateUser(int targetId, int requesterId, Integer roleId, String status,
                                              String nickname, String email, String newPassword,
                                              Boolean mustChangePassword, Boolean require2fa) {
        return MyBatisFactory.doWorkWithResult(session -> {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            RoleMapper roleMapper = session.getMapper(RoleMapper.class);
            SessionMapper sessionMapper = session.getMapper(SessionMapper.class);

            UserAccount existing = userMapper.findById(targetId);
            if (existing == null) {
                return new UpdateUserResult(false, "NOT_FOUND", "User not found", null);
            }

            int nextRoleId = roleId != null ? roleId : existing.getRoleId();
            RoleInfo role = roleMapper.findById(nextRoleId);
            if (role == null || "guest".equalsIgnoreCase(role.getName())) {
                return new UpdateUserResult(false, "INVALID_ROLE", "Invalid role selected", null);
            }

            String nextStatus = status != null ? status : existing.getStatus();
            if (!VALID_STATUSES.contains(nextStatus)) {
                return new UpdateUserResult(false, "INVALID_STATUS", "Invalid account status", null);
            }

            if (targetId == requesterId) {
                if (nextRoleId != existing.getRoleId()) {
                    return new UpdateUserResult(false, "CANNOT_MODIFY_SELF_ROLE", "Cannot change your own role", null);
                }
                if (!"active".equals(nextStatus)) {
                    return new UpdateUserResult(false, "CANNOT_DISABLE_SELF", "Cannot disable or ban your own account", null);
                }
            }

            if (existing.getRoleId() == PermissionService.ADMIN_ROLE_ID
                    && (nextRoleId != PermissionService.ADMIN_ROLE_ID || !"active".equals(nextStatus))) {
                if (userMapper.countActiveAdmins() <= 1) {
                    return new UpdateUserResult(false, "LAST_ADMIN_PROTECTED", "Cannot downgrade or disable the last active administrator", null);
                }
            }

            String nextEmail = email != null
                    ? (email.trim().isEmpty() ? null : email.trim())
                    : existing.getEmail();
            if (nextEmail != null && !nextEmail.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                return new UpdateUserResult(false, "INVALID_EMAIL", "Invalid email format", null);
            }
            if (nextEmail != null && userMapper.countByEmailExclude(nextEmail, targetId) > 0) {
                return new UpdateUserResult(false, "EMAIL_EXISTS", "Email already in use", null);
            }

            long now = System.currentTimeMillis() / 1000L;
            if (newPassword != null && !newPassword.isEmpty()) {
                if (newPassword.length() < 6) {
                    return new UpdateUserResult(false, "WEAK_PASSWORD", "Password must be at least 6 characters", null);
                }
                userMapper.updatePassword(targetId, PasswordUtil.hash(newPassword),
                        Boolean.TRUE.equals(mustChangePassword) ? 1 : 0, now);
                sessionMapper.deleteAllForUser(targetId);
            }

            UserAccount updated = new UserAccount();
            updated.setId(targetId);
            updated.setRoleId(nextRoleId);
            updated.setStatus(nextStatus);
            updated.setNickname(nickname != null ? nickname.trim() : existing.getNickname());
            updated.setEmail(nextEmail);
            updated.setMustChangePassword(mustChangePassword != null ? mustChangePassword : existing.isMustChangePassword());
            updated.setRequire2fa(require2fa != null ? require2fa : existing.isRequire2fa());
            updated.setUpdatedAt(now);
            userMapper.adminUpdate(updated);

            if (!"active".equals(nextStatus)) {
                userMapper.bumpTokenVersion(targetId, now);
                sessionMapper.deleteAllForUser(targetId);
            }

            UserAccount fresh = userMapper.findById(targetId);
            return new UpdateUserResult(true, null, "User updated", toAdminDto(fresh));
        });
    }

    public static boolean unlockUser(int targetId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            UserMapper mapper = session.getMapper(UserMapper.class);
            UserAccount acc = mapper.findById(targetId);
            if (acc == null) return false;
            long now = System.currentTimeMillis() / 1000L;
            mapper.updateLoginFailures(targetId, 0, null, now);
            if ("frozen".equals(acc.getStatus())) {
                mapper.updateStatus(targetId, "active", now);
            }
            return true;
        });
    }

    public static boolean resetUser2fa(int targetId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            SecurityMapper secMapper = session.getMapper(SecurityMapper.class);
            if (userMapper.findById(targetId) == null) return false;
            secMapper.deleteTotpByUserId(targetId);
            userMapper.bumpTokenVersion(targetId, System.currentTimeMillis() / 1000L);
            return true;
        });
    }

    public enum DeleteResult { SUCCESS, NOT_FOUND, CANNOT_DELETE_SELF, LAST_ADMIN_PROTECTED }

    public static DeleteResult deleteUser(int targetId, int requesterId) {
        if (targetId == requesterId) return DeleteResult.CANNOT_DELETE_SELF;
        return MyBatisFactory.doWorkWithResult(session -> {
            UserMapper userMapper = session.getMapper(UserMapper.class);
            UserAccount target = userMapper.findById(targetId);
            if (target == null) return DeleteResult.NOT_FOUND;
            if (target.getRoleId() == PermissionService.ADMIN_ROLE_ID
                    && "active".equals(target.getStatus())
                    && userMapper.countActiveAdmins() <= 1) {
                return DeleteResult.LAST_ADMIN_PROTECTED;
            }
            userMapper.deleteById(targetId);
            return DeleteResult.SUCCESS;
        });
    }

    public static Map<String, Object> toAdminDto(UserAccount u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("nickname", u.getNickname());
        m.put("email", u.getEmail());
        m.put("roleId", u.getRoleId());
        m.put("roleName", u.getRoleName());
        m.put("roleDisplayName", u.getRoleDisplayName());
        m.put("status", u.getStatus());
        m.put("mustChangePassword", u.isMustChangePassword());
        m.put("require2fa", u.isRequire2fa());
        m.put("totpEnabled", u.isTotpEnabled());
        m.put("failedLoginAttempts", u.getFailedLoginAttempts());
        m.put("lockoutUntil", u.getLockoutUntil());
        m.put("bound", u.isBound());
        m.put("playerUuid", u.getPlayerUuid());
        m.put("playerName", u.getPlayerName());
        m.put("boundAt", u.getBoundAt());
        m.put("locale", u.getLocale());
        m.put("createdAt", u.getCreatedAt());
        m.put("updatedAt", u.getUpdatedAt());
        return m;
    }
}
