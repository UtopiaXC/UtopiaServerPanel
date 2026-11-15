package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.db.model.WhitelistApplication;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionService;
import com.utopiaxc.utopiaserverpanel.web.service.SettingsService;
import com.utopiaxc.utopiaserverpanel.web.service.WhitelistService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST endpoints for Whitelist Player UUID Lookup, Applications, and Admin Review / CRUD.
 */
public final class WhitelistController {

    private WhitelistController() {}

    /** GET /api/whitelist/status */
    public static void getStatus(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        ResponseHelper.sendOk(ctx, WhitelistService.getPublicStatus(userId));
    }

    /** GET /api/whitelist/lookup?username=... */
    public static void lookupPlayer(RequestContext ctx) {
        if (!SettingsService.isWhitelistFeatureVisible()) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "FORBIDDEN", "Whitelist portal is disabled");
            return;
        }
        Integer userId = (Integer) ctx.getAttribute("userId");
        boolean permitted = (userId == null)
                ? PermissionService.guestMeetsRequirement("whitelist_apply:2")
                : PermissionService.meetsRequirement(userId.intValue(), "whitelist_apply:2");
        if (!permitted) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "FORBIDDEN", "Permission denied");
            return;
        }
        String username = ctx.queryParam("username");
        try {
            ResponseHelper.sendOk(ctx, WhitelistService.lookupPlayerIdentity(username, ctx.clientIp()));
        } catch (IllegalArgumentException e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_USERNAME", e.getMessage());
        } catch (IllegalStateException e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", e.getMessage());
        }
    }

    /** POST /api/whitelist/apply */
    public static void apply(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        JsonObject body = JsonHelper.parseObject(ctx.body());
        String username = JsonHelper.str(body, "username");
        String onlineUuid = JsonHelper.str(body, "onlineUuid");
        String offlineUuid = JsonHelper.str(body, "offlineUuid");
        String preferredUuidMode = JsonHelper.str(body, "preferredUuidMode");
        String email = JsonHelper.str(body, "email");
        String reason = JsonHelper.str(body, "reason");
        String socialAccount = JsonHelper.str(body, "socialAccount");
        if (body != null && body.has("socialAccounts") && body.get("socialAccounts").isJsonArray()) {
            JsonArray arr = body.getAsJsonArray("socialAccounts");
            socialAccount = JsonHelper.toJson(arr);
        }
        boolean bindOnApprove = JsonHelper.boolVal(body, "bindOnApprove", false);
        String captcha = JsonHelper.str(body, "captcha");

        try {
            WhitelistApplication created = WhitelistService.submitApplication(
                    userId, username, onlineUuid, offlineUuid, preferredUuidMode,
                    email, reason, socialAccount, bindOnApprove, captcha, ctx.clientIp());
            ResponseHelper.sendOk(ctx, created);
        } catch (SecurityException e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "FORBIDDEN", e.getMessage());
        } catch (IllegalArgumentException e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_APPLICATION", e.getMessage());
        } catch (IllegalStateException e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.CONFLICT, "APPLICATION_CONFLICT", e.getMessage());
        }
    }

    /** GET /api/whitelist/admin/applications */
    public static void listApplications(RequestContext ctx) {
        String status = ctx.queryParam("status");
        String search = ctx.queryParam("search");
        int page = parseInt(ctx.queryParam("page"), 1);
        int size = parseInt(ctx.queryParam("pageSize"), 30);
        ResponseHelper.sendOk(ctx, WhitelistService.listAdminApplications(status, search, page, size));
    }

    /** POST /api/whitelist/admin/applications/{id}/review */
    public static void reviewApplication(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        int id = parseInt(ctx.pathParam("id"), -1);
        JsonObject body = JsonHelper.parseObject(ctx.body());
        boolean approve = JsonHelper.boolVal(body, "approve", false);
        String note = JsonHelper.str(body, "reviewNote");
        try {
            WhitelistApplication updated = WhitelistService.reviewApplication(id, userId.intValue(), approve, note);
            ResponseHelper.sendOk(ctx, updated);
        } catch (IllegalArgumentException e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_REQUEST", e.getMessage());
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "REVIEW_FAILED", e.getMessage());
        }
    }

    /** GET /api/whitelist/admin/entries */
    public static void listEntries(RequestContext ctx) {
        Map<String, Object> res = new LinkedHashMap<String, Object>();
        res.put("items", WhitelistService.listServerWhitelistEntries());
        ResponseHelper.sendOk(ctx, res);
    }

    /** POST /api/whitelist/admin/entries */
    public static void addEntry(RequestContext ctx) {
        JsonObject body = JsonHelper.parseObject(ctx.body());
        String username = JsonHelper.str(body, "username");
        String uuid = JsonHelper.str(body, "uuid");
        try {
            WhitelistService.addWhitelistEntryToServer(username, uuid);
            ResponseHelper.sendOk(ctx, Collections.singletonMap("added", Boolean.TRUE));
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "ADD_FAILED", e.getMessage());
        }
    }

    /** DELETE /api/whitelist/admin/entries/{name} */
    public static void removeEntry(RequestContext ctx) {
        String name = ctx.pathParam("name");
        try {
            boolean removed = WhitelistService.removeWhitelistEntryFromServer(name);
            ResponseHelper.sendOk(ctx, Collections.singletonMap("removed", Boolean.valueOf(removed)));
        } catch (Exception e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "REMOVE_FAILED", e.getMessage());
        }
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
