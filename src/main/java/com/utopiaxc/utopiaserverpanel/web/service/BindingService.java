package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.BindingMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.UserMapper;

import java.security.SecureRandom;
import java.util.*;

/**
 * Player binding service using MyBatis mappers.
 */
public final class BindingService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final int CODE_EXPIRY_SECONDS = 5 * 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    private BindingService() {}

    public static String generateBindingCode(String playerUuid, String playerName) {
        String code = generateRandomCode();
        long now = System.currentTimeMillis() / 1000;
        long expiresAt = now + CODE_EXPIRY_SECONDS;

        MyBatisFactory.doWork(session -> {
            var bindingMapper = session.getMapper(BindingMapper.class);
            bindingMapper.invalidatePlayerCodes(playerUuid);

            Map<String, Object> codeData = new HashMap<>();
            codeData.put("code", code);
            codeData.put("playerUuid", playerUuid);
            codeData.put("playerName", playerName);
            codeData.put("createdAt", now);
            codeData.put("expiresAt", expiresAt);
            bindingMapper.insertCode(codeData);
        });

        UtopiaServerPanel.LOGGER.info("Binding code generated for {}: {}", playerName, code);
        return code;
    }

    public static boolean unbindByPlayer(String playerUuid) {
        return MyBatisFactory.doWorkWithResult(session -> {
            var bindingMapper = session.getMapper(BindingMapper.class);
            var userMapper = session.getMapper(UserMapper.class);

            Integer userId = bindingMapper.findUserIdByPlayerUuid(playerUuid);
            if (userId == null) return false;

            bindingMapper.deleteByPlayerUuid(playerUuid);

            Map<String, Object> status = new HashMap<>();
            status.put("id", userId);
            status.put("bindingStatus", "unbound");
            status.put("updatedAt", System.currentTimeMillis() / 1000);
            userMapper.updateBindingStatus(status);

            UtopiaServerPanel.LOGGER.info("Player {} unbound from user {}", playerUuid, userId);
            return true;
        });
    }

    private static String generateRandomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
