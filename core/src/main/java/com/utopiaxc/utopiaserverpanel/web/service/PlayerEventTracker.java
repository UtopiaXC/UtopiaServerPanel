package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.logging.PanelLog;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.MonitorMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.PlayerBindingMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tracks player join/leave events and records them into normalized mc_players + player_events.
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

    public void onPlayerJoin(String playerName, String playerUuid) {
        recordEvent(playerName, playerUuid, 1);
    }

    public void onPlayerLeave(String playerName, String playerUuid) {
        recordEvent(playerName, playerUuid, 0);
    }

    private void recordEvent(String playerName, String playerUuid, int eventType) {
        long ts = System.currentTimeMillis() / 1000L;
        executor.submit(() -> {
            try {
                // Always sync canonical MC player identity in 3NF mc_players
                MyBatisFactory.doWork(session -> {
                    session.getMapper(PlayerBindingMapper.class).upsertMcPlayer(playerUuid, playerName, ts);
                    if (MonitorConfigService.isEnabled()) {
                        session.getMapper(MonitorMapper.class).insertPlayerEvent(ts, playerUuid, eventType);
                    }
                });

                if (MonitorConfigService.isEnabled()) {
                    Map<String, Object> event = new HashMap<>();
                    event.put("ts", ts);
                    event.put("playerName", playerName);
                    event.put("playerUuid", playerUuid);
                    event.put("eventType", eventType);
                    com.utopiaxc.utopiaserverpanel.web.controller.WebSocketController.broadcastPlayerEvent(event);
                }
            } catch (Exception e) {
                PanelLog.get().warn("Failed to record player event for " + playerName, e);
            }
        });
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
