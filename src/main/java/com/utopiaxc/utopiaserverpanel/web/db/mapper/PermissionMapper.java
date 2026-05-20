package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * MyBatis mapper for the level-based permission system.
 * Uses the {@code role_permission_levels} table with 4 permission keys
 * (admin, dashboard, terminal, logs) each at level 0 (deny), 1 (readonly), or 2 (full).
 */
public interface PermissionMapper {

    // ────── Role permission levels ──────

    /** Get all permission levels for a role as a list of {permissionKey, level} maps. */
    @Select("SELECT permission_key, level FROM role_permission_levels WHERE role_id = #{roleId}")
    @Results({
            @Result(property = "permissionKey", column = "permission_key"),
            @Result(property = "level", column = "level")
    })
    List<Map<String, Object>> getRoleLevels(int roleId);

    /** Get a specific permission level for a user (via their role). Returns null if not found. */
    @Select("""
        SELECT rpl.level FROM role_permission_levels rpl
        JOIN users u ON u.role_id = rpl.role_id
        WHERE u.id = #{userId} AND u.is_active = 1 AND rpl.permission_key = #{permissionKey}
    """)
    Integer getUserPermissionLevel(@Param("userId") int userId, @Param("permissionKey") String permissionKey);

    /** Get all permission levels for a user (via their role). */
    @Select("""
        SELECT rpl.permission_key, rpl.level FROM role_permission_levels rpl
        JOIN users u ON u.role_id = rpl.role_id
        WHERE u.id = #{userId} AND u.is_active = 1
    """)
    @Results({
            @Result(property = "permissionKey", column = "permission_key"),
            @Result(property = "level", column = "level")
    })
    List<Map<String, Object>> getUserPermissionLevels(int userId);

    /** Set (upsert) a permission level for a role. */
    @Insert("INSERT OR REPLACE INTO role_permission_levels (role_id, permission_key, level) VALUES (#{roleId}, #{permissionKey}, #{level})")
    int setRolePermissionLevel(@Param("roleId") int roleId, @Param("permissionKey") String permissionKey, @Param("level") int level);

    /** Delete all permission levels for a role. */
    @Delete("DELETE FROM role_permission_levels WHERE role_id = #{roleId}")
    int deleteByRoleId(int roleId);

    /** Count how many permission entries exist for a role. */
    @Select("SELECT COUNT(*) FROM role_permission_levels WHERE role_id = #{roleId}")
    int countByRoleId(int roleId);

    /** Update role timestamp. */
    @Update("UPDATE roles SET updated_at = #{updatedAt} WHERE id = #{id}")
    int updateRoleTimestamp(Map<String, Object> params);
}
