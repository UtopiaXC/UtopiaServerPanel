package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import com.utopiaxc.utopiaserverpanel.auth.JwtUtil;
import com.utopiaxc.utopiaserverpanel.auth.PasswordUtil;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Core authentication service using MyBatis mappers.
 */
public final class AuthService {

    private AuthService() {}

    public static AuthResult login(String username, String password) {
        return MyBatisFactory.doWorkWithResult(session -> {
            var userMapper = session.getMapper(UserMapper.class);
            Map<String, Object> user = userMapper.findByUsername(username);
            if (user == null) return null;

            int isActive = toInt(user.get("isActive"));
            if (isActive == 0) {
                UtopiaServerPanel.LOGGER.warn("Login attempt for inactive user: {}", username);
                return null;
            }

            String storedHash = (String) user.get("passwordHash");
            if (!PasswordUtil.verify(password, storedHash)) {
                UtopiaServerPanel.LOGGER.warn("Failed login attempt for user: {}", username);
                return null;
            }

            int userId = toInt(user.get("id"));
            int roleId = toInt(user.get("roleId"));
            boolean mustChange = toInt(user.get("mustChangePassword")) == 1;
            String bindingStatus = (String) user.get("bindingStatus");

            String accessToken = JwtUtil.generateAccessToken(userId, username, roleId);
            String refreshToken = JwtUtil.generateRefreshToken(userId);

            // Store refresh token
            var tokenMapper = session.getMapper(TokenMapper.class);
            Map<String, Object> tokenData = new HashMap<>();
            tokenData.put("userId", userId);
            tokenData.put("tokenHash", hashToken(refreshToken));
            tokenData.put("expiresAt", System.currentTimeMillis() / 1000 + 7 * 24 * 60 * 60);
            tokenData.put("createdAt", System.currentTimeMillis() / 1000);
            tokenMapper.insertToken(tokenData);

            Set<String> permissions = getUserPermissions(session, userId);
            Map<String, Object> userInfo = buildUserInfo(user, roleId, permissions, session);

            return new AuthResult(accessToken, refreshToken, userId, username, roleId,
                    mustChange, userInfo, permissions, false, null);
        });
    }

    public static AuthResult refreshToken(String rawRefreshToken) {
        return MyBatisFactory.doWorkWithResult(session -> {
            var tokenMapper = session.getMapper(TokenMapper.class);
            String tokenHash = hashToken(rawRefreshToken);
            Map<String, Object> tokenData = tokenMapper.findTokenByHash(tokenHash);
            if (tokenData == null) return null;

            if (toInt(tokenData.get("isActive")) == 0) {
                tokenMapper.deleteByHash(tokenHash);
                return null;
            }
            long expiresAt = ((Number) tokenData.get("expiresAt")).longValue();
            if (expiresAt < System.currentTimeMillis() / 1000) {
                tokenMapper.deleteByHash(tokenHash);
                return null;
            }

            int userId = toInt(tokenData.get("userId"));
            String username = (String) tokenData.get("username");
            int roleId = toInt(tokenData.get("roleId"));
            boolean mustChange = toInt(tokenData.get("mustChangePassword")) == 1;

            // Rotate refresh token
            tokenMapper.deleteByHash(tokenHash);
            String newAccessToken = JwtUtil.generateAccessToken(userId, username, roleId);
            String newRefreshToken = JwtUtil.generateRefreshToken(userId);
            Map<String, Object> newToken = new HashMap<>();
            newToken.put("userId", userId);
            newToken.put("tokenHash", hashToken(newRefreshToken));
            newToken.put("expiresAt", System.currentTimeMillis() / 1000 + 7 * 24 * 60 * 60);
            newToken.put("createdAt", System.currentTimeMillis() / 1000);
            tokenMapper.insertToken(newToken);

            Set<String> permissions = getUserPermissions(session, userId);
            return new AuthResult(newAccessToken, newRefreshToken, userId, username, roleId,
                    mustChange, null, permissions, false, null);
        });
    }

