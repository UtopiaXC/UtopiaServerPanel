package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

public interface MonitorMapper {

    @Insert("INSERT INTO perf_logs (ts, cpu, mem_jvm, mem_jvm_max, mem_sys_used, mem_sys_total, tps, disk_used, disk_total, game_folder, online_count) " +
            "VALUES (#{ts}, #{cpu}, #{memJvm}, #{memJvmMax}, #{memSysUsed}, #{memSysTotal}, #{tps}, #{diskUsed}, #{diskTotal}, #{gameFolder}, #{onlineCount})")
    int insertPerfLog(Map<String, Object> log);

    @Select("SELECT id, ts, cpu, mem_jvm AS memJvm, mem_jvm_max AS memJvmMax, mem_sys_used AS memSysUsed, " +
            "mem_sys_total AS memSysTotal, tps, disk_used AS diskUsed, disk_total AS diskTotal, " +
            "game_folder AS gameFolder, online_count AS onlineCount " +
            "FROM perf_logs WHERE ts >= #{startTs} AND ts <= #{endTs} ORDER BY ts ASC")
    List<Map<String, Object>> queryPerfLogs(@Param("startTs") long startTs, @Param("endTs") long endTs);

    @Delete("DELETE FROM perf_logs WHERE ts < #{beforeTs}")
    int deleteOldPerfLogs(@Param("beforeTs") long beforeTs);

    // ────── 3NF Player Events (player_uuid -> mc_players) ──────

    @Insert("INSERT INTO player_events (ts, player_uuid, event_type) VALUES (#{ts}, #{playerUuid}, #{eventType})")
    int insertPlayerEvent(@Param("ts") long ts, @Param("playerUuid") String playerUuid, @Param("eventType") int eventType);

    @Select("SELECT pe.id, pe.ts, mp.player_name AS playerName, pe.player_uuid AS playerUuid, pe.event_type AS eventType " +
            "FROM player_events pe JOIN mc_players mp ON mp.player_uuid = pe.player_uuid " +
            "WHERE pe.ts >= #{startTs} AND pe.ts <= #{endTs} ORDER BY pe.ts ASC, pe.id ASC")
    List<Map<String, Object>> queryPlayerEvents(@Param("startTs") long startTs, @Param("endTs") long endTs);

    @Select("SELECT online_count FROM perf_logs WHERE ts <= #{ts} ORDER BY ts DESC LIMIT 1")
    Integer getOnlineCountAt(@Param("ts") long ts);

    @Select("SELECT mp.player_name AS playerName " +
            "FROM player_events pe " +
            "INNER JOIN (SELECT player_uuid, MAX(id) AS max_id FROM player_events WHERE ts <= #{ts} GROUP BY player_uuid) latest " +
            "ON pe.id = latest.max_id " +
            "INNER JOIN mc_players mp ON mp.player_uuid = pe.player_uuid " +
            "WHERE pe.event_type = 1")
    List<Map<String, Object>> getOnlinePlayersAt(@Param("ts") long ts);

    @Delete("DELETE FROM player_events WHERE ts < #{beforeTs}")
    int deleteOldPlayerEvents(@Param("beforeTs") long beforeTs);

    // ────── Server Lifecycle ──────

    @Insert("INSERT INTO server_lifecycle (ts, event_type, detail) VALUES (#{ts}, #{eventType}, #{detail})")
    int insertLifecycleEvent(Map<String, Object> event);

    @Select("SELECT id, ts, event_type AS eventType, detail FROM server_lifecycle " +
            "WHERE ts >= #{startTs} AND ts <= #{endTs} ORDER BY ts ASC")
    List<Map<String, Object>> queryLifecycleRange(@Param("startTs") long startTs, @Param("endTs") long endTs);

    @Select("SELECT id, ts, event_type AS eventType, detail FROM server_lifecycle " +
            "ORDER BY ts DESC LIMIT #{size} OFFSET #{offset}")
    List<Map<String, Object>> queryLifecyclePaged(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM server_lifecycle")
    int countLifecycle();

    @Select("SELECT id, ts, event_type AS eventType, detail FROM server_lifecycle ORDER BY ts DESC LIMIT 1")
    Map<String, Object> getLastLifecycleEvent();

    @Delete("DELETE FROM server_lifecycle WHERE ts < #{beforeTs}")
    int deleteOldLifecycle(@Param("beforeTs") long beforeTs);
}
