package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

public interface SettingsMapper {

    @Select("SELECT config_value FROM system_settings WHERE config_key = #{key}")
    String getValue(@Param("key") String key);

    @Select("SELECT config_key AS configKey, config_value AS configValue FROM system_settings")
    List<Map<String, Object>> getAll();

    @Insert("INSERT INTO system_settings (config_key, config_value, updated_at) VALUES (#{key}, #{value}, #{updatedAt}) " +
            "ON CONFLICT(config_key) DO UPDATE SET config_value = excluded.config_value, updated_at = excluded.updated_at")
    int setValue(@Param("key") String key, @Param("value") String value, @Param("updatedAt") long updatedAt);
}

