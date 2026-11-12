package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import com.utopiaxc.utopiaserverpanel.web.db.model.ApiToken;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface ApiTokenMapper {

    @Insert("INSERT INTO api_tokens (user_id, name, prefix, token_hash, scopes, expires_at, last_used_at, created_at) " +
            "VALUES (#{userId}, #{name}, #{prefix}, #{tokenHash}, #{scopes}, #{expiresAt}, NULL, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ApiToken token);

    @Select("SELECT id, user_id AS userId, name, prefix, scopes, expires_at AS expiresAt, last_used_at AS lastUsedAt, created_at AS createdAt " +
            "FROM api_tokens WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<ApiToken> listByUser(@Param("userId") int userId);

    @Select("SELECT t.id, t.user_id AS userId, t.name, t.prefix, t.token_hash AS tokenHash, t.scopes, t.expires_at AS expiresAt, t.last_used_at AS lastUsedAt, t.created_at AS createdAt, " +
            "u.username, u.role_id AS roleId, u.status AS userStatus " +
            "FROM api_tokens t JOIN users u ON u.id = t.user_id WHERE t.token_hash = #{tokenHash}")
    ApiToken findByTokenHash(@Param("tokenHash") String tokenHash);

    @Update("UPDATE api_tokens SET last_used_at = #{now} WHERE id = #{id}")
    int updateLastUsed(@Param("id") int id, @Param("now") long now);

    @Delete("DELETE FROM api_tokens WHERE id = #{id} AND user_id = #{userId}")
    int deleteForUser(@Param("id") int id, @Param("userId") int userId);
}

