package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * MyBatis mapper for permissions and role_permissions tables.
 */
public interface PermissionMapper {

    // ────── Permission definitions ──────

    @Select("SELECT permission_key, module, group_name, permission_type, description FROM permission_definitions ORDER BY module, group_name, permission_type")
    @Results({
            @Result(property = "key", column = "permission_key"),
            @Result(property = "module", column = "module"),
            @Result(property = "groupName", column = "group_name"),
            @Result(property = "type", column = "permission_type"),
            @Result(property = "description", column = "description")
    })
    List<Map<String, Object>> getAllDefinitions();

    @Select("SELECT permission_key FROM permission_definitions ORDER BY module, group_name, permission_type")
    List<String> getAllKeys();

    @Insert("INSERT OR IGNORE INTO permission_definitions (permission_key, module, group_name, permission_type, description) VALUES (#{key}, #{module}, #{groupName}, #{type}, #{description})")
    int insertDefinition(Map<String, Object> perm);

    // ────── Role permissions ──────

    @Select("SELECT permission_key FROM role_permissions WHERE role_id = #{roleId}")
    List<String> getKeysByRoleId(int roleId);

    @Select("""
        SELECT rp.permission_key FROM role_permissions rp
        JOIN users u ON u.role_id = rp.role_id
        WHERE u.id = #{userId} AND u.is_active = 1
    """)
    List<String> getKeysByUserId(int userId);

    @Select("""
        SELECT COUNT(*) FROM role_permissions rp
        JOIN users u ON u.role_id = rp.role_id
        WHERE u.id = #{userId} AND u.is_active = 1 AND rp.permission_key = #{permissionKey}
    """)
    int countUserPermission(@Param("userId") int userId, @Param("permissionKey") String permissionKey);

    @Delete("DELETE FROM role_permissions WHERE role_id = #{roleId}")
    int deleteByRoleId(int roleId);

    @Insert("INSERT OR IGNORE INTO role_permissions (role_id, permission_key) VALUES (#{roleId}, #{permissionKey})")
    int insertRolePermission(@Param("roleId") int roleId, @Param("permissionKey") String permissionKey);

    @Insert("INSERT OR IGNORE INTO role_permissions (role_id, permission_key) SELECT #{roleId}, permission_key FROM permission_definitions")
    int grantAllToRole(int roleId);

    @Update("UPDATE roles SET updated_at = #{updatedAt} WHERE id = #{id}")
    int updateRoleTimestamp(Map<String, Object> params);
}
