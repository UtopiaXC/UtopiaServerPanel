package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import com.utopiaxc.utopiaserverpanel.web.db.model.UserProfileInfo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserProfileMapper {

    @Select("SELECT user_id AS userId, avatar_data AS avatarData, gender, birthday, bio, " +
            "social_accounts AS socialAccounts, privacy_json AS privacyJson, updated_at AS updatedAt " +
            "FROM user_profiles WHERE user_id = #{userId}")
    UserProfileInfo findByUserId(@Param("userId") int userId);

    @Insert("INSERT INTO user_profiles (user_id, avatar_data, gender, birthday, bio, social_accounts, privacy_json, updated_at) " +
            "VALUES (#{userId}, #{avatarData}, #{gender}, #{birthday}, #{bio}, #{socialAccounts}, '{}', #{updatedAt}) " +
            "ON CONFLICT(user_id) DO UPDATE SET " +
            "avatar_data = excluded.avatar_data, " +
            "gender = excluded.gender, " +
            "birthday = excluded.birthday, " +
            "bio = excluded.bio, " +
            "social_accounts = excluded.social_accounts, " +
            "updated_at = excluded.updated_at")
    int upsertProfileDetails(
            @Param("userId") int userId,
            @Param("avatarData") String avatarData,
            @Param("gender") String gender,
            @Param("birthday") String birthday,
            @Param("bio") String bio,
            @Param("socialAccounts") String socialAccounts,
            @Param("updatedAt") long updatedAt);

    @Insert("INSERT INTO user_profiles (user_id, avatar_data, gender, birthday, bio, social_accounts, privacy_json, updated_at) " +
            "VALUES (#{userId}, '', 'unspecified', '', '', '[]', #{privacyJson}, #{updatedAt}) " +
            "ON CONFLICT(user_id) DO UPDATE SET " +
            "privacy_json = excluded.privacy_json, " +
            "updated_at = excluded.updated_at")
    int upsertPrivacy(
            @Param("userId") int userId,
            @Param("privacyJson") String privacyJson,
            @Param("updatedAt") long updatedAt);
}