    public static void logout(String rawRefreshToken) {
        MyBatisFactory.doWork(session -> {
            var tokenMapper = session.getMapper(TokenMapper.class);
            tokenMapper.deleteByHash(hashToken(rawRefreshToken));
        });
    }

    public static PasswordChangeResult changePassword(int userId, String oldPassword, String newPassword) {
        return MyBatisFactory.doWorkWithResult(session -> {
            var userMapper = session.getMapper(UserMapper.class);
            String currentHash = userMapper.getPasswordHash(userId);
            if (currentHash == null) return PasswordChangeResult.ERROR;
            if (!PasswordUtil.verify(oldPassword, currentHash)) return PasswordChangeResult.WRONG_PASSWORD;

            Map<String, Object> params = new HashMap<>();
            params.put("id", userId);
            params.put("passwordHash", PasswordUtil.hash(newPassword));
            params.put("mustChangePassword", 0);
            params.put("updatedAt", System.currentTimeMillis() / 1000);
            userMapper.updatePassword(params);
            return PasswordChangeResult.SUCCESS;
        });
    }

    public static RegisterResult register(String username, String password, String bindingCode) {
        if (username == null || !username.matches("^[a-zA-Z0-9_]{3,32}$"))
            return new RegisterResult(false, "Username must be 3-32 alphanumeric characters");
        if (password == null || password.length() < 4)
            return new RegisterResult(false, "Password must be at least 4 characters");

        return MyBatisFactory.doWorkWithResult(session -> {
            var userMapper = session.getMapper(UserMapper.class);
            var bindingMapper = session.getMapper(BindingMapper.class);

            if (userMapper.countByUsername(username) > 0)
                return new RegisterResult(false, "Username already exists");

            Map<String, Object> codeData = bindingMapper.findByCode(bindingCode);
            if (codeData == null) return new RegisterResult(false, "Invalid or expired binding code");
            if (toInt(codeData.get("used")) == 1) return new RegisterResult(false, "Binding code already used");
            long expiresAt = ((Number) codeData.get("expiresAt")).longValue();
            if (expiresAt < System.currentTimeMillis() / 1000)
                return new RegisterResult(false, "Binding code expired");

            String playerUuid = (String) codeData.get("playerUuid");
            String playerName = (String) codeData.get("playerName");

            long now = System.currentTimeMillis() / 1000;
            Map<String, Object> user = new HashMap<>();
            user.put("username", username);
            user.put("passwordHash", PasswordUtil.hash(password));
            user.put("roleId", 2);
            user.put("mustChangePassword", 0);
            user.put("bindingStatus", "bound");
            user.put("createdAt", now);
            user.put("updatedAt", now);
            userMapper.insert(user);

            int userId = toInt(user.get("id"));
            Map<String, Object> binding = new HashMap<>();
            binding.put("userId", userId);
            binding.put("playerUuid", playerUuid);
            binding.put("playerName", playerName);
            binding.put("boundAt", now);
            bindingMapper.insertBinding(binding);
            bindingMapper.markCodeUsed(bindingCode);

            return new RegisterResult(true, "Registration successful");
        });
    }

