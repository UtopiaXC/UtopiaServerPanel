package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import com.utopiaxc.utopiaserverpanel.web.db.model.RoleInfo;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface RoleMapper {

    @Select("SELECT r.id, r.name, r.display_name AS displayName, r.description, " +
            "r.is_system AS systemRole, r.is_immutable AS immutable, r.created_at AS createdAt, r.updated_at AS updatedAt, " +
            "(SELECT COUNT(*) FROM users u WHERE u.role_id = r.id) AS userCount " +
            "FROM roles r ORDER BY r.id ASC")
    List<RoleInfo> findAll();

    @Select("SELECT r.id, r.name, r.display_name AS displayName, r.description, " +
            "r.is_system AS systemRole, r.is_immutable AS immutable, r.created_at AS createdAt, r.updated_at AS updatedAt, " +
            "(SELECT COUNT(*) FROM users u WHERE u.role_id = r.id) AS userCount " +
            "FROM roles r WHERE r.id = #{id}")
    RoleInfo findById(@Param("id") int id);

    @Select("SELECT r.id, r.name, r.display_name AS displayName, r.description, " +
            "r.is_system AS systemRole, r.is_immutable AS immutable, r.created_at AS createdAt, r.updated_at AS updatedAt " +
            "FROM roles r WHERE LOWER(r.name) = LOWER(#{name})")
    RoleInfo findByName(@Param("name") String name);

    @Insert("INSERT OR IGNORE INTO roles (id, name, display_name, description, is_system, is_immutable, created_at, updated_at) " +
            "VALUES (#{id}, #{name}, #{displayName}, #{description}, #{systemRole}, #{immutable}, #{createdAt}, #{updatedAt})")
    int insertSeed(RoleInfo role);

    @Insert("INSERT INTO roles (name, display_name, description, is_system, is_immutable, created_at, updated_at) " +
            "VALUES (#{name}, #{displayName}, #{description}, 0, 0, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RoleInfo role);

    @Update("UPDATE roles SET name = #{name}, display_name = #{displayName}, description = #{description}, updated_at = #{updatedAt} WHERE id = #{id}")
    int update(RoleInfo role);

    @Update("UPDATE roles SET updated_at = #{updatedAt} WHERE id = #{id}")
    int touchUpdatedAt(@Param("id") int id, @Param("updatedAt") long updatedAt);

    @Delete("DELETE FROM roles WHERE id = #{id} AND is_system = 0")
    int deleteNonSystem(@Param("id") int id);

    @Select("SELECT COUNT(*) FROM users WHERE role_id = #{roleId}")
    int countUsersWithRole(@Param("roleId") int roleId);
}
