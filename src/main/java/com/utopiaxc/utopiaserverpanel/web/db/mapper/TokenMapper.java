package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import org.apache.ibatis.annotations.*;

import java.util.Map;

/**
 * MyBatis mapper for refresh_tokens and server_config tables.
 */
public interface TokenMapper {

    // ────── Refresh tokens ──────

    @Insert("INSERT INTO refresh_tokens (user_id, token_hash, expires_at, created_at) VALUES (#{userId}, #{tokenHash}, #{expiresAt}, #{createdAt})")
    int insertToken(Map<String, Object> token);

    @Select("""
        SELECT rt.user_id, rt.expires_at, u.username, u.role_id, u.must_change_password, u.is_active
        FROM refresh_tokens rt JOIN users u ON u.id = rt.user_id
        WHERE rt.token_hash = #{tokenHash}
    """)
    @Results({
            @Result(property = "userId", column = "user_id"),
            @Result(property = "expiresAt", column = "expires_at"),
            @Result(property = "username", column = "username"),
            @Result(property = "roleId", column = "role_id"),
            @Result(property = "mustChangePassword", column = "must_change_password"),
            @Result(property = "isActive", column = "is_active")
    })
    Map<String, Object> findTokenByHash(String tokenHash);

    @Delete("DELETE FROM refresh_tokens WHERE token_hash = #{tokenHash}")
    int deleteByHash(String tokenHash);

    @Delete("DELETE FROM refresh_tokens WHERE user_id = #{userId}")
    int deleteByUserId(int userId);

    // ────── Server config ──────

    @Select("SELECT value FROM server_config WHERE key = #{key}")
    String getConfig(String key);

    @Insert("INSERT OR REPLACE INTO server_config (key, value) VALUES (#{key}, #{value})")
    int setConfig(@Param("key") String key, @Param("value") String value);
}
