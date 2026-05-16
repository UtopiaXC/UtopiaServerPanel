package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * MyBatis mapper for roles table.
 */
public interface RoleMapper {

    @Select("""
        SELECT r.id, r.name, r.description, r.is_system, r.is_immutable, r.created_at, r.updated_at,
               (SELECT COUNT(*) FROM users u WHERE u.role_id = r.id) as user_count
        FROM roles r ORDER BY r.id
    """)
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "name", column = "name"),
            @Result(property = "description", column = "description"),
            @Result(property = "isSystem", column = "is_system"),
            @Result(property = "isImmutable", column = "is_immutable"),
            @Result(property = "userCount", column = "user_count"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    List<Map<String, Object>> listAll();

    @Select("SELECT id, name, description, is_system, is_immutable, created_at, updated_at FROM roles WHERE id = #{id}")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "name", column = "name"),
            @Result(property = "description", column = "description"),
            @Result(property = "isSystem", column = "is_system"),
            @Result(property = "isImmutable", column = "is_immutable"),
            @Result(property = "createdAt", column = "created_at"),
            @Result(property = "updatedAt", column = "updated_at")
    })
    Map<String, Object> findById(int id);

    @Select("SELECT COUNT(*) FROM roles WHERE name = #{name}")
    int countByName(String name);

    @Select("SELECT COUNT(*) FROM users WHERE role_id = #{roleId}")
    int countUsersByRoleId(int roleId);

    @Insert("INSERT INTO roles (name, description, is_system, is_immutable, created_at, updated_at) VALUES (#{name}, #{description}, 0, 0, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Map<String, Object> role);

    @Update("UPDATE roles SET name = #{name}, description = #{description}, updated_at = #{updatedAt} WHERE id = #{id}")
    int update(Map<String, Object> params);

    @Delete("DELETE FROM roles WHERE id = #{id}")
    int delete(int id);

    @Insert("INSERT OR IGNORE INTO roles (id, name, description, is_system, is_immutable, created_at, updated_at) VALUES (#{id}, #{name}, #{description}, #{isSystem}, #{isImmutable}, #{createdAt}, #{updatedAt})")
    int insertSeed(Map<String, Object> role);
}
