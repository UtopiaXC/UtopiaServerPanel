package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import com.utopiaxc.utopiaserverpanel.auth.PasswordUtil;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.*;

import java.util.*;

/**
 * User management service using MyBatis mappers.
 */
public final class UserService {

    private UserService() {}

    public static List<Map<String, Object>> listUsers() {
        return MyBatisFactory.doWorkWithResult(session -> {
            var userMapper = session.getMapper(UserMapper.class);
            var permMapper = session.getMapper(PermissionMapper.class);
            List<Map<String, Object>> users = userMapper.listAll();
            for (Map<String, Object> u : users) {
                int userId = toInt(u.get("id"));
                List<Map<String, Object>> levels = permMapper.getUserPermissionLevels(userId);
                Map<String, Integer> permLevels = new LinkedHashMap<>();
                for (Map<String, Object> entry : levels) {
                    permLevels.put((String) entry.get("permissionKey"), ((Number) entry.get("level")).intValue());
                }
                u.put("permissionLevels", permLevels);
            }
            return users;
        });
    }

    public static Map<String, Object> getUser(int userId) {
        return AuthService.getUserInfo(userId);
    }

    public static CreateUserResult createUser(String username, String password, int roleId) {
        if (!username.matches("^[a-zA-Z0-9_]{3,32}$"))
            return new CreateUserResult(-1, "Username must be 3-32 alphanumeric characters");
        if (password.length() < 4)
            return new CreateUserResult(-1, "Password must be at least 4 characters");

        return MyBatisFactory.doWorkWithResult(session -> {
            var userMapper = session.getMapper(UserMapper.class);
            var roleMapper = session.getMapper(RoleMapper.class);

            if (userMapper.countByUsername(username) > 0)
                return new CreateUserResult(-1, "Username already exists");
            if (roleMapper.findById(roleId) == null)
                return new CreateUserResult(-1, "Role not found");

            long now = System.currentTimeMillis() / 1000;
            Map<String, Object> user = new HashMap<>();
            user.put("username", username);
            user.put("passwordHash", PasswordUtil.hash(password));
            user.put("roleId", roleId);
            user.put("mustChangePassword", 1);
            user.put("bindingStatus", "unbound");
            user.put("createdAt", now);
            user.put("updatedAt", now);
            userMapper.insert(user);

            int userId = toInt(user.get("id"));
            UtopiaServerPanel.LOGGER.info("User created: {} (id={})", username, userId);
            return new CreateUserResult(userId, "User created successfully");
        });
    }

    public static boolean updateUser(int userId, Integer roleId, Boolean isActive) {
        return MyBatisFactory.doWorkWithResult(session -> {
            var userMapper = session.getMapper(UserMapper.class);

            // Don't allow deactivating admin
            Map<String, Object> user = userMapper.findById(userId);
            if (user == null) return false;
            if ("admin".equals(user.get("username")) && isActive != null && !isActive)
                return false;

            if (roleId != null) {
                var roleMapper = session.getMapper(RoleMapper.class);
                if (roleMapper.findById(roleId) == null) return false;
            }

            Map<String, Object> params = new HashMap<>();
            params.put("id", userId);
            params.put("roleId", roleId);
            params.put("isActive", isActive != null ? (isActive ? 1 : 0) : null);
            params.put("updatedAt", System.currentTimeMillis() / 1000);
            return userMapper.update(params) > 0;
        });
    }

    public static DeleteResult deleteUser(int userId, int requestingUserId) {
        if (userId == 1) return DeleteResult.CANNOT_DELETE_ADMIN;
        if (userId == requestingUserId) return DeleteResult.CANNOT_DELETE_SELF;

        return MyBatisFactory.doWorkWithResult(session -> {
            var userMapper = session.getMapper(UserMapper.class);
            if (userMapper.delete(userId) > 0) {
                UtopiaServerPanel.LOGGER.info("User deleted: id={}", userId);
                return DeleteResult.SUCCESS;
            }
            return DeleteResult.NOT_FOUND;
        });
    }

    public static boolean resetPassword(int userId, String newPassword) {
        if (newPassword.length() < 4) return false;
        return MyBatisFactory.doWorkWithResult(session -> {
            var userMapper = session.getMapper(UserMapper.class);
            Map<String, Object> params = new HashMap<>();
            params.put("id", userId);
            params.put("passwordHash", PasswordUtil.hash(newPassword));
            params.put("mustChangePassword", 1);
            params.put("updatedAt", System.currentTimeMillis() / 1000);
            return userMapper.updatePassword(params) > 0;
        });
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) return Integer.parseInt(s);
        return 0;
    }

    public record CreateUserResult(int userId, String message) {
        public boolean isSuccess() { return userId >= 0; }
    }

    public enum DeleteResult { SUCCESS, NOT_FOUND, CANNOT_DELETE_ADMIN, CANNOT_DELETE_SELF, ERROR }
}
