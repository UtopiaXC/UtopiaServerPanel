package com.utopiaxc.utopiaserverpanel.web.db.model;

/**
 * In-site notification, private message, and offline player mail record.
 */
public class InSiteMessage {
    private int id;
    private String msgType;
    private Integer senderUserId;
    private String senderName;
    private String senderUuid;
    private Integer recipientUserId;
    private String recipientPlayerUuid;
    private String recipientName;
    private String title;
    private String content;
    private String relatedType;
    private String relatedId;
    private boolean read;
    private boolean deliveredIngame;
    private long createdAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getMsgType() { return msgType; }
    public void setMsgType(String msgType) { this.msgType = msgType; }

    public Integer getSenderUserId() { return senderUserId; }
    public void setSenderUserId(Integer senderUserId) { this.senderUserId = senderUserId; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getSenderUuid() { return senderUuid; }
    public void setSenderUuid(String senderUuid) { this.senderUuid = senderUuid; }

    public Integer getRecipientUserId() { return recipientUserId; }
    public void setRecipientUserId(Integer recipientUserId) { this.recipientUserId = recipientUserId; }

    public String getRecipientPlayerUuid() { return recipientPlayerUuid; }
    public void setRecipientPlayerUuid(String recipientPlayerUuid) { this.recipientPlayerUuid = recipientPlayerUuid; }

    public String getRecipientName() { return recipientName; }
    public void setRecipientName(String recipientName) { this.recipientName = recipientName; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getRelatedType() { return relatedType; }
    public void setRelatedType(String relatedType) { this.relatedType = relatedType; }

    public String getRelatedId() { return relatedId; }
    public void setRelatedId(String relatedId) { this.relatedId = relatedId; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public boolean isDeliveredIngame() { return deliveredIngame; }
    public void setDeliveredIngame(boolean deliveredIngame) { this.deliveredIngame = deliveredIngame; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
