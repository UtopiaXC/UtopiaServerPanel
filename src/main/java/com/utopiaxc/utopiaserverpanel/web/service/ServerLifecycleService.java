package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.MonitorMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks server lifecycle events: start, normal stop, abnormal stop, crash.
 * <p>
 * Event types:
 * <ul>
 *   <li>0 — Server started</li>
 *   <li>1 — Normal shutdown</li>
 *   <li>2 — Abnormal / forced shutdown (detected on next start)</li>
 *   <li>3 — Crash (reserved for future use)</li>
 * </ul>
 */
public final class ServerLifecycleService {

    public static final int EVENT_START = 0;
    public static final int EVENT_NORMAL_STOP = 1;
    public static final int EVENT_ABNORMAL_STOP = 2;
    public static final int EVENT_CRASH = 3;

    private ServerLifecycleService() {}

    /**
     * Called when the server starts.
     * <p>
     * If the last recorded lifecycle event was a START (no matching STOP),
     * we infer that the previous session ended abnormally and backfill
     * an ABNORMAL_STOP event using the timestamp of the last perf_log entry
     * (or the previous start timestamp + 1 if no perf_logs exist).
     */
    public static void recordStart() {
        try {
            MyBatisFactory.doWork(session -> {
                MonitorMapper mapper = session.getMapper(MonitorMapper.class);

                // Check if last event was a start (unmatched)
                Map<String, Object> lastEvent = mapper.getLastLifecycleEvent();
                if (lastEvent != null) {
                    Object eventTypeObj = lastEvent.get("eventType");
                    int lastType = eventTypeObj instanceof Number ? ((Number) eventTypeObj).intValue() : Integer.parseInt(eventTypeObj.toString());
                    if (lastType == EVENT_START) {
                        // Previous start had no matching stop — backfill abnormal stop
                        long lastStartTs = ((Number) lastEvent.get("ts")).longValue();

                        // Try to use last perf_log timestamp as the stop time
                        Integer lastPerfCount = mapper.getOnlineCountAt(Long.MAX_VALUE / 1000);
                        // Actually, get the ts of the latest perf_log
                        long abnormalStopTs = lastStartTs + 1; // fallback
                        // We need the ts, not the count. Let's query differently:
                        // Use a simple approach: query perf_logs after lastStartTs
                        // For now, use current time - 1 as a reasonable estimate
                        // Better: find max ts from perf_logs
                        try {
                            var perfLogs = mapper.queryPerfLogs(lastStartTs, System.currentTimeMillis() / 1000);
                            if (!perfLogs.isEmpty()) {
                                abnormalStopTs = ((Number) perfLogs.get(perfLogs.size() - 1).get("ts")).longValue();
                            }
                        } catch (Exception ignored) {}

                        Map<String, Object> abnormalStop = new HashMap<>();
                        abnormalStop.put("ts", abnormalStopTs);
                        abnormalStop.put("eventType", EVENT_ABNORMAL_STOP);
                        abnormalStop.put("detail", "Detected on next startup: no shutdown event recorded");
                        mapper.insertLifecycleEvent(abnormalStop);
                        UtopiaServerPanel.LOGGER.info("Backfilled abnormal stop event at ts={}", abnormalStopTs);
                    }
                }

                // Record this start
                Map<String, Object> startEvent = new HashMap<>();
                startEvent.put("ts", System.currentTimeMillis() / 1000);
                startEvent.put("eventType", EVENT_START);
                startEvent.put("detail", "");
                mapper.insertLifecycleEvent(startEvent);
            });
            UtopiaServerPanel.LOGGER.info("Recorded server start lifecycle event");
        } catch (Exception e) {
            UtopiaServerPanel.LOGGER.warn("Failed to record server start lifecycle event", e);
        }
    }

    /**
     * Called when the server stops normally (via ServerStoppingEvent).
     */
    public static void recordNormalStop() {
        try {
            MyBatisFactory.doWork(session -> {
                MonitorMapper mapper = session.getMapper(MonitorMapper.class);
                Map<String, Object> stopEvent = new HashMap<>();
                stopEvent.put("ts", System.currentTimeMillis() / 1000);
                stopEvent.put("eventType", EVENT_NORMAL_STOP);
                stopEvent.put("detail", "");
                mapper.insertLifecycleEvent(stopEvent);
            });
            UtopiaServerPanel.LOGGER.info("Recorded server normal stop lifecycle event");
        } catch (Exception e) {
            UtopiaServerPanel.LOGGER.warn("Failed to record server stop lifecycle event", e);
        }
    }
}
