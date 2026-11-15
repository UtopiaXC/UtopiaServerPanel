package com.utopiaxc.utopiaserverpanel.web.db.model;

/**
 * Player whitelist review application persisted per save database.
 */
public class WhitelistApplication {
    private int id;
    private String username;
    private String onlineUuid;
    private String offlineUuid;
    private String preferredUuidMode;
    private String email;
    private String reason;
    private String socialAccount;
    private String status;
    private Integer applicantUserId;
    private boolean bindOnApprove;
    private String submitIp;
    private Integer reviewerUserId;
    private String reviewNote;
    private long createdAt;
    private Long reviewedAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getOnlineUuid() { return onlineUuid; }
    public void setOnlineUuid(String onlineUuid) { this.onlineUuid = onlineUuid; }

    public String getOfflineUuid() { return offlineUuid; }
    public void setOfflineUuid(String offlineUuid) { this.offlineUuid = offlineUuid; }

    public String getPreferredUuidMode() { return preferredUuidMode; }
    public void setPreferredUuidMode(String preferredUuidMode) { this.preferredUuidMode = preferredUuidMode; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getSocialAccount() { return socialAccount; }
    public void setSocialAccount(String socialAccount) { this.socialAccount = socialAccount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getApplicantUserId() { return applicantUserId; }
    public void setApplicantUserId(Integer applicantUserId) { this.applicantUserId = applicantUserId; }

    public boolean isBindOnApprove() { return bindOnApprove; }
    public void setBindOnApprove(boolean bindOnApprove) { this.bindOnApprove = bindOnApprove; }

    public String getSubmitIp() { return submitIp; }
    public void setSubmitIp(String submitIp) { this.submitIp = submitIp; }

    public Integer getReviewerUserId() { return reviewerUserId; }
    public void setReviewerUserId(Integer reviewerUserId) { this.reviewerUserId = reviewerUserId; }

    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public Long getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Long reviewedAt) { this.reviewedAt = reviewedAt; }
}
