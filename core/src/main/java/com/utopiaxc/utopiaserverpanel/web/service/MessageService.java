package com.utopiaxc.utopiaserverpanel.web.service;

import com.utopiaxc.utopiaserverpanel.adapter.AdapterRegistry;
import com.utopiaxc.utopiaserverpanel.adapter.GameAdapter;
import com.utopiaxc.utopiaserverpanel.adapter.model.OnlinePlayerSnapshot;
import com.utopiaxc.utopiaserverpanel.logging.PanelLog;
import com.utopiaxc.utopiaserverpanel.web.controller.WebSocketController;
import com.utopiaxc.utopiaserverpanel.web.db.MyBatisFactory;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.MessageMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.PlayerBindingMapper;
import com.utopiaxc.utopiaserverpanel.web.db.mapper.UserMapper;
import com.utopiaxc.utopiaserverpanel.web.db.model.InGameChatEntry;
import com.utopiaxc.utopiaserverpanel.web.db.model.InSiteMessage;
import com.utopiaxc.utopiaserverpanel.web.db.model.PlayerBindingInfo;
import com.utopiaxc.utopiaserverpanel.web.db.model.UserAccount;
import com.utopiaxc.utopiaserverpanel.web.db.model.WhitelistApplication;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * In-site inbox notifications, player-to-player offline mail ({@code /usp msg}),
 * two-way public chat sync, and in-game whisper logger.
 */
public final class MessageService {

    private MessageService() {}

    public static Map<String, Object> getInbox(int userId, String filterType, int page, int pageSize) {
        final List<String> playerUuids = boundUuidsForUser(userId);
        final int safeSize = Math.max(1, Math.min(100, pageSize));
        final int offset = Math.max(0, (page - 1) * safeSize);

        return MyBatisFactory.doWorkWithResult(session -> {
            MessageMapper mapper = session.getMapper(MessageMapper.class);
            List<InSiteMessage> items = mapper.listInbox(userId, playerUuids, filterType, offset, safeSize);
            int unread = mapper.countUnread(userId, playerUuids);
            Map<String, Object> res = new LinkedHashMap<String, Object>();
            res.put("items", items != null ? items : Collections.emptyList());
            res.put("unreadCount", Integer.valueOf(unread));
            return res;
        });
    }

