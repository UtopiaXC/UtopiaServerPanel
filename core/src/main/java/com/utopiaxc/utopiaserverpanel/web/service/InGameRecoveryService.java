package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.auth.PasswordUtil;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.PlayerBindingMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.SecurityMapper;
import com.utopiaxc.utopiaserverpanel.web.db.model.PlayerBindingInfo;
import com.utopiaxc.utopiaserverpanel.web.db.model.RecoveryToken;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserTotp;

/**
 * Generates short-lived single-use recovery tokens in-game for bound Minecraft players
 * so accounts can recover passwords or 2FA even without SMTP configured.
 */
public final class InGameRecoveryService {
    private static final long TOKEN_TTL_SECONDS = 15 * 60L;

    private InGameRecoveryService() {}

    public static final class InGameRecoveryResult {
        private final boolean success;
        private final String errorCode;
        private final String token;

        public InGameRecoveryResult(boolean success, String errorCode, String token) {
            this.success = success;
            this.errorCode = errorCode;
            this.token = token;
        }

        public boolean isSuccess() { return success; }
        public String getErrorCode() { return errorCode; }
        public String getToken() { return token; }
    }

    public static InGameRecoveryResult createRecoveryTokenForPlayer(String playerUuid, String playerName, String tokenType) {
        BindingService.syncPlayerIdentity(playerUuid, playerName);

        return MyBatisFactory.doWorkWithResult(session -> {
            PlayerBindingMapper bindingMapper = session.getMapper(PlayerBindingMapper.class);
            PlayerBindingInfo binding = bindingMapper.findByPlayerUuid(playerUuid);
            if (binding == null) {
                return new InGameRecoveryResult(false, "NOT_BOUND", null);
            }

            SecurityMapper secMapper = session.getMapper(SecurityMapper.class);
            if ("2fa".equals(tokenType)) {
                UserTotp totp = secMapper.findTotpByUserId(binding.getUserId());
                if (totp == null || !totp.isEnabled()) {
                    return new InGameRecoveryResult(false, "TWO_FA_NOT_ENABLED", null);
                }
            }

            long now = System.currentTimeMillis() / 1000L;
            String rawToken = PasswordUtil.randomHex(16);
            String tokenHash = PasswordUtil.sha256Hex(rawToken);

            secMapper.invalidateUnusedTokens(binding.getUserId(), tokenType, now);
            RecoveryToken rt = new RecoveryToken();
            rt.setUserId(binding.getUserId());
            rt.setTokenHash(tokenHash);
            rt.setTokenType(tokenType);
            rt.setSource("ingame");
            rt.setExpiresAt(now + TOKEN_TTL_SECONDS);
            rt.setCreatedAt(now);
            secMapper.insertRecoveryToken(rt);

            return new InGameRecoveryResult(true, null, rawToken);
        });
    }
}

