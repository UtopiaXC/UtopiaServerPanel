package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.MonitorMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tracks player join/leave events and records them to the database.
 * Uses an async single-thread executor to avoid blocking the game thread.
 */
public final class PlayerEventTracker {
    private static final PlayerEventTracker INSTANCE = new PlayerEventTracker();

    private final ExecutorService executor;

    private PlayerEventTracker() {
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "UtopiaPanel-PlayerEvents");
            t.setDaemon(true);
            return t;
        });
    }

    public static PlayerEventTracker getInstance() { return INSTANCE; }

    /** Record a player login event. */
    public void onPlayerJoin(String playerName, String playerUuid) {
        if (!MonitorConfigService.isEnabled()) return;
        recordEvent(playerName, playerUuid, 1);
    }

    /** Record a player logout event. */
    public void onPlayerLeave(String playerName, String playerUuid) {
        if (!MonitorConfigService.isEnabled()) return;
        recordEvent(playerName, playerUuid, 0);
    }

    private void recordEvent(String playerName, String playerUuid, int eventType) {
        long ts = System.currentTimeMillis() / 1000;
        executor.submit(() -> {
            try {
                Map<String, Object> event = new HashMap<>();
                event.put("ts", ts);
                event.put("playerName", playerName);
                event.put("playerUuid", playerUuid);
                event.put("eventType", eventType);

                MyBatisFactory.doWork(session -> {
                    MonitorMapper mapper = session.getMapper(MonitorMapper.class);
                    mapper.insertPlayerEvent(event);
                });
                com.utopiaxc.utopiaserverpanel.web.controller.WebSocketController.broadcastPlayerEvent(event);
            } catch (Exception e) {
                UtopiaServerPanel.LOGGER.warn("Failed to record player event for {}", playerName, e);
            }
        });
    }

    /** Shut down the async executor. */
    public void shutdown() {
        executor.shutdownNow();
    }
}
