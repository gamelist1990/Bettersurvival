package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService;

public class WebProfile {
    private String uuid = "";
    private String username = "";
    private String displayName = "";
    private String faceUrl = "";
    private String email = "";
    private String passwordSalt = "";
    private String passwordHash = "";
    private boolean passkeyEnabled = false;
    private String passkeyLabel = "";
    private String nickname = "";
    private String bio = "Minecraft と BetterSurvival のプロフィール";
    private String location = "";
    private String country = "";
    private String region = "";
    private String bannerUrl = "";
    private String website = "";
    private String xUrl = "";
    private String youtubeUrl = "";
    private String instagramUrl = "";
    private long createdAt = System.currentTimeMillis();
    private long updatedAt = System.currentTimeMillis();

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid == null ? "" : uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username == null ? "" : username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? "" : displayName;
    }

    public String getFaceUrl() {
        return faceUrl;
    }

    public void setFaceUrl(String faceUrl) {
        this.faceUrl = faceUrl == null ? "" : faceUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? "" : email;
    }

    public String getPasswordSalt() {
        return passwordSalt;
    }

    public void setPasswordSalt(String passwordSalt) {
        this.passwordSalt = passwordSalt == null ? "" : passwordSalt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash == null ? "" : passwordHash;
    }

    public boolean isPasskeyEnabled() {
        return passkeyEnabled;
    }

    public void setPasskeyEnabled(boolean passkeyEnabled) {
        this.passkeyEnabled = passkeyEnabled;
    }

    public String getPasskeyLabel() {
        return passkeyLabel;
    }

    public void setPasskeyLabel(String passkeyLabel) {
        this.passkeyLabel = passkeyLabel == null ? "" : passkeyLabel;
    }

    public String getBio() {
        return bio;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname == null ? "" : nickname;
    }

    public void setBio(String bio) {
        this.bio = bio == null ? "" : bio;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location == null ? "" : location;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country == null ? "" : country;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region == null ? "" : region;
    }

    public String getBannerUrl() {
        return bannerUrl;
    }

    public void setBannerUrl(String bannerUrl) {
        this.bannerUrl = bannerUrl == null ? "" : bannerUrl;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website == null ? "" : website;
    }

    public String getXUrl() {
        return xUrl;
    }

    public void setXUrl(String xUrl) {
        this.xUrl = xUrl == null ? "" : xUrl;
    }

    public String getYoutubeUrl() {
        return youtubeUrl;
    }

    public void setYoutubeUrl(String youtubeUrl) {
        this.youtubeUrl = youtubeUrl == null ? "" : youtubeUrl;
    }

    public String getInstagramUrl() {
        return instagramUrl;
    }

    public void setInstagramUrl(String instagramUrl) {
        this.instagramUrl = instagramUrl == null ? "" : instagramUrl;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
