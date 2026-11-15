package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import com.utopiaxc.utopiaserverpanel.web.db.model.InGameChatEntry;
import com.utopiaxc.utopiaserverpanel.web.db.model.InSiteMessage;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface MessageMapper {

    String MSG_COLS = "id, msg_type AS msgType, sender_user_id AS senderUserId, sender_name AS senderName, " +
            "sender_uuid AS senderUuid, recipient_user_id AS recipientUserId, recipient_player_uuid AS recipientPlayerUuid, " +
            "recipient_name AS recipientName, title, content, related_type AS relatedType, related_id AS relatedId, " +
            "is_read AS read, delivered_ingame AS deliveredIngame, created_at AS createdAt ";

    @Insert("INSERT INTO in_site_messages " +
            "(msg_type, sender_user_id, sender_name, sender_uuid, recipient_user_id, recipient_player_uuid, recipient_name, " +
            "title, content, related_type, related_id, is_read, delivered_ingame, created_at) " +
            "VALUES (#{msgType}, #{senderUserId}, #{senderName}, #{senderUuid}, #{recipientUserId}, #{recipientPlayerUuid}, " +
            "#{recipientName}, #{title}, #{content}, #{relatedType}, #{relatedId}, #{read}, #{deliveredIngame}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertMessage(InSiteMessage msg);

    @Select("<script>SELECT " + MSG_COLS + "FROM in_site_messages " +
            "WHERE (recipient_user_id = #{userId} " +
            "  <if test='playerUuids != null and !playerUuids.isEmpty()'> OR recipient_player_uuid IN " +
            "    <foreach collection='playerUuids' item='uuid' open='(' separator=',' close=')'>#{uuid}</foreach>" +
            "  </if>) " +
            "  <if test='filterType != null and filterType != \"\" and filterType != \"all\"'> AND msg_type = #{filterType}</if> " +
            "ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<InSiteMessage> listInbox(
            @Param("userId") int userId,
            @Param("playerUuids") List<String> playerUuids,
            @Param("filterType") String filterType,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("SELECT " + MSG_COLS + "FROM in_site_messages " +
            "WHERE sender_user_id = #{userId} ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<InSiteMessage> listSent(@Param("userId") int userId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("<script>SELECT COUNT(*) FROM in_site_messages " +
            "WHERE is_read = 0 AND (recipient_user_id = #{userId} " +
            "  <if test='playerUuids != null and !playerUuids.isEmpty()'> OR recipient_player_uuid IN " +
            "    <foreach collection='playerUuids' item='uuid' open='(' separator=',' close=')'>#{uuid}</foreach>" +
            "  </if>)</script>")
    int countUnread(@Param("userId") int userId, @Param("playerUuids") List<String> playerUuids);

    @Update("<script>UPDATE in_site_messages SET is_read = 1 WHERE id = #{id} AND (recipient_user_id = #{userId} " +
            "<if test='playerUuids != null and !playerUuids.isEmpty()'> OR recipient_player_uuid IN " +
            "  <foreach collection='playerUuids' item='uuid' open='(' separator=',' close=')'>#{uuid}</foreach>" +
            "</if>)</script>")
    int markRead(@Param("id") int id, @Param("userId") int userId, @Param("playerUuids") List<String> playerUuids);

    @Update("<script>UPDATE in_site_messages SET is_read = 1 WHERE is_read = 0 AND (recipient_user_id = #{userId} " +
            "<if test='playerUuids != null and !playerUuids.isEmpty()'> OR recipient_player_uuid IN " +
            "  <foreach collection='playerUuids' item='uuid' open='(' separator=',' close=')'>#{uuid}</foreach>" +
            "</if>)</script>")
    int markAllRead(@Param("userId") int userId, @Param("playerUuids") List<String> playerUuids);

    @Select("<script>SELECT " + MSG_COLS + "FROM in_site_messages " +
            "WHERE is_read = 0 AND (recipient_player_uuid = #{playerUuid} " +
            "  <if test='userId != null'> OR recipient_user_id = #{userId}</if>) " +
            "ORDER BY created_at DESC, id DESC LIMIT #{limit}</script>")
    List<InSiteMessage> listUnreadForPlayer(
            @Param("playerUuid") String playerUuid,
            @Param("userId") Integer userId,
            @Param("limit") int limit);

    @Select("<script>SELECT COUNT(*) FROM in_site_messages " +
            "WHERE is_read = 0 AND delivered_ingame = 0 AND (recipient_player_uuid = #{playerUuid} " +
            "  <if test='userId != null'> OR recipient_user_id = #{userId}</if>)</script>")
    int countUndeliveredForPlayer(@Param("playerUuid") String playerUuid, @Param("userId") Integer userId);

    @Update("<script>UPDATE in_site_messages SET delivered_ingame = 1 WHERE delivered_ingame = 0 AND (recipient_player_uuid = #{playerUuid} " +
            "<if test='userId != null'> OR recipient_user_id = #{userId}</if>)</script>")
    int markDeliveredIngame(@Param("playerUuid") String playerUuid, @Param("userId") Integer userId);

    @Insert("INSERT INTO ingame_chat_logs " +
            "(channel, sender_uuid, sender_name, recipient_uuid, recipient_name, source, content, created_at) " +
            "VALUES (#{channel}, #{senderUuid}, #{senderName}, #{recipientUuid}, #{recipientName}, #{source}, #{content}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertChatLog(InGameChatEntry entry);

    @Select("SELECT id, channel, sender_uuid AS senderUuid, sender_name AS senderName, " +
            "recipient_uuid AS recipientUuid, recipient_name AS recipientName, source, content, created_at AS createdAt " +
            "FROM ingame_chat_logs WHERE channel = 'public' ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}")
    List<InGameChatEntry> listPublicChat(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT id, channel, sender_uuid AS senderUuid, sender_name AS senderName, " +
            "recipient_uuid AS recipientUuid, recipient_name AS recipientName, source, content, created_at AS createdAt " +
            "FROM ingame_chat_logs WHERE channel = 'public' AND id > #{afterId} ORDER BY id ASC LIMIT #{limit}")
    List<InGameChatEntry> listPublicChatAfter(@Param("afterId") int afterId, @Param("limit") int limit);

    @Select("<script>SELECT id, channel, sender_uuid AS senderUuid, sender_name AS senderName, " +
            "recipient_uuid AS recipientUuid, recipient_name AS recipientName, source, content, created_at AS createdAt " +
            "FROM ingame_chat_logs WHERE channel = 'whisper' " +
            "<if test='playerNames != null and !playerNames.isEmpty()'>" +
            "  AND (LOWER(sender_name) IN <foreach collection='playerNames' item='pname' open='(' separator=',' close=')'>#{pname}</foreach> " +
            "   OR LOWER(COALESCE(recipient_name,'')) IN <foreach collection='playerNames' item='pname2' open='(' separator=',' close=')'>#{pname2}</foreach>) " +
            "</if>" +
            "ORDER BY created_at DESC, id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<InGameChatEntry> listWhispers(@Param("playerNames") List<String> playerNames, @Param("offset") int offset, @Param("limit") int limit);

    @Delete("DELETE FROM ingame_chat_logs WHERE created_at < #{cutoffTs}")
    int pruneOldChats(@Param("cutoffTs") long cutoffTs);
}
