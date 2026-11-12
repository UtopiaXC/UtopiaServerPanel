package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import com.utopiaxc.utopiaserverpanel.web.db.model.UserAccount;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface UserMapper {

    String BASE_SELECT =
            "SELECT u.id, u.username, u.nickname, u.email, u.password_hash AS passwordHash, " +
            "u.role_id AS roleId, r.name AS roleName, r.display_name AS roleDisplayName, " +
            "u.status, u.must_change_password AS mustChangePassword, u.require_2fa AS require2fa, " +
            "u.failed_login_attempts AS failedLoginAttempts, u.lockout_until AS lockoutUntil, " +
            "u.token_version AS tokenVersion, u.locale, u.created_at AS createdAt, u.updated_at AS updatedAt, " +
            "pb.player_uuid AS playerUuid, mp.player_name AS playerName, pb.bound_at AS boundAt, " +
            "COALESCE(ut.enabled, 0) AS totpEnabled " +
            "FROM users u " +
            "LEFT JOIN roles r ON u.role_id = r.id " +
            "LEFT JOIN player_bindings pb ON pb.user_id = u.id " +
            "LEFT JOIN mc_players mp ON mp.player_uuid = pb.player_uuid " +
            "LEFT JOIN user_totp ut ON ut.user_id = u.id ";

    @Select(BASE_SELECT + "WHERE u.id = #{id}")
    UserAccount findById(@Param("id") int id);

    @Select(BASE_SELECT + "WHERE LOWER(u.username) = LOWER(#{username})")
    UserAccount findByUsername(@Param("username") String username);

    @Select(BASE_SELECT + "WHERE LOWER(u.email) = LOWER(#{email})")
    UserAccount findByEmail(@Param("email") String email);

    @Select(BASE_SELECT + "WHERE LOWER(u.username) = LOWER(#{account}) OR (u.email IS NOT NULL AND LOWER(u.email) = LOWER(#{account})) LIMIT 1")
    UserAccount findByUsernameOrEmail(@Param("account") String account);

    @Select("<script>" + BASE_SELECT +
            "<where>" +
            "  <if test='search != null and search != \"\"'>" +
            "    (LOWER(u.username) LIKE '%' || LOWER(#{search}) || '%' " +
            "     OR LOWER(u.nickname) LIKE '%' || LOWER(#{search}) || '%' " +
            "     OR LOWER(COALESCE(u.email,'')) LIKE '%' || LOWER(#{search}) || '%' " +
            "     OR LOWER(COALESCE(mp.player_name,'')) LIKE '%' || LOWER(#{search}) || '%')" +
            "  </if>" +
            "  <if test='roleId != null'> AND u.role_id = #{roleId}</if>" +
            "  <if test='status != null and status != \"\"'> AND u.status = #{status}</if>" +
            "</where>" +
            "<choose>" +
            "  <when test='sortBy == \"username\" and sortOrder == \"desc\"'> ORDER BY u.username DESC</when>" +
            "  <when test='sortBy == \"username\"'> ORDER BY u.username ASC</when>" +
            "  <when test='sortBy == \"roleId\" and sortOrder == \"desc\"'> ORDER BY u.role_id DESC</when>" +
            "  <when test='sortBy == \"roleId\"'> ORDER BY u.role_id ASC</when>" +
            "  <when test='sortBy == \"status\" and sortOrder == \"desc\"'> ORDER BY u.status DESC</when>" +
            "  <when test='sortBy == \"status\"'> ORDER BY u.status ASC</when>" +
            "  <when test='sortBy == \"createdAt\" and sortOrder == \"asc\"'> ORDER BY u.created_at ASC</when>" +
            "  <when test='sortBy == \"createdAt\"'> ORDER BY u.created_at DESC</when>" +
            "  <when test='sortOrder == \"desc\"'> ORDER BY u.id DESC</when>" +
            "  <otherwise> ORDER BY u.id ASC</otherwise>" +
            "</choose>" +
            " LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<UserAccount> listFiltered(
            @Param("search") String search,
            @Param("roleId") Integer roleId,
            @Param("status") String status,
            @Param("sortBy") String sortBy,
            @Param("sortOrder") String sortOrder,
            @Param("offset") int offset,
            @Param("limit") int limit);

    @Select("<script>SELECT COUNT(*) FROM users u " +
            "LEFT JOIN player_bindings pb ON pb.user_id = u.id " +
            "LEFT JOIN mc_players mp ON mp.player_uuid = pb.player_uuid " +
            "<where>" +
            "  <if test='search != null and search != \"\"'>" +
            "    (LOWER(u.username) LIKE '%' || LOWER(#{search}) || '%' " +
            "     OR LOWER(u.nickname) LIKE '%' || LOWER(#{search}) || '%' " +
            "     OR LOWER(COALESCE(u.email,'')) LIKE '%' || LOWER(#{search}) || '%' " +
            "     OR LOWER(COALESCE(mp.player_name,'')) LIKE '%' || LOWER(#{search}) || '%')" +
            "  </if>" +
            "  <if test='roleId != null'> AND u.role_id = #{roleId}</if>" +
            "  <if test='status != null and status != \"\"'> AND u.status = #{status}</if>" +
            "</where></script>")
    int countFiltered(@Param("search") String search, @Param("roleId") Integer roleId, @Param("status") String status);

    @Select("SELECT COUNT(*) FROM users WHERE LOWER(username) = LOWER(#{username})")
    int countByUsername(@Param("username") String username);

    @Select("SELECT COUNT(*) FROM users WHERE email IS NOT NULL AND LOWER(email) = LOWER(#{email}) AND id != #{excludeId}")
    int countByEmailExclude(@Param("email") String email, @Param("excludeId") int excludeId);

    @Select("SELECT COUNT(*) FROM users WHERE role_id = 1 AND status = 'active'")
    int countActiveAdmins();

    @Select("SELECT COUNT(*) FROM users")
    int countAllUsers();

    @Insert("INSERT INTO users (username, nickname, email, password_hash, role_id, status, must_change_password, require_2fa, failed_login_attempts, lockout_until, token_version, locale, created_at, updated_at) " +
            "VALUES (#{username}, #{nickname}, #{email}, #{passwordHash}, #{roleId}, #{status}, #{mustChangePassword}, #{require2fa}, 0, NULL, 1, #{locale}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserAccount user);

    @Update("UPDATE users SET nickname = #{nickname}, email = #{email}, locale = #{locale}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateProfile(@Param("id") int id, @Param("nickname") String nickname, @Param("email") String email, @Param("locale") String locale, @Param("updatedAt") long updatedAt);

    @Update("UPDATE users SET username = #{username}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateUsername(@Param("id") int id, @Param("username") String username, @Param("updatedAt") long updatedAt);

    @Update("UPDATE users SET password_hash = #{passwordHash}, must_change_password = #{mustChangePassword}, token_version = token_version + 1, updated_at = #{updatedAt} WHERE id = #{id}")
    int updatePassword(@Param("id") int id, @Param("passwordHash") String passwordHash, @Param("mustChangePassword") int mustChangePassword, @Param("updatedAt") long updatedAt);

    @Update("UPDATE users SET role_id = #{roleId}, status = #{status}, nickname = #{nickname}, email = #{email}, " +
            "must_change_password = #{mustChangePassword}, require_2fa = #{require2fa}, updated_at = #{updatedAt} WHERE id = #{id}")
    int adminUpdate(UserAccount user);

    @Update("UPDATE users SET status = #{status}, token_version = token_version + 1, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateStatus(@Param("id") int id, @Param("status") String status, @Param("updatedAt") long updatedAt);

    @Update("UPDATE users SET failed_login_attempts = #{attempts}, lockout_until = #{lockoutUntil}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateLoginFailures(@Param("id") int id, @Param("attempts") int attempts, @Param("lockoutUntil") Long lockoutUntil, @Param("updatedAt") long updatedAt);

    @Update("UPDATE users SET token_version = token_version + 1, updated_at = #{updatedAt} WHERE id = #{id}")
    int bumpTokenVersion(@Param("id") int id, @Param("updatedAt") long updatedAt);

    @Delete("DELETE FROM users WHERE id = #{id}")
    int deleteById(@Param("id") int id);
}
