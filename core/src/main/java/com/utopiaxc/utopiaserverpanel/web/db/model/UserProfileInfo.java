package com.utopiaxc.utopiaserverpanel.web.db.model;

/**
 * Extended panel user profile fields (avatar, gender, birthday, bio, social contacts)
 * and serialized public homepage privacy settings stored in {@code user_profiles}.
 */
public class UserProfileInfo {
    private int userId;
    private String avatarData;
    private String gender;
    private String birthday;
    private String bio;
    private String socialAccounts;
    private String privacyJson;
    private long updatedAt;

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getAvatarData() { return avatarData; }
    public void setAvatarData(String avatarData) { this.avatarData = avatarData; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getBirthday() { return birthday; }
    public void setBirthday(String birthday) { this.birthday = birthday; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public String getSocialAccounts() { return socialAccounts; }
    public void setSocialAccounts(String socialAccounts) { this.socialAccounts = socialAccounts; }

    public String getPrivacyJson() { return privacyJson; }
    public void setPrivacyJson(String privacyJson) { this.privacyJson = privacyJson; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
