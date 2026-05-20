package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * MyBatis mapper for users table.
 */
public interface UserMapper {

    @Select("SELECT id, username, password_hash, role_id, must_change_password, is_active, binding_status, created_at, updated_at FROM users WHERE username = #{username}")
    @Results(id = "userResult", value = {
            @Result(property = "id", column = "id"),
            @Result(property = "username", column = "username"),
            @Result(property = "passwordHash", column = "password_hash"),
            @Result(property = "roleId", column = "role_id"),
            @Result(property = "mustChangePassword", column = "must_change_password"),
            @Result(property = "isActive", column = "is_active"),
            @Result(property = "bindingStatus", column = "binding_status"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    Map<String, Object> findByUsername(String username);

    @Select("""
        SELECT u.id, u.username, u.role_id, r.name as role_name, u.must_change_password,
               u.is_active, u.binding_status, u.created_at, u.updated_at,
               pb.player_uuid, pb.player_name
        FROM users u
        JOIN roles r ON r.id = u.role_id
        LEFT JOIN player_bindings pb ON pb.user_id = u.id
        WHERE u.id = #{userId}
    """)
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "username", column = "username"),
            @Result(property = "roleId", column = "role_id"),
            @Result(property = "roleName", column = "role_name"),
            @Result(property = "mustChangePassword", column = "must_change_password"),
            @Result(property = "isActive", column = "is_active"),
            @Result(property = "bindingStatus", column = "binding_status"),
            @Result(property = "playerUuid", column = "player_uuid"),
            @Result(property = "playerName", column = "player_name"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    Map<String, Object> findById(int userId);

    @Select("""
        SELECT u.id, u.username, u.role_id, r.name as role_name, u.is_active,
               u.binding_status, u.must_change_password, u.created_at, u.updated_at,
               pb.player_name, pb.player_uuid
        FROM users u
        JOIN roles r ON r.id = u.role_id
        LEFT JOIN player_bindings pb ON pb.user_id = u.id
        ORDER BY u.id
    """)
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "username", column = "username"),
            @Result(property = "roleId", column = "role_id"),
            @Result(property = "roleName", column = "role_name"),
            @Result(property = "isActive", column = "is_active"),
            @Result(property = "bindingStatus", column = "binding_status"),
            @Result(property = "mustChangePassword", column = "must_change_password"),
            @Result(property = "playerName", column = "player_name"),
            @Result(property = "playerUuid", column = "player_uuid"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    List<Map<String, Object>> listAll();

    @Select("SELECT COUNT(*) FROM users WHERE username = #{username}")
    int countByUsername(String username);

    @Insert("INSERT INTO users (username, password_hash, role_id, must_change_password, is_active, binding_status, created_at, updated_at) VALUES (#{username}, #{passwordHash}, #{roleId}, #{mustChangePassword}, 1, #{bindingStatus}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Map<String, Object> user);

    @Update("UPDATE users SET role_id = #{roleId}, is_active = #{isActive}, updated_at = #{updatedAt} WHERE id = #{id}")
    int update(Map<String, Object> params);

    @Update("UPDATE users SET password_hash = #{passwordHash}, must_change_password = #{mustChangePassword}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updatePassword(Map<String, Object> params);

    @Update("UPDATE users SET binding_status = #{bindingStatus}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateBindingStatus(Map<String, Object> params);

    @Delete("DELETE FROM users WHERE id = #{id}")
    int delete(int id);

    @Select("SELECT password_hash FROM users WHERE id = #{id}")
    String getPasswordHash(int id);

    @Update("UPDATE users SET username = #{username}, updated_at = #{updatedAt} WHERE id = #{id}")
    int updateUsername(Map<String, Object> params);
}
