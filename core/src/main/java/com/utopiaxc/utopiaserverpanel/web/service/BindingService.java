package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.logging.PanelLog;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.PlayerBindingMapper;
import com.utopiaxc.utopiaserverpanel.web.db.model.BindingCodeInfo;
import com.utopiaxc.utopiaserverpanel.web.db.model.PlayerBindingInfo;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Player <-> Panel account binding service using normalized mc_players & player_bindings tables.
 */
public final class BindingService {
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final int CODE_EXPIRY_SECONDS = 5 * 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    private BindingService() {}

    public static void syncPlayerIdentity(final String playerUuid, String playerName) {
        if (playerUuid == null || playerUuid.trim().isEmpty()) return;
        final long now = System.currentTimeMillis() / 1000L;
        final String safeName = (playerName != null && !playerName.trim().isEmpty()) ? playerName : playerUuid;
        MyBatisFactory.doWork(session ->
                session.getMapper(PlayerBindingMapper.class).upsertMcPlayer(playerUuid, safeName, now));
    }

    public static String generateBindingCode(final String playerUuid, final String playerName) {
        final String code = generateRandomCode();
        final long now = System.currentTimeMillis() / 1000L;
        final long expiresAt = now + CODE_EXPIRY_SECONDS;

        MyBatisFactory.doWork(session -> {
            PlayerBindingMapper mapper = session.getMapper(PlayerBindingMapper.class);
            mapper.upsertMcPlayer(playerUuid, playerName, now);
            mapper.invalidatePlayerCodes(playerUuid, now);
            mapper.insertCode(code, playerUuid, now, expiresAt);
        });

        return code;
    }

    public static PlayerBindingInfo findByPlayerUuid(final String playerUuid) {
        return MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(PlayerBindingMapper.class).findByPlayerUuid(playerUuid));
    }

    public static PlayerBindingInfo findByUserId(final int userId) {
        return MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(PlayerBindingMapper.class).findByUserId(userId));
    }

    public static final class BindResult {
        private final boolean success;
        private final String errorCode;
        private final String message;
        private final String playerName;
        private final String playerUuid;

        public BindResult(boolean success, String errorCode, String message, String playerName, String playerUuid) {
            this.success = success;
            this.errorCode = errorCode;
            this.message = message;
            this.playerName = playerName;
            this.playerUuid = playerUuid;
        }

        public boolean success() { return success; }
        public String errorCode() { return errorCode; }
        public String message() { return message; }
        public String playerName() { return playerName; }
        public String playerUuid() { return playerUuid; }
    }

    public static BindResult bindUserWithCode(final int userId, String rawCode) {
        if (rawCode == null || rawCode.trim().isEmpty()) {
            return new BindResult(false, "BINDING_CODE_REQUIRED", "Binding code is required", null, null);
        }
        final String code = rawCode.trim().toUpperCase(Locale.ROOT);
        final long now = System.currentTimeMillis() / 1000L;

        return MyBatisFactory.doWorkWithResult(session -> {
            PlayerBindingMapper mapper = session.getMapper(PlayerBindingMapper.class);
            if (mapper.findByUserId(userId) != null) {
                return new BindResult(false, "ALREADY_BOUND", "Account is already bound to a player", null, null);
            }

            BindingCodeInfo codeInfo = mapper.findByCode(code);
            if (codeInfo == null || codeInfo.getConsumedAt() != null || codeInfo.getExpiresAt() < now) {
                return new BindResult(false, "INVALID_BINDING_CODE", "Invalid or expired binding code", null, null);
            }

            if (mapper.countByPlayerUuid(codeInfo.getPlayerUuid()) > 0) {
                return new BindResult(false, "PLAYER_ALREADY_BOUND", "This player is already bound to another account", null, null);
            }

            mapper.insertBinding(userId, codeInfo.getPlayerUuid(), now);
            mapper.markCodeConsumed(code, now);
            return new BindResult(true, null, "Player bound successfully", codeInfo.getPlayerName(), codeInfo.getPlayerUuid());
        });
    }

    public static boolean unbindByUserId(final int userId) {
        return MyBatisFactory.doWorkWithResult(session ->
                Boolean.valueOf(session.getMapper(PlayerBindingMapper.class).deleteByUserId(userId) > 0)).booleanValue();
    }

    public static boolean unbindByPlayer(final String playerUuid) {
        return MyBatisFactory.doWorkWithResult(session -> {
            int deleted = session.getMapper(PlayerBindingMapper.class).deleteByPlayerUuid(playerUuid);
            if (deleted > 0) {
                PanelLog.get().info("Player {} unbound from panel account", playerUuid);
                return Boolean.TRUE;
            }
            return Boolean.FALSE;
        }).booleanValue();
    }

    private static String generateRandomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
