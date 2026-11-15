package com.utopiaxc.utopiaserverpanel.web.db.model;

/**
 * Persisted in-game public chat or whisper log synced with the in-site message center.
 */
public class InGameChatEntry {
    private int id;
    private String channel;
    private String senderUuid;
    private String senderName;
    private String recipientUuid;
    private String recipientName;
    private String source;
    private String content;
    private long createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getSenderUuid() { return senderUuid; }
    public void setSenderUuid(String senderUuid) { this.senderUuid = senderUuid; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getRecipientUuid() { return recipientUuid; }
    public void setRecipientUuid(String recipientUuid) { this.recipientUuid = recipientUuid; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
