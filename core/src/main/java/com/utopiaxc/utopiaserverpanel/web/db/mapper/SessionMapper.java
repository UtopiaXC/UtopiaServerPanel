package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import com.utopiaxc.utopiaserverpanel.web.db.model.LoginHistoryEntry;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserSession;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface SessionMapper {

    @Insert("INSERT INTO user_sessions (session_id, user_id, token_hash, token_version, ip_address, user_agent, remember_me, created_at, last_seen_at, expires_at) " +
            "VALUES (#{sessionId}, #{userId}, #{tokenHash}, #{tokenVersion}, #{ipAddress}, #{userAgent}, #{rememberMe}, #{createdAt}, #{lastSeenAt}, #{expiresAt})")
    int insertSession(UserSession session);

    @Select("SELECT session_id AS sessionId, user_id AS userId, token_hash AS tokenHash, token_version AS tokenVersion, " +
            "ip_address AS ipAddress, user_agent AS userAgent, remember_me AS rememberMe, created_at AS createdAt, last_seen_at AS lastSeenAt, expires_at AS expiresAt " +
            "FROM user_sessions WHERE token_hash = #{tokenHash}")
    UserSession findByTokenHash(@Param("tokenHash") String tokenHash);

    @Select("SELECT session_id AS sessionId, user_id AS userId, token_hash AS tokenHash, token_version AS tokenVersion, " +
            "ip_address AS ipAddress, user_agent AS userAgent, remember_me AS rememberMe, created_at AS createdAt, last_seen_at AS lastSeenAt, expires_at AS expiresAt " +
            "FROM user_sessions WHERE session_id = #{sessionId}")
    UserSession findBySessionId(@Param("sessionId") String sessionId);

    @Select("SELECT session_id AS sessionId, user_id AS userId, ip_address AS ipAddress, user_agent AS userAgent, " +
            "remember_me AS rememberMe, created_at AS createdAt, last_seen_at AS lastSeenAt, expires_at AS expiresAt " +
            "FROM user_sessions WHERE user_id = #{userId} AND expires_at > #{now} ORDER BY last_seen_at DESC")
    List<UserSession> listActiveByUser(@Param("userId") int userId, @Param("now") long now);

    @Update("UPDATE user_sessions SET token_hash = #{newTokenHash}, token_version = #{tokenVersion}, last_seen_at = #{now}, expires_at = #{expiresAt} " +
            "WHERE session_id = #{sessionId}")
    int rotateRefreshToken(@Param("sessionId") String sessionId, @Param("newTokenHash") String newTokenHash, @Param("tokenVersion") int tokenVersion, @Param("now") long now, @Param("expiresAt") long expiresAt);

    @Update("UPDATE user_sessions SET last_seen_at = #{now} WHERE session_id = #{sessionId}")
    int touchSession(@Param("sessionId") String sessionId, @Param("now") long now);

    @Delete("DELETE FROM user_sessions WHERE session_id = #{sessionId} AND user_id = #{userId}")
    int deleteSessionForUser(@Param("sessionId") String sessionId, @Param("userId") int userId);

    @Delete("DELETE FROM user_sessions WHERE token_hash = #{tokenHash}")
    int deleteByTokenHash(@Param("tokenHash") String tokenHash);

    @Delete("DELETE FROM user_sessions WHERE user_id = #{userId} AND session_id != #{keepSessionId}")
    int deleteOtherSessions(@Param("userId") int userId, @Param("keepSessionId") String keepSessionId);

    @Delete("DELETE FROM user_sessions WHERE user_id = #{userId}")
    int deleteAllForUser(@Param("userId") int userId);

    @Delete("DELETE FROM user_sessions WHERE expires_at < #{now}")
    int deleteExpired(@Param("now") long now);

    // ── Login history ──

    @Insert("INSERT INTO login_history (user_id, ip_address, user_agent, success, fail_reason, login_time) " +
            "VALUES (#{userId}, #{ipAddress}, #{userAgent}, #{success}, #{failReason}, #{loginTime})")
    int insertLoginHistory(LoginHistoryEntry entry);

    @Select("SELECT id, user_id AS userId, ip_address AS ipAddress, user_agent AS userAgent, success, fail_reason AS failReason, login_time AS loginTime " +
            "FROM login_history WHERE user_id = #{userId} ORDER BY login_time DESC LIMIT #{limit}")
    List<LoginHistoryEntry> listLoginHistory(@Param("userId") int userId, @Param("limit") int limit);
}

