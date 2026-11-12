package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import com.utopiaxc.utopiaserverpanel.web.db.model.PermissionDef;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

public interface PermissionMapper {

    @Select("SELECT perm_key AS permKey, category, sort_order AS sortOrder, is_active AS active FROM permissions WHERE is_active = 1 ORDER BY sort_order ASC, perm_key ASC")
    List<PermissionDef> listActiveDefinitions();

    @Insert("INSERT INTO permissions (perm_key, category, sort_order, is_active) VALUES (#{permKey}, #{category}, #{sortOrder}, #{active}) " +
            "ON CONFLICT(perm_key) DO UPDATE SET category = excluded.category, sort_order = excluded.sort_order, is_active = excluded.is_active")
    int upsertDefinition(PermissionDef def);

    @Select("SELECT rp.level FROM users u " +
            "LEFT JOIN role_permissions rp ON u.role_id = rp.role_id AND rp.perm_key = #{permKey} " +
            "WHERE u.id = #{userId} AND u.status = 'active'")
    Integer getUserPermissionLevel(@Param("userId") int userId, @Param("permKey") String permKey);

    @Select("SELECT p.perm_key AS permissionKey, COALESCE(rp.level, 0) AS level " +
            "FROM permissions p " +
            "LEFT JOIN users u ON u.id = #{userId} AND u.status = 'active' " +
            "LEFT JOIN role_permissions rp ON rp.role_id = u.role_id AND rp.perm_key = p.perm_key " +
            "WHERE p.is_active = 1 ORDER BY p.sort_order ASC")
    List<Map<String, Object>> getUserPermissionLevels(@Param("userId") int userId);

    @Select("SELECT p.perm_key AS permissionKey, COALESCE(rp.level, 0) AS level " +
            "FROM permissions p " +
            "LEFT JOIN role_permissions rp ON rp.role_id = #{roleId} AND rp.perm_key = p.perm_key " +
            "WHERE p.is_active = 1 ORDER BY p.sort_order ASC")
    List<Map<String, Object>> getRoleLevels(@Param("roleId") int roleId);

    @Select("SELECT role_id AS roleId, perm_key AS permKey, level FROM role_permissions")
    List<Map<String, Object>> getAllRoleLevels();

    @Insert("INSERT INTO role_permissions (role_id, perm_key, level) VALUES (#{roleId}, #{permKey}, #{level}) " +
            "ON CONFLICT(role_id, perm_key) DO UPDATE SET level = excluded.level")
    int setRolePermissionLevel(@Param("roleId") int roleId, @Param("permKey") String permKey, @Param("level") int level);

    @Delete("DELETE FROM role_permissions WHERE role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") int roleId);
}
