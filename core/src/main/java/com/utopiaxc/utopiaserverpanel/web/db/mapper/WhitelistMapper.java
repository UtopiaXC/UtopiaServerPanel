package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import com.utopiaxc.utopiaserverpanel.web.db.model.WhitelistApplication;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface WhitelistMapper {

    String COLS = "id, username, online_uuid AS onlineUuid, offline_uuid AS offlineUuid, " +
            "preferred_uuid_mode AS preferredUuidMode, email, reason, social_account AS socialAccount, " +
            "status, applicant_user_id AS applicantUserId, bind_on_approve AS bindOnApprove, " +
            "submit_ip AS submitIp, reviewer_user_id AS reviewerUserId, review_note AS reviewNote, " +
            "created_at AS createdAt, reviewed_at AS reviewedAt ";

    @Select("<script>SELECT " + COLS + "FROM whitelist_applications " +
            "<where>" +
            "  <if test='status != null and status != \"\" and status != \"all\"'> status = #{status}</if>" +
            "  <if test='search != null and search != \"\"'>" +
            "    AND (LOWER(username) LIKE '%' || LOWER(#{search}) || '%' OR LOWER(email) LIKE '%' || LOWER(#{search}) || '%')" +
            "  </if>" +
            "</where>" +
            " ORDER BY CASE WHEN status = 'pending' THEN 0 ELSE 1 END, created_at DESC, id DESC " +
            "LIMIT #{limit} OFFSET #{offset}</script>")
    List<WhitelistApplication> listApplications(
            @Param("status") String status,
            @Param("search") String search,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("<script>SELECT COUNT(*) FROM whitelist_applications " +
            "<where>" +
            "  <if test='status != null and status != \"\" and status != \"all\"'> status = #{status}</if>" +
            "  <if test='search != null and search != \"\"'>" +
            "    AND (LOWER(username) LIKE '%' || LOWER(#{search}) || '%' OR LOWER(email) LIKE '%' || LOWER(#{search}) || '%')" +
            "  </if>" +
            "</where></script>")
    int countApplications(@Param("status") String status, @Param("search") String search);

    @Select("SELECT " + COLS + "FROM whitelist_applications WHERE id = #{id}")
    WhitelistApplication findById(@Param("id") int id);

    @Select("SELECT " + COLS + "FROM whitelist_applications WHERE applicant_user_id = #{userId} ORDER BY created_at DESC, id DESC LIMIT 20")
    List<WhitelistApplication> listByUserId(@Param("userId") int userId);

    @Select("SELECT COUNT(*) FROM whitelist_applications WHERE LOWER(username) = LOWER(#{username}) AND status = 'pending'")
    int countPendingByUsername(@Param("username") String username);

    @Select("SELECT COUNT(*) FROM whitelist_applications WHERE submit_ip = #{ip} AND status = 'pending'")
    int countPendingByIp(@Param("ip") String ip);

    @Select("SELECT MAX(created_at) FROM whitelist_applications WHERE submit_ip = #{ip}")
    Long getLatestSubmitTsByIp(@Param("ip") String ip);

    @Insert("INSERT INTO whitelist_applications " +
            "(username, online_uuid, offline_uuid, preferred_uuid_mode, email, reason, social_account, " +
            "status, applicant_user_id, bind_on_approve, submit_ip, review_note, created_at) " +
            "VALUES (#{username}, #{onlineUuid}, #{offlineUuid}, #{preferredUuidMode}, #{email}, #{reason}, #{socialAccount}, " +
            "#{status}, #{applicantUserId}, #{bindOnApprove}, #{submitIp}, '', #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(WhitelistApplication app);

    @Update("UPDATE whitelist_applications SET status = #{status}, reviewer_user_id = #{reviewerUserId}, " +
            "review_note = #{reviewNote}, reviewed_at = #{reviewedAt} WHERE id = #{id}")
    int updateReview(
            @Param("id") int id,
            @Param("status") String status,
            @Param("reviewerUserId") Integer reviewerUserId,
            @Param("reviewNote") String reviewNote,
            @Param("reviewedAt") long reviewedAt);
}
