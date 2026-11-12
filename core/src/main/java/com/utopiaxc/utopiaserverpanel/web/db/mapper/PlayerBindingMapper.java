package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import com.utopiaxc.utopiaserverpanel.web.db.model.BindingCodeInfo;
import com.utopiaxc.utopiaserverpanel.web.db.model.PlayerBindingInfo;
import org.apache.ibatis.annotations.*;

public interface PlayerBindingMapper {

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

    @Insert("INSERT INTO player_bindings (user_id, player_uuid, bound_at) VALUES (#{userId}, #{playerUuid}, #{boundAt})")
    int insertBinding(@Param("userId") int userId, @Param("playerUuid") String playerUuid, @Param("boundAt") long boundAt);

    @Select("SELECT pb.id, pb.user_id AS userId, pb.player_uuid AS playerUuid, mp.player_name AS playerName, pb.bound_at AS boundAt " +
            "FROM player_bindings pb JOIN mc_players mp ON mp.player_uuid = pb.player_uuid " +
            "WHERE pb.user_id = #{userId}")
    PlayerBindingInfo findByUserId(@Param("userId") int userId);

    @Select("SELECT pb.id, pb.user_id AS userId, pb.player_uuid AS playerUuid, mp.player_name AS playerName, pb.bound_at AS boundAt " +
            "FROM player_bindings pb JOIN mc_players mp ON mp.player_uuid = pb.player_uuid " +
            "WHERE pb.player_uuid = #{playerUuid}")
    PlayerBindingInfo findByPlayerUuid(@Param("playerUuid") String playerUuid);

    @Select("SELECT COUNT(*) FROM player_bindings WHERE player_uuid = #{playerUuid}")
    int countByPlayerUuid(@Param("playerUuid") String playerUuid);

    @Delete("DELETE FROM player_bindings WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") int userId);

    @Delete("DELETE FROM player_bindings WHERE player_uuid = #{playerUuid}")
    int deleteByPlayerUuid(@Param("playerUuid") String playerUuid);
}