    public static List<InSiteMessage> getSent(int userId, int page, int pageSize) {
        final int safeSize = Math.max(1, Math.min(100, pageSize));
        final int offset = Math.max(0, (page - 1) * safeSize);
        return MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(MessageMapper.class).listSent(userId, offset, safeSize));
    }

    public static int getUnreadCount(int userId) {
        final List<String> playerUuids = boundUuidsForUser(userId);
        return MyBatisFactory.doWorkWithResult(session ->
                Integer.valueOf(session.getMapper(MessageMapper.class).countUnread(userId, playerUuids))).intValue();
    }

    public static boolean markRead(int userId, int messageId) {
        final List<String> playerUuids = boundUuidsForUser(userId);
        return MyBatisFactory.doWorkWithResult(session ->
                Boolean.valueOf(session.getMapper(MessageMapper.class).markRead(messageId, userId, playerUuids) > 0)).booleanValue();
    }

    public static int markAllRead(int userId) {
        final List<String> playerUuids = boundUuidsForUser(userId);
        return MyBatisFactory.doWorkWithResult(session ->
                Integer.valueOf(session.getMapper(MessageMapper.class).markAllRead(userId, playerUuids))).intValue();
    }

    private static List<String> boundUuidsForUser(int userId) {
        List<PlayerBindingInfo> list = BindingService.listByUserId(userId);
        if (list == null || list.isEmpty()) return Collections.emptyList();
        List<String> uuids = new ArrayList<String>(list.size());
        for (PlayerBindingInfo info : list) {
            if (info.getPlayerUuid() != null && !info.getPlayerUuid().isEmpty()) {
                uuids.add(info.getPlayerUuid());
            }
        }
        return uuids;
    }

    public static InSiteMessage sendDirectMessage(Integer senderUserId, String senderPlayerUuid,
                                                  String senderName, String target,
                                                  String title, String content) {
        if (target == null || target.trim().isEmpty()) {
            throw new IllegalArgumentException("Target username or player name is required");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Message content is required");
        }
        final String cleanTarget = target.trim();
        final String safeContent = content.trim();
        final String safeTitle = (title != null && !title.trim().isEmpty()) ? title.trim() : "Direct Message";

        TargetResolution resolved = resolveTarget(cleanTarget);
        if (resolved == null) {
            throw new IllegalArgumentException("Recipient user or bound player not found: " + cleanTarget);
        }

        InSiteMessage msg = new InSiteMessage();
        msg.setMsgType(senderUserId == null ? "ingame_mail" : "private");
        msg.setSenderUserId(senderUserId);
        msg.setSenderName(senderName != null ? senderName : "System");
        msg.setSenderUuid(nonNull(senderPlayerUuid));
        msg.setRecipientUserId(resolved.userId);
        msg.setRecipientPlayerUuid(nonNull(resolved.playerUuid));
        msg.setRecipientName(nonNull(resolved.displayName));
        msg.setTitle(safeTitle);
        msg.setContent(safeContent);
        msg.setRelatedType("");
        msg.setRelatedId("");
        msg.setRead(false);
        msg.setDeliveredIngame(false);
        msg.setCreatedAt(System.currentTimeMillis() / 1000L);

        // Deliver immediately in-game if player is online
        boolean deliveredNow = tryDeliverInGameLive(resolved.playerName, resolved.playerUuid,
                msg.getSenderName(), safeContent);
        if (deliveredNow) {
            msg.setDeliveredIngame(true);
        }

        MyBatisFactory.doWork(session -> session.getMapper(MessageMapper.class).insertMessage(msg));

        if (resolved.userId != null) {
            WebSocketController.pushInboxNotification(resolved.userId.intValue(), msg);
        }
        return msg;
    }

    public static void notifyAdminsWhitelistSubmitted(WhitelistApplication app) {
        if (app == null) return;
        try {
            List<UserAccount> admins = MyBatisFactory.doWorkWithResult(session ->
                    session.getMapper(UserMapper.class).listFiltered(
                            null, Integer.valueOf(1), "active", null, null, 0, 50));
            if (admins == null || admins.isEmpty()) return;
            long now = System.currentTimeMillis() / 1000L;
            for (UserAccount admin : admins) {
                InSiteMessage msg = new InSiteMessage();
                msg.setMsgType("whitelist_submitted");
                msg.setSenderUserId(app.getApplicantUserId());
                msg.setSenderName(nonNull(app.getUsername()));
                String senderUuid = (app.getOnlineUuid() != null && !app.getOnlineUuid().isEmpty())
                        ? app.getOnlineUuid() : app.getOfflineUuid();
                msg.setSenderUuid(nonNull(senderUuid));
                msg.setRecipientUserId(Integer.valueOf(admin.getId()));
                msg.setRecipientPlayerUuid(nonNull(admin.getPlayerUuid()));
                msg.setRecipientName(nonNull(admin.getUsername()));
                msg.setTitle("Whitelist application from " + app.getUsername());
                msg.setContent(app.getReason() != null ? app.getReason() : "");
                msg.setRelatedType("whitelist_application");
                msg.setRelatedId(String.valueOf(app.getId()));
                msg.setRead(false);
                msg.setDeliveredIngame(true);
                msg.setCreatedAt(now);
                MyBatisFactory.doWork(session -> session.getMapper(MessageMapper.class).insertMessage(msg));
                WebSocketController.pushInboxNotification(admin.getId(), msg);
            }
        } catch (Exception e) {
            PanelLog.get().warn("Failed to send whitelist submission inbox notice: {}", e.getMessage());
        }
    }

    public static void notifyApplicantWhitelistReviewed(WhitelistApplication app, boolean approved, String reviewNote) {
        if (app == null || app.getApplicantUserId() == null) return;
        try {
            InSiteMessage msg = new InSiteMessage();
            msg.setMsgType("whitelist_review");
            msg.setSenderUserId(app.getReviewerUserId());
            msg.setSenderName("Whitelist Review");
            msg.setSenderUuid("");
            msg.setRecipientUserId(app.getApplicantUserId());
            String recUuid = (app.getOnlineUuid() != null && !app.getOnlineUuid().isEmpty())
                    ? app.getOnlineUuid() : app.getOfflineUuid();
            msg.setRecipientPlayerUuid(nonNull(recUuid));
            msg.setRecipientName(nonNull(app.getUsername()));
            msg.setTitle(approved
                    ? ("Whitelist approved for " + app.getUsername())
                    : ("Whitelist application rejected for " + app.getUsername()));
            msg.setContent((reviewNote != null && !reviewNote.trim().isEmpty())
                    ? reviewNote.trim()
                    : (approved ? "Your whitelist application has been approved." : "Your whitelist application was declined."));
            msg.setRelatedType("whitelist_application");
            msg.setRelatedId(String.valueOf(app.getId()));
            msg.setRead(false);
            msg.setDeliveredIngame(false);
            msg.setCreatedAt(System.currentTimeMillis() / 1000L);
            MyBatisFactory.doWork(session -> session.getMapper(MessageMapper.class).insertMessage(msg));
            WebSocketController.pushInboxNotification(app.getApplicantUserId().intValue(), msg);
        } catch (Exception e) {
            PanelLog.get().warn("Failed to send whitelist review inbox notice: {}", e.getMessage());
        }
    }

    public static void recordInGameChat(String senderUuid, String senderName, String channel,
                                          String recipientName, String content) {
        if (content == null || content.trim().isEmpty()) return;
        String safeChannel = "whisper".equalsIgnoreCase(channel) ? "whisper" : "public";
        InGameChatEntry entry = new InGameChatEntry();
        entry.setChannel(safeChannel);
        entry.setSenderUuid(nonNull(senderUuid));
        entry.setSenderName(senderName != null && !senderName.isEmpty() ? senderName : "Server");
        entry.setRecipientUuid("");
        entry.setRecipientName(nonNull(recipientName));
        entry.setSource("game");
        entry.setContent(content.trim());
        entry.setCreatedAt(System.currentTimeMillis() / 1000L);

        try {
            MyBatisFactory.doWork(session -> session.getMapper(MessageMapper.class).insertChatLog(entry));
            WebSocketController.broadcastInGameChat(entry);
        } catch (Exception e) {
            PanelLog.get().debug("Chat persistence failed: {}", e.getMessage());
        }
    }

    public static InGameChatEntry sendWebPublicChat(int userId, boolean isAdmin, String content) {
        return sendWebPublicChat(userId, null, content);
    }

    public static InGameChatEntry sendWebPublicChat(int userId, String requestedPlayerUuid, String content) {
        if (!SettingsService.isWebChatSendEnabled()) {
            throw new IllegalStateException("Sending chat from web is disabled by administrator");
        }
        if (content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("Chat message cannot be empty");
        }
        UserAccount user = MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(UserMapper.class).findById(userId));
        if (user == null) {
            throw new IllegalStateException("User not found");
        }
        List<PlayerBindingInfo> bindings = BindingService.listByUserId(userId);
        if (bindings == null || bindings.isEmpty()) {
            throw new IllegalStateException("PLAYER_NOT_BOUND");
        }
        PlayerBindingInfo binding;
        if (requestedPlayerUuid != null && !requestedPlayerUuid.trim().isEmpty()) {
            binding = BindingService.findByUserAndUuid(userId, requestedPlayerUuid.trim());
            if (binding == null) {
                throw new SecurityException("Selected player is not bound to your account");
            }
        } else {
            binding = bindings.get(0);
        }

        String displayName = (binding.getPlayerName() != null && !binding.getPlayerName().trim().isEmpty())
                ? binding.getPlayerName()
                : user.getUsername();
        String playerUuid = binding.getPlayerUuid();
        String cleanContent = content.trim();
        if (cleanContent.length() > 256) cleanContent = cleanContent.substring(0, 256);

        GameAdapter adapter = AdapterRegistry.getNullable();
        if (adapter != null) {
            adapter.broadcastPublicChat(displayName, cleanContent);
        }

        InGameChatEntry entry = new InGameChatEntry();
        entry.setChannel("public");
        entry.setSenderUuid(nonNull(playerUuid));
        entry.setSenderName(nonNull(displayName));
        entry.setRecipientUuid("");
        entry.setRecipientName("");
        entry.setSource("web");
        entry.setContent(cleanContent);
        entry.setCreatedAt(System.currentTimeMillis() / 1000L);
        MyBatisFactory.doWork(session -> session.getMapper(MessageMapper.class).insertChatLog(entry));
        WebSocketController.broadcastInGameChat(entry);
        return entry;
    }

    public static List<InGameChatEntry> listChatHistory(int userId, boolean isAdmin, String channel, int page, int pageSize) {
        final int safeSize = Math.max(1, Math.min(200, pageSize));
        final int offset = Math.max(0, (page - 1) * safeSize);

        if ("whisper".equalsIgnoreCase(channel)) {
            List<String> lowerNames = null;
            if (!isAdmin) {
                List<PlayerBindingInfo> bindings = BindingService.listByUserId(userId);
                if (bindings == null || bindings.isEmpty()) return Collections.emptyList();
                lowerNames = new ArrayList<String>(bindings.size());
                for (PlayerBindingInfo info : bindings) {
                    if (info.getPlayerName() != null && !info.getPlayerName().trim().isEmpty()) {
                        lowerNames.add(info.getPlayerName().trim().toLowerCase(java.util.Locale.ROOT));
                    }
                }
                if (lowerNames.isEmpty()) return Collections.emptyList();
            }
            final List<String> filterNames = lowerNames;
            List<InGameChatEntry> raw = MyBatisFactory.doWorkWithResult(session ->
                    session.getMapper(MessageMapper.class).listWhispers(filterNames, offset, safeSize));
            if (raw == null) return Collections.emptyList();
            Collections.reverse(raw);
            return raw;
        }

        List<InGameChatEntry> raw = MyBatisFactory.doWorkWithResult(session ->
                session.getMapper(MessageMapper.class).listPublicChat(offset, safeSize));
        if (raw == null) return Collections.emptyList();
        Collections.reverse(raw);
        return raw;
    }

    /**
     * Notify an arriving player in-game if they have unread messages in their inbox.
     */
    public static void handlePlayerJoin(String playerName, String playerUuid) {
        if (playerUuid == null && playerName == null) return;
        try {
            PlayerBindingInfo binding = playerUuid != null ? BindingService.findByPlayerUuid(playerUuid) : null;
            Integer uid = binding != null ? Integer.valueOf(binding.getUserId()) : null;
            String uuidKey = playerUuid != null ? playerUuid
                    : UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8)).toString();

            int unread = MyBatisFactory.doWorkWithResult(session ->
                    Integer.valueOf(session.getMapper(MessageMapper.class).listUnreadForPlayer(uuidKey, uid, 50).size())).intValue();
            if (unread > 0) {
                GameAdapter adapter = AdapterRegistry.getNullable();
                if (adapter != null) {
                    adapter.sendDirectPlayerMessage(playerName != null ? playerName : uuidKey,
                            "[UtopiaServerPanel] You have " + unread + " unread message(s). Use /usp inbox to read.");
                }
            }
        } catch (Exception ignored) {}
    }

    public static List<InSiteMessage> getInGameInboxAndMarkDelivered(String playerUuid, String playerName, int limit) {
        PlayerBindingInfo binding = playerUuid != null ? BindingService.findByPlayerUuid(playerUuid) : null;
        final Integer uid = binding != null ? Integer.valueOf(binding.getUserId()) : null;
        final String keyUuid = playerUuid != null ? playerUuid
                : UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8)).toString();

        return MyBatisFactory.doWorkWithResult(session -> {
            MessageMapper mapper = session.getMapper(MessageMapper.class);
            List<InSiteMessage> list = mapper.listUnreadForPlayer(keyUuid, uid, limit);
            mapper.markDeliveredIngame(keyUuid, uid);
            return list != null ? list : Collections.<InSiteMessage>emptyList();
        });
    }

    private static boolean tryDeliverInGameLive(String playerName, String playerUuid, String senderName, String content) {
        GameAdapter adapter = AdapterRegistry.getNullable();
        if (adapter == null) return false;
        String onlineTarget = null;
        for (OnlinePlayerSnapshot p : adapter.getOnlinePlayers()) {
            if ((playerUuid != null && playerUuid.equalsIgnoreCase(p.getUuid()))
                    || (playerName != null && playerName.equalsIgnoreCase(p.getName()))) {
                onlineTarget = p.getName();
                break;
            }
        }
        if (onlineTarget == null) return false;
        adapter.sendDirectPlayerMessage(onlineTarget, "[Mail from " + senderName + "] " + content);
        return true;
    }

    private static TargetResolution resolveTarget(String identifier) {
        return MyBatisFactory.doWorkWithResult(session -> {
            UserMapper uMapper = session.getMapper(UserMapper.class);
            PlayerBindingMapper pbMapper = session.getMapper(PlayerBindingMapper.class);

            UserAccount byUser = uMapper.findByUsername(identifier);
            if (byUser != null) {
                TargetResolution r = new TargetResolution();
                r.userId = Integer.valueOf(byUser.getId());
                r.playerUuid = byUser.getPlayerUuid();
                r.playerName = byUser.getPlayerName();
                r.displayName = byUser.getUsername();
                return r;
            }

            PlayerBindingInfo byPlayer = pbMapper.findByPlayerName(identifier);
            if (byPlayer != null) {
                TargetResolution r = new TargetResolution();
                r.userId = Integer.valueOf(byPlayer.getUserId());
                r.playerUuid = byPlayer.getPlayerUuid();
                r.playerName = byPlayer.getPlayerName();
                r.displayName = byPlayer.getPlayerName();
                return r;
            }

            // Check online players in case sender targets an unbound MC player
            GameAdapter adapter = AdapterRegistry.getNullable();
            if (adapter != null) {
                for (OnlinePlayerSnapshot p : adapter.getOnlinePlayers()) {
                    if (identifier.equalsIgnoreCase(p.getName())) {
                        TargetResolution r = new TargetResolution();
                        r.playerUuid = p.getUuid();
                        r.playerName = p.getName();
                        r.displayName = p.getName();
                        return r;
                    }
                }
            }
            return null;
        });
    }

    private static String nonNull(String value) {
        return value != null ? value : "";
    }

    private static final class TargetResolution {
        Integer userId;
        String playerUuid;
        String playerName;
        String displayName;
    }
}
