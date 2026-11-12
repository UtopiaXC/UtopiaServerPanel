package com.utopiaxc.utopiaserverpanel.web.middleware;

import com.utopiaxc.utopiaserverpanel.auth.JwtClaims;
import com.utopiaxc.utopiaserverpanel.auth.JwtUtil;
import com.utopiaxc.utopiaserverpanel.auth.PasswordUtil;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.ApiTokenMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.SessionMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.UserMapper;
import com.utopiaxc.utopiaserverpanel.web.db.model.ApiToken;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserAccount;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserSession;

/**
 * Resolves either JWT Access Tokens or Personal API Tokens ("usp_...") into a verified principal.
 */
public final class TokenAuthHelper {

    public static final class Principal {
        private final int userId;
        private final String username;
        private final int roleId;
        private final String sessionId;
        private final String authType;
        private final String scopes;

        public Principal(int userId, String username, int roleId, String sessionId, String authType, String scopes) {
            this.userId = userId;
            this.username = username;
            this.roleId = roleId;
            this.sessionId = sessionId;
            this.authType = authType;
            this.scopes = scopes;
        }

        public int getUserId() { return userId; }
        public String getUsername() { return username; }
        public int getRoleId() { return roleId; }
        public String getSessionId() { return sessionId; }
        public String getAuthType() { return authType; }
        public String getScopes() { return scopes; }
    }

    private TokenAuthHelper() {}

    public static Principal resolve(String rawToken) {
        if (rawToken == null || rawToken.trim().isEmpty()) return null;
        String token = rawToken.trim();
        final long now = System.currentTimeMillis() / 1000L;

        // 1. Personal Access Token ("usp_...")
        if (token.startsWith("usp_")) {
            final String hash = PasswordUtil.sha256Hex(token);
            return MyBatisFactory.doWorkWithResult(session -> {
                ApiTokenMapper tokenMapper = session.getMapper(ApiTokenMapper.class);
                ApiToken pat = tokenMapper.findByTokenHash(hash);
                if (pat == null) return null;
                if (!"active".equals(pat.getUserStatus())) return null;
                if (pat.getExpiresAt() != null && pat.getExpiresAt() < now) return null;

                if (pat.getLastUsedAt() == null || now - pat.getLastUsedAt() >= 60) {
                    tokenMapper.updateLastUsed(pat.getId(), now);
                }
                return new Principal(pat.getUserId(), pat.getUsername(), pat.getRoleId(), null, "api_token", pat.getScopes());
            });
        }

        // 2. JWT Access Token
        if (!JwtUtil.isInitialized()) return null;
        JwtClaims claims = JwtUtil.validateToken(token);
        if (claims == null) return null;
        String purpose = JwtUtil.getPurpose(claims);
        if (purpose != null && !"access".equals(purpose)) return null;

        final int userId = JwtUtil.getUserId(claims);
        final int tokenVersion = JwtUtil.getTokenVersion(claims);
        final String sessionId = JwtUtil.getSessionId(claims);

        return MyBatisFactory.doWorkWithResult(session -> {
            UserAccount user = session.getMapper(UserMapper.class).findById(userId);
            if (user == null || !"active".equals(user.getStatus()) || user.getTokenVersion() != tokenVersion) {
                return null;
            }
            if (sessionId != null && !sessionId.isEmpty()) {
                SessionMapper sm = session.getMapper(SessionMapper.class);
                UserSession us = sm.findBySessionId(sessionId);
                if (us == null || us.getExpiresAt() < now) {
                    return null;
                }
                if (now - us.getLastSeenAt() >= 60) {
                    sm.touchSession(sessionId, now);
                }
            }
            return new Principal(user.getId(), user.getUsername(), user.getRoleId(), sessionId, "jwt", "*");
        });
    }
}

