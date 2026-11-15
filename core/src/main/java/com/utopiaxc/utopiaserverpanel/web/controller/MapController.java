package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.db.model.MapWaypoint;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionLevel;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionService;
import com.utopiaxc.utopiaserverpanel.web.service.map.MapService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST endpoints for the isolated JourneyMap-style web minimap &amp; waypoints.
 */
public final class MapController {

    private MapController() {}

    /** GET /api/map/bootstrap */
    public static void bootstrap(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        ResponseHelper.sendOk(ctx, MapService.getBootstrap(userId));
    }

    /** GET /api/map/tile?dim=...&amp;z=0&amp;x=0&amp;y=0 */
    public static void getTile(RequestContext ctx) {
        String dim = ctx.queryParam("dim");
        if (dim == null || dim.isEmpty()) dim = "minecraft:overworld";
        int zoom = parseInt(ctx.queryParam("z"), 0);
        int tx = parseInt(ctx.queryParam("x"), 0);
        int tz = parseInt(ctx.queryParam("y"), 0);

        Integer userId = (Integer) ctx.getAttribute("userId");
        byte[] png = MapService.getTilePng(dim, zoom, tx, tz, userId);
        ctx.addResponseHeader("Cache-Control", "public, max-age=10");
        ResponseHelper.sendFile(ctx, png, "image/png");
    }

    /** GET /api/map/regions?dim=... */
    public static void getRegions(RequestContext ctx) {
        String dim = ctx.queryParam("dim");
        if (dim == null || dim.isEmpty()) dim = "minecraft:overworld";
        ResponseHelper.sendOk(ctx, Collections.singletonMap("regions", MapService.listAvailableRegions(dim)));
    }

    /** GET /api/map/overlays?dim=...&amp;q=... */
    public static void getOverlays(RequestContext ctx) {
        String dim = ctx.queryParam("dim");
        if (dim == null || dim.isEmpty()) dim = "minecraft:overworld";
        String query = ctx.queryParam("q");
        Integer userId = (Integer) ctx.getAttribute("userId");
        ResponseHelper.sendOk(ctx, MapService.getOverlays(dim, userId, query));
    }

    /** POST /api/map/waypoints */
    public static void createWaypoint(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        Map<String, Object> boot = MapService.getBootstrap(userId);
        if (!Boolean.TRUE.equals(boot.get("canManageWaypoints"))) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "WAYPOINT_DISABLED",
                    "Waypoint management is disabled for your role");
            return;
        }
        JsonObject body = JsonHelper.parseObject(ctx.body());
        String name = JsonHelper.str(body, "name");
        String dim = JsonHelper.str(body, "dimension");
        int x = JsonHelper.intVal(body, "x", 0);
        int y = JsonHelper.intVal(body, "y", 64);
        int z = JsonHelper.intVal(body, "z", 0);
        String color = JsonHelper.str(body, "color");
        String icon = JsonHelper.str(body, "icon");
        boolean isPublic = JsonHelper.boolVal(body, "isPublic", true);

        if (name == null || name.trim().isEmpty()) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_INPUT", "Waypoint name is required");
            return;
        }
        MapWaypoint created = MapService.createWaypoint(userId.intValue(), name, dim, x, y, z, color, icon, isPublic);
        ResponseHelper.sendOk(ctx, created);
    }

    /** DELETE /api/map/waypoints/{id} */
    public static void deleteWaypoint(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        Map<String, Object> boot = MapService.getBootstrap(userId);
        if (!Boolean.TRUE.equals(boot.get("canManageWaypoints"))) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "WAYPOINT_DISABLED",
                    "Waypoint management is disabled for your role");
            return;
        }
        int id = parseInt(ctx.pathParam("id"), -1);
        if (id <= 0) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_ID", "Invalid waypoint ID");
            return;
        }
        boolean full = PermissionService.meetsRequirement(userId, "map:2");
        boolean deleted = MapService.deleteWaypoint(id, userId.intValue(), full);
        if (!deleted) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "FORBIDDEN", "Cannot delete this waypoint");
            return;
        }
        ResponseHelper.sendOk(ctx, Collections.singletonMap("deleted", Boolean.TRUE));
    }

    /** POST /api/map/teleport */
    public static void teleport(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        Map<String, Object> boot = MapService.getBootstrap(userId);
        if (!Boolean.TRUE.equals(boot.get("canTeleport"))) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "TELEPORT_DISABLED",
                    "Teleport is disabled for your role");
            return;
        }
        JsonObject body = JsonHelper.parseObject(ctx.body());
        String dim = JsonHelper.str(body, "dimension");
        if (dim == null || dim.isEmpty()) dim = "minecraft:overworld";
        double x = body.has("x") && !body.get("x").isJsonNull() ? body.get("x").getAsDouble() : 0.0;
        double y = body.has("y") && !body.get("y").isJsonNull() ? body.get("y").getAsDouble() : 80.0;
        double z = body.has("z") && !body.get("z").isJsonNull() ? body.get("z").getAsDouble() : 0.0;

        boolean ok = MapService.teleportBoundPlayer(userId.intValue(), dim, x, y, z);
        if (!ok) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "TELEPORT_FAILED",
                    "Teleport requires an online bound Minecraft player");
            return;
        }
        Map<String, Object> res = new LinkedHashMap<String, Object>();
        res.put("teleported", Boolean.TRUE);
        res.put("dimension", dim);
        res.put("x", Double.valueOf(x));
        res.put("y", Double.valueOf(y));
        res.put("z", Double.valueOf(z));
        ResponseHelper.sendOk(ctx, res);
    }

    /** POST /api/map/reset-health (admin) */
    public static void resetHealth(RequestContext ctx) {
        MapService.resetHealth();
        ResponseHelper.sendOk(ctx, Collections.singletonMap("healthy", Boolean.TRUE));
    }

    /** POST /api/map/clear-cache (admin) */
    public static void clearCache(RequestContext ctx) {
        JsonObject body = JsonHelper.parseObject(ctx.body());
        String dim = JsonHelper.str(body, "dimension");
        MapService.clearTileCache(dim);
        ResponseHelper.sendOk(ctx, Collections.singletonMap("cleared", Boolean.TRUE));
    }

    private static int parseInt(String s, int def) {
        if (s == null || s.trim().isEmpty()) return def;
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
