package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import com.utopiaxc.utopiaserverpanel.web.db.model.UserPasskey;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface PasskeyMapper {

    String BASE_SELECT =
            "SELECT id, user_id AS userId, credential_id AS credentialId, name, " +
            "public_key_cose AS publicKeyCose, algorithm, sign_count AS signCount, " +
            "transports, created_at AS createdAt, last_used_at AS lastUsedAt FROM user_passkeys ";

    @Insert("INSERT INTO user_passkeys (user_id, credential_id, name, public_key_cose, algorithm, sign_count, transports, created_at, last_used_at) " +
            "VALUES (#{userId}, #{credentialId}, #{name}, #{publicKeyCose}, #{algorithm}, #{signCount}, #{transports}, #{createdAt}, #{lastUsedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserPasskey passkey);

    @Select(BASE_SELECT + "WHERE credential_id = #{credentialId} LIMIT 1")
    UserPasskey findByCredentialId(@Param("credentialId") String credentialId);

    @Select(BASE_SELECT + "WHERE id = #{id} AND user_id = #{userId} LIMIT 1")
    UserPasskey findByIdAndUser(@Param("id") int id, @Param("userId") int userId);

    @Select(BASE_SELECT + "WHERE user_id = #{userId} ORDER BY created_at DESC, id DESC")
    List<UserPasskey> listByUserId(@Param("userId") int userId);

    @Select("SELECT COUNT(*) FROM user_passkeys WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") int userId);

    @Update("UPDATE user_passkeys SET name = #{name} WHERE id = #{id} AND user_id = #{userId}")
    int rename(@Param("id") int id, @Param("userId") int userId, @Param("name") String name);

    @Update("UPDATE user_passkeys SET sign_count = #{signCount}, last_used_at = #{lastUsedAt} WHERE id = #{id}")
    int updateSignCountAndLastUsed(@Param("id") int id, @Param("signCount") long signCount, @Param("lastUsedAt") long lastUsedAt);

    @Delete("DELETE FROM user_passkeys WHERE id = #{id} AND user_id = #{userId}")
    int deleteByIdAndUser(@Param("id") int id, @Param("userId") int userId);

    @Delete("DELETE FROM user_passkeys WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") int userId);
}
