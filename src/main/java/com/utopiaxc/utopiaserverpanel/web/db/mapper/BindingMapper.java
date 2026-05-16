package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import org.apache.ibatis.annotations.*;

import java.util.Map;

/**
 * MyBatis mapper for player_bindings and binding_codes tables.
 */
public interface BindingMapper {

    // ────── Player bindings ──────

    @Select("SELECT user_id, player_uuid, player_name, bound_at FROM player_bindings WHERE user_id = #{userId}")
    @Results({
            @Result(property = "userId", column = "user_id"),
            @Result(property = "playerUuid", column = "player_uuid"),
            @Result(property = "playerName", column = "player_name"),
            @Result(property = "boundAt", column = "bound_at")
    })
    Map<String, Object> findByUserId(int userId);

    @Select("SELECT user_id FROM player_bindings WHERE player_uuid = #{playerUuid}")
    Integer findUserIdByPlayerUuid(String playerUuid);

    @Select("SELECT COUNT(*) FROM player_bindings WHERE player_uuid = #{playerUuid}")
    int countByPlayerUuid(String playerUuid);

    @Insert("INSERT INTO player_bindings (user_id, player_uuid, player_name, bound_at) VALUES (#{userId}, #{playerUuid}, #{playerName}, #{boundAt})")
    int insertBinding(Map<String, Object> binding);

    @Delete("DELETE FROM player_bindings WHERE user_id = #{userId}")
    int deleteByUserId(int userId);

    @Delete("DELETE FROM player_bindings WHERE player_uuid = #{playerUuid}")
    int deleteByPlayerUuid(String playerUuid);

    // ────── Binding codes ──────

    @Insert("INSERT INTO binding_codes (code, player_uuid, player_name, created_at, expires_at, used) VALUES (#{code}, #{playerUuid}, #{playerName}, #{createdAt}, #{expiresAt}, 0)")
    int insertCode(Map<String, Object> code);

    @Select("SELECT code, player_uuid, player_name, expires_at, used FROM binding_codes WHERE code = #{code}")
    @Results({
            @Result(property = "code", column = "code"),
            @Result(property = "playerUuid", column = "player_uuid"),
            @Result(property = "playerName", column = "player_name"),
            @Result(property = "expiresAt", column = "expires_at"),
            @Result(property = "used", column = "used")
    })
    Map<String, Object> findByCode(String code);

    @Update("UPDATE binding_codes SET used = 1 WHERE code = #{code}")
    int markCodeUsed(String code);

    @Update("UPDATE binding_codes SET used = 1 WHERE player_uuid = #{playerUuid} AND used = 0")
    int invalidatePlayerCodes(String playerUuid);
}
