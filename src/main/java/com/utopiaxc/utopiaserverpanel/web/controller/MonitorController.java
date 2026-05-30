package com.utopiaxc.utopiaserverpanel.web.controller;

import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.MonitorMapper;
import com.utopiaxc.utopiaserverpanel.web.service.MonitorConfigService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.List;
import java.util.Map;

/**
 * HTTP controller for monitoring log query endpoints.
 */
public final class MonitorController {

    private MonitorController() {}

    /** GET /api/monitor/perf?start=&end= — logs:1 */
    public static void queryPerfLogs(RequestContext ctx) {
        try {
            long start = parseLong(ctx.queryParam("start"), 0);
            long end = parseLong(ctx.queryParam("end"), System.currentTimeMillis() / 1000);

            if (start <= 0) {
                // Default: last 3 days
                start = end - 3 * 24 * 3600;
            }

            final long s = start, e = end;
            List<Map<String, Object>> logs = MyBatisFactory.doWorkWithResult(session -> {
                MonitorMapper mapper = session.getMapper(MonitorMapper.class);
                return mapper.queryPerfLogs(s, e);
            });

            ResponseHelper.sendOk(ctx, Map.of("logs", logs, "start", s, "end", e));
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid parameters");
        }
    }

    /** GET /api/monitor/players?start=&end= — logs:1 */
    public static void queryPlayerEvents(RequestContext ctx) {
        try {
            long start = parseLong(ctx.queryParam("start"), 0);
            long end = parseLong(ctx.queryParam("end"), System.currentTimeMillis() / 1000);

            if (start <= 0) {
                start = end - 3 * 24 * 3600;
            }

            final long s = start, e = end;

            Map<String, Object> result = MyBatisFactory.doWorkWithResult(session -> {
                MonitorMapper mapper = session.getMapper(MonitorMapper.class);

                // Events in the requested range
                List<Map<String, Object>> events = mapper.queryPlayerEvents(s, e);

                // Initial online count at start time (from perf_logs)
                Integer rawCount = mapper.getOnlineCountAt(s);
                int initialCount = rawCount != null ? rawCount : 0;

                // Initial online player list at start time (from player_events history)
                List<Map<String, Object>> onlineRows = mapper.getOnlinePlayersAt(s);
                List<String> initialPlayers = onlineRows.stream()
                        .map(m -> (String) m.get("playerName"))
                        .toList();

                Map<String, Object> data = new java.util.HashMap<>();
                data.put("initialCount", initialCount);
                data.put("initialPlayers", initialPlayers);
                data.put("events", events);
                data.put("start", s);
                data.put("end", e);
                return data;
            });

            ResponseHelper.sendOk(ctx, result);
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid parameters");
        }
    }

    /** GET /api/monitor/config — public display config */
    public static void getDisplayConfig(RequestContext ctx) {
        ResponseHelper.sendOk(ctx, Map.of(
                "showPlayerNames", MonitorConfigService.isShowPlayerNames(),
                "enabled", MonitorConfigService.isEnabled()
        ));
    }

    private static long parseLong(String str, long defaultVal) {
        if (str == null || str.isEmpty()) return defaultVal;
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static int parseInt(String str, int defaultVal) {
        if (str == null || str.isEmpty()) return defaultVal;
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    /** GET /api/monitor/lifecycle?page=1&size=20 — logs:1 */
    public static void queryLifecyclePaged(RequestContext ctx) {
        try {
            int page = parseInt(ctx.queryParam("page"), 1);
            int size = parseInt(ctx.queryParam("size"), 20);
            if (page < 1) page = 1;
            if (size < 1) size = 20;
            if (size > 100) size = 100;
            int offset = (page - 1) * size;

            final int fOffset = offset;
            final int fSize = size;
            final int fPage = page;

            Map<String, Object> result = MyBatisFactory.doWorkWithResult(session -> {
                MonitorMapper mapper = session.getMapper(MonitorMapper.class);
                List<Map<String, Object>> events = mapper.queryLifecyclePaged(fOffset, fSize);
                int total = mapper.countLifecycle();
                Map<String, Object> data = new java.util.HashMap<>();
                data.put("events", events);
                data.put("total", total);
                data.put("page", fPage);
                data.put("size", fSize);
                return data;
            });

            ResponseHelper.sendOk(ctx, result);
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid parameters");
        }
    }

    /** GET /api/monitor/lifecycle/range?start=&end= — logs:1 */
    public static void queryLifecycleRange(RequestContext ctx) {
        try {
            long start = parseLong(ctx.queryParam("start"), 0);
            long end = parseLong(ctx.queryParam("end"), System.currentTimeMillis() / 1000);

            if (start <= 0) {
                start = end - 3 * 24 * 3600;
            }

            final long s = start, e = end;
            List<Map<String, Object>> events = MyBatisFactory.doWorkWithResult(session -> {
                MonitorMapper mapper = session.getMapper(MonitorMapper.class);
                return mapper.queryLifecycleRange(s, e);
            });

            ResponseHelper.sendOk(ctx, Map.of("events", events, "start", s, "end", e));
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "Invalid parameters");
        }
    }
}
