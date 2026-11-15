package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import com.utopiaxc.utopiaserverpanel.web.db.model.BindingCodeInfo;
import com.utopiaxc.utopiaserverpanel.web.db.model.PlayerBindingInfo;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface PlayerBindingMapper {

    String BASE_BINDING_SELECT =
            "SELECT pb.id, pb.user_id AS userId, pb.player_uuid AS playerUuid, " +
            "mp.player_name AS playerName, pb.is_primary AS \"primary\", pb.bound_at AS boundAt " +
            "FROM player_bindings pb JOIN mc_players mp ON mp.player_uuid = pb.player_uuid ";

    @Insert("INSERT INTO mc_players (player_uuid, player_name, updated_at) VALUES (#{uuid}, #{name}, #{updatedAt}) " +
            "ON CONFLICT(player_uuid) DO UPDATE SET player_name = excluded.player_name, updated_at = excluded.updated_at")
    int upsertMcPlayer(@Param("uuid") String uuid, @Param("name") String name, @Param("updatedAt") long updatedAt);

    @Update("UPDATE binding_codes SET consumed_at = #{now} WHERE player_uuid = #{playerUuid} AND consumed_at IS NULL")
    int invalidatePlayerCodes(@Param("playerUuid") String playerUuid, @Param("now") long now);

    @Insert("INSERT INTO binding_codes (code, player_uuid, created_at, expires_at, consumed_at) " +
            "VALUES (#{code}, #{playerUuid}, #{createdAt}, #{expiresAt}, NULL)")
    int insertCode(@Param("code") String code, @Param("playerUuid") String playerUuid, @Param("createdAt") long createdAt, @Param("expiresAt") long expiresAt);

    @Select("SELECT bc.code, bc.player_uuid AS playerUuid, mp.player_name AS playerName, bc.created_at AS createdAt, bc.expires_at AS expiresAt, bc.consumed_at AS consumedAt " +
            "FROM binding_codes bc JOIN mc_players mp ON mp.player_uuid = bc.player_uuid " +
            "WHERE bc.code = #{code}")
    BindingCodeInfo findByCode(@Param("code") String code);

    @Update("UPDATE binding_codes SET consumed_at = #{now} WHERE code = #{code} AND consumed_at IS NULL")
    int markCodeConsumed(@Param("code") String code, @Param("now") long now);

    @Insert("INSERT INTO player_bindings (user_id, player_uuid, is_primary, bound_at) VALUES (#{userId}, #{playerUuid}, #{isPrimary}, #{boundAt})")
    int insertBinding(@Param("userId") int userId, @Param("playerUuid") String playerUuid, @Param("isPrimary") int isPrimary, @Param("boundAt") long boundAt);

    @Select(BASE_BINDING_SELECT + "WHERE pb.user_id = #{userId} ORDER BY pb.is_primary DESC, pb.bound_at ASC, pb.id ASC LIMIT 1")
    PlayerBindingInfo findByUserId(@Param("userId") int userId);

    @Select(BASE_BINDING_SELECT + "WHERE pb.user_id = #{userId} ORDER BY pb.is_primary DESC, pb.bound_at ASC, pb.id ASC")
    List<PlayerBindingInfo> listByUserId(@Param("userId") int userId);

    @Select(BASE_BINDING_SELECT + "WHERE pb.user_id = #{userId} AND pb.player_uuid = #{playerUuid} LIMIT 1")
    PlayerBindingInfo findByUserAndUuid(@Param("userId") int userId, @Param("playerUuid") String playerUuid);

    @Select(BASE_BINDING_SELECT + "WHERE pb.player_uuid = #{playerUuid}")
    PlayerBindingInfo findByPlayerUuid(@Param("playerUuid") String playerUuid);

    @Select(BASE_BINDING_SELECT + "WHERE LOWER(mp.player_name) = LOWER(#{playerName}) LIMIT 1")
    PlayerBindingInfo findByPlayerName(@Param("playerName") String playerName);

    @Select("SELECT COUNT(*) FROM player_bindings WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") int userId);

    @Select("SELECT COUNT(*) FROM player_bindings WHERE player_uuid = #{playerUuid}")
    int countByPlayerUuid(@Param("playerUuid") String playerUuid);

    @Update("UPDATE player_bindings SET is_primary = 0 WHERE user_id = #{userId}")
    int clearPrimaryForUser(@Param("userId") int userId);

    @Update("UPDATE player_bindings SET is_primary = 1 WHERE user_id = #{userId} AND player_uuid = #{playerUuid}")
    int setPrimaryForUserAndUuid(@Param("userId") int userId, @Param("playerUuid") String playerUuid);

    @Update("UPDATE player_bindings SET is_primary = 1 WHERE id = (" +
            "SELECT id FROM player_bindings WHERE user_id = #{userId} ORDER BY is_primary DESC, bound_at ASC, id ASC LIMIT 1" +
            ")")
    int ensurePrimaryExists(@Param("userId") int userId);

    @Delete("DELETE FROM player_bindings WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") int userId);

    @Delete("DELETE FROM player_bindings WHERE user_id = #{userId} AND player_uuid = #{playerUuid}")
    int deleteByUserAndUuid(@Param("userId") int userId, @Param("playerUuid") String playerUuid);

    @Delete("DELETE FROM player_bindings WHERE player_uuid = #{playerUuid}")
    int deleteByPlayerUuid(@Param("playerUuid") String playerUuid);
}

