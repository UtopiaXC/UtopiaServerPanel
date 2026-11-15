package com.utopiaxc.utopiaserverpanel.web.controller;

import com.google.gson.JsonObject;
import com.utopiaxc.utopiaserverpanel.web.context.JsonHelper;
import com.utopiaxc.utopiaserverpanel.web.context.RequestContext;
import com.utopiaxc.utopiaserverpanel.web.context.ResponseHelper;
import com.utopiaxc.utopiaserverpanel.web.db.model.InGameChatEntry;
import com.utopiaxc.utopiaserverpanel.web.db.model.InSiteMessage;
import com.utopiaxc.utopiaserverpanel.web.service.BindingService;
import com.utopiaxc.utopiaserverpanel.web.service.MessageService;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionLevel;
import com.utopiaxc.utopiaserverpanel.web.service.PermissionService;
import com.utopiaxc.utopiaserverpanel.web.service.SettingsService;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for In-Site Inbox notifications, Player Mail, and Web <-> Game Chat Sync.
 */
public final class MessageController {

    private MessageController() {}

    /** GET /api/messages/inbox */
    public static void listInbox(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        String filter = ctx.queryParam("type");
        int page = parseInt(ctx.queryParam("page"), 1);
        int size = parseInt(ctx.queryParam("pageSize"), 30);
        ResponseHelper.sendOk(ctx, MessageService.getInbox(userId.intValue(), filter, page, size));
    }

    /** GET /api/messages/sent */
    public static void listSent(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        int page = parseInt(ctx.queryParam("page"), 1);
        int size = parseInt(ctx.queryParam("pageSize"), 30);
        ResponseHelper.sendOk(ctx, Collections.singletonMap("items", MessageService.getSent(userId.intValue(), page, size)));
    }

    /** GET /api/messages/unread-count */
    public static void unreadCount(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendOk(ctx, Collections.singletonMap("unreadCount", Integer.valueOf(0)));
            return;
        }
        ResponseHelper.sendOk(ctx, Collections.singletonMap("unreadCount", Integer.valueOf(MessageService.getUnreadCount(userId.intValue()))));
    }

    /** POST /api/messages/send */
    public static void send(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        String username = (String) ctx.getAttribute("username");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        JsonObject body = JsonHelper.parseObject(ctx.body());
        String target = JsonHelper.str(body, "target");
        String title = JsonHelper.str(body, "title");
        String content = JsonHelper.str(body, "content");
        try {
            InSiteMessage sent = MessageService.sendDirectMessage(userId, null, username, target, title, content);
            ResponseHelper.sendOk(ctx, sent);
        } catch (IllegalArgumentException e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "SEND_FAILED", e.getMessage());
        }
    }

    /** POST /api/messages/{id}/read */
    public static void markRead(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        int id = parseInt(ctx.pathParam("id"), -1);
        ResponseHelper.sendOk(ctx, Collections.singletonMap("updated", Boolean.valueOf(MessageService.markRead(userId.intValue(), id))));
    }

    /** POST /api/messages/read-all */
    public static void markAllRead(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        int count = MessageService.markAllRead(userId.intValue());
        ResponseHelper.sendOk(ctx, Collections.singletonMap("updated", Integer.valueOf(count)));
    }

    /** GET /api/chat/history?channel=public|whisper */
    public static void listChatHistory(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        String channel = ctx.queryParam("channel");
        int page = parseInt(ctx.queryParam("page"), 1);
        int size = parseInt(ctx.queryParam("pageSize"), 80);
        boolean isAdmin = PermissionService.meetsRequirement(userId, "admin:1");

        List<InGameChatEntry> list = MessageService.listChatHistory(userId.intValue(), isAdmin, channel, page, size);
        Map<String, Object> res = new LinkedHashMap<String, Object>();
        res.put("items", list);
        res.put("webChatSendEnabled", Boolean.valueOf(SettingsService.isWebChatSendEnabled()));
        res.put("boundPlayer", BindingService.findByUserId(userId.intValue()));
        res.put("boundPlayers", BindingService.listByUserId(userId.intValue()));
        ResponseHelper.sendOk(ctx, res);
    }

    /** POST /api/chat/send */
    public static void sendChat(RequestContext ctx) {
        Integer userId = (Integer) ctx.getAttribute("userId");
        if (userId == null) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
            return;
        }
        JsonObject body = JsonHelper.parseObject(ctx.body());
        String content = JsonHelper.str(body, "content");
        String playerUuid = JsonHelper.str(body, "playerUuid");
        try {
            InGameChatEntry entry = MessageService.sendWebPublicChat(userId.intValue(), playerUuid, content);
            ResponseHelper.sendOk(ctx, entry);
        } catch (SecurityException e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.FORBIDDEN, "FORBIDDEN", e.getMessage());
        } catch (IllegalStateException e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, e.getMessage(), e.getMessage());
        } catch (IllegalArgumentException e) {
            ResponseHelper.sendError(ctx, HttpResponseStatus.BAD_REQUEST, "INVALID_MESSAGE", e.getMessage());
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
