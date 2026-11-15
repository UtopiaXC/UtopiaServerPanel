package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import com.utopiaxc.utopiaserverpanel.web.db.model.MapWaypoint;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface MapWaypointMapper {

    @Select("SELECT id, dimension, name, x, y, z, color, icon, owner_user_id AS ownerUserId, " +
            "is_global AS global, created_at AS createdAt FROM map_waypoints " +
            "WHERE (#{dimension} IS NULL OR #{dimension} = '' OR dimension = #{dimension}) " +
            "ORDER BY created_at DESC, id DESC")
    List<MapWaypoint> listByDimension(@Param("dimension") String dimension);

    @Select("SELECT id, dimension, name, x, y, z, color, icon, owner_user_id AS ownerUserId, " +
            "is_global AS global, created_at AS createdAt FROM map_waypoints WHERE id = #{id}")
    MapWaypoint findById(@Param("id") int id);

    @Insert("INSERT INTO map_waypoints (dimension, name, x, y, z, color, icon, owner_user_id, is_global, created_at) " +
            "VALUES (#{dimension}, #{name}, #{x}, #{y}, #{z}, #{color}, #{icon}, #{ownerUserId}, #{global}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MapWaypoint wp);

    @Delete("DELETE FROM map_waypoints WHERE id = #{id}")
    int deleteById(@Param("id") int id);
}