    public static BindResult bindUser(int userId, String bindingCode) {
        return MyBatisFactory.doWorkWithResult(session -> {
            var userMapper = session.getMapper(UserMapper.class);
            var bindingMapper = session.getMapper(BindingMapper.class);

            Map<String, Object> user = userMapper.findById(userId);
            if (user == null) return new BindResult(false, "User not found");
            if ("bound".equals(user.get("bindingStatus")))
                return new BindResult(false, "User is already bound to a player");

            Map<String, Object> codeData = bindingMapper.findByCode(bindingCode);
            if (codeData == null) return new BindResult(false, "Invalid or expired binding code");
            if (toInt(codeData.get("used")) == 1) return new BindResult(false, "Binding code already used");
            long expiresAt = ((Number) codeData.get("expiresAt")).longValue();
            if (expiresAt < System.currentTimeMillis() / 1000)
                return new BindResult(false, "Binding code expired");

            String playerUuid = (String) codeData.get("playerUuid");
            String playerName = (String) codeData.get("playerName");

            if (bindingMapper.countByPlayerUuid(playerUuid) > 0)
                return new BindResult(false, "This player is already bound to another account");

            long now = System.currentTimeMillis() / 1000;
            Map<String, Object> binding = new HashMap<>();
            binding.put("userId", userId);
            binding.put("playerUuid", playerUuid);
            binding.put("playerName", playerName);
            binding.put("boundAt", now);
            bindingMapper.insertBinding(binding);

            Map<String, Object> status = new HashMap<>();
            status.put("id", userId);
            status.put("bindingStatus", "bound");
            status.put("updatedAt", now);
            userMapper.updateBindingStatus(status);

            bindingMapper.markCodeUsed(bindingCode);
            return new BindResult(true, "Successfully bound to " + playerName);
        });
    }

    public static boolean unbindUser(int userId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            var userMapper = session.getMapper(UserMapper.class);
            var bindingMapper = session.getMapper(BindingMapper.class);
            bindingMapper.deleteByUserId(userId);
            Map<String, Object> status = new HashMap<>();
            status.put("id", userId);
            status.put("bindingStatus", "unbound");
            status.put("updatedAt", System.currentTimeMillis() / 1000);
            userMapper.updateBindingStatus(status);
            return true;
        });
    }

    public static Map<String, Object> getUserInfo(int userId) {
        return MyBatisFactory.doWorkWithResult(session -> {
            var userMapper = session.getMapper(UserMapper.class);
            Map<String, Object> user = userMapper.findById(userId);
            if (user == null) return null;
            int roleId = toInt(user.get("roleId"));
            Set<String> permissions = getUserPermissions(session, userId);
            return buildUserInfo(user, roleId, permissions, session);
        });
    }

    // ── helpers ──

    private static Set<String> getUserPermissions(org.apache.ibatis.session.SqlSession session, int userId) {
        var permMapper = session.getMapper(PermissionMapper.class);
        return new HashSet<>(permMapper.getKeysByUserId(userId));
    }

    private static Map<String, Object> buildUserInfo(Map<String, Object> user, int roleId,
                                                      Set<String> permissions,
                                                      org.apache.ibatis.session.SqlSession session) {
        var roleMapper = session.getMapper(RoleMapper.class);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", user.get("id"));
        info.put("username", user.get("username"));
        info.put("roleId", roleId);
        info.put("roleName", user.get("roleName"));

        // Fetch role name if not present
        if (info.get("roleName") == null) {
            Map<String, Object> role = roleMapper.findById(roleId);
            info.put("roleName", role != null ? role.get("name") : "unknown");
        }

        info.put("mustChangePassword", toInt(user.get("mustChangePassword")) == 1);
        info.put("isActive", toInt(user.get("isActive")) == 1);
        info.put("bindingStatus", user.get("bindingStatus"));
        info.put("createdAt", user.get("createdAt"));

        if (user.containsKey("playerName") && user.get("playerName") != null) {
            info.put("playerName", user.get("playerName"));
            info.put("playerUuid", user.get("playerUuid"));
        }
        info.put("permissions", new ArrayList<>(permissions));
        return info;
    }

    private static int toInt(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value instanceof String s) return Integer.parseInt(s);
        return 0;
    }

    private static String hashToken(String rawToken) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    // ── result types ──

    public record AuthResult(String accessToken, String refreshToken, int userId, String username,
                             int roleId, boolean mustChangePassword, Map<String, Object> user,
                             Set<String> permissions, boolean needsBinding, String errorMessage) {
        public boolean isSuccess() { return accessToken != null; }
    }

    public record RegisterResult(boolean success, String message) {}
    public record BindResult(boolean success, String message) {}
    public enum PasswordChangeResult { SUCCESS, WRONG_PASSWORD, ERROR }
}
