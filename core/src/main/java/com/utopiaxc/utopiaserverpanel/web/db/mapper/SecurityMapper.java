package com.utopiaxc.utopiaserverpanel.web.db.mapper;

import com.utopiaxc.utopiaserverpanel.web.db.model.RecoveryToken;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserTotp;
import org.apache.ibatis.annotations.*;

public interface SecurityMapper {

    // ── TOTP 2FA ──

    @Select("SELECT user_id AS userId, secret_encrypted AS secretEncrypted, pending_secret_encrypted AS pendingSecretEncrypted, " +
            "enabled, verified_at AS verifiedAt, updated_at AS updatedAt FROM user_totp WHERE user_id = #{userId}")
    UserTotp findTotpByUserId(@Param("userId") int userId);

    @Insert("INSERT INTO user_totp (user_id, secret_encrypted, pending_secret_encrypted, enabled, verified_at, updated_at) " +
            "VALUES (#{userId}, #{secretEncrypted}, #{pendingSecretEncrypted}, #{enabled}, #{verifiedAt}, #{updatedAt}) " +
            "ON CONFLICT(user_id) DO UPDATE SET secret_encrypted = excluded.secret_encrypted, " +
            "pending_secret_encrypted = excluded.pending_secret_encrypted, enabled = excluded.enabled, " +
            "verified_at = excluded.verified_at, updated_at = excluded.updated_at")
    int upsertTotp(UserTotp totp);

    @Delete("DELETE FROM user_totp WHERE user_id = #{userId}")
    int deleteTotpByUserId(@Param("userId") int userId);

    // ── Recovery & Activation Tokens ──

    @Update("UPDATE recovery_tokens SET used_at = #{now} WHERE user_id = #{userId} AND token_type = #{tokenType} AND used_at IS NULL")
    int invalidateUnusedTokens(@Param("userId") int userId, @Param("tokenType") String tokenType, @Param("now") long now);

    @Insert("INSERT INTO recovery_tokens (user_id, token_hash, token_type, source, expires_at, used_at, created_at) " +
            "VALUES (#{userId}, #{tokenHash}, #{tokenType}, #{source}, #{expiresAt}, NULL, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRecoveryToken(RecoveryToken token);

    @Select("SELECT id, user_id AS userId, token_hash AS tokenHash, token_type AS tokenType, source, expires_at AS expiresAt, used_at AS usedAt, created_at AS createdAt " +
            "FROM recovery_tokens WHERE token_hash = #{tokenHash} AND token_type = #{tokenType}")
    RecoveryToken findRecoveryToken(@Param("tokenHash") String tokenHash, @Param("tokenType") String tokenType);

    @Update("UPDATE recovery_tokens SET used_at = #{now} WHERE id = #{id}")
    int markRecoveryTokenUsed(@Param("id") int id, @Param("now") long now);

    @Delete("DELETE FROM recovery_tokens WHERE expires_at < #{now}")
    int deleteExpiredRecoveryTokens(@Param("now") long now);
}

