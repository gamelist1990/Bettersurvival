package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService;

import java.util.ArrayList;
import java.util.List;

public class WebPost {
    private String id = "";
    private String uuid = "";
    private String username = "";
    private String displayName = "";
    private String nickname = "";
    private String faceUrl = "";
    private String source = "minecraft";
    private String text = "";
    private List<Attachment> attachments = new ArrayList<>();
    private long createdAt = System.currentTimeMillis();
    private String replyToId = "";
    private String replyToUsername = "";
    private String externalId = "";
    private String externalUrl = "";
    private String externalChannelId = "";
    private String externalAuthorId = "";
    private String externalReplyToId = "";
    private int likes;
    private int replies;
    private int reposts;
    private long views;
    private List<String> likedBy = new ArrayList<>();
    private List<String> repostedBy = new ArrayList<>();
    private boolean deleted;

    public String getId() { return id; }
    public void setId(String id) { this.id = id == null ? "" : id; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid == null ? "" : uuid; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username == null ? "" : username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName == null ? "" : displayName; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname == null ? "" : nickname; }
    public String getFaceUrl() { return faceUrl; }
    public void setFaceUrl(String faceUrl) { this.faceUrl = faceUrl == null ? "" : faceUrl; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source == null ? "minecraft" : source; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text == null ? "" : text; }
    public List<Attachment> getAttachments() { return attachments; }
    public void setAttachments(List<Attachment> attachments) { this.attachments = attachments == null ? new ArrayList<>() : attachments; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public String getReplyToId() { return replyToId; }
    public void setReplyToId(String replyToId) { this.replyToId = replyToId == null ? "" : replyToId; }
    public String getReplyToUsername() { return replyToUsername; }
    public void setReplyToUsername(String replyToUsername) { this.replyToUsername = replyToUsername == null ? "" : replyToUsername; }
    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId == null ? "" : externalId; }
    public String getExternalUrl() { return externalUrl; }
    public void setExternalUrl(String externalUrl) { this.externalUrl = externalUrl == null ? "" : externalUrl; }
    public String getExternalChannelId() { return externalChannelId; }
    public void setExternalChannelId(String externalChannelId) { this.externalChannelId = externalChannelId == null ? "" : externalChannelId; }
    public String getExternalAuthorId() { return externalAuthorId; }
    public void setExternalAuthorId(String externalAuthorId) { this.externalAuthorId = externalAuthorId == null ? "" : externalAuthorId; }
    public String getExternalReplyToId() { return externalReplyToId; }
    public void setExternalReplyToId(String externalReplyToId) { this.externalReplyToId = externalReplyToId == null ? "" : externalReplyToId; }
    public int getLikes() { return Math.max(0, likes); }
    public void setLikes(int likes) { this.likes = Math.max(0, likes); }
    public int getReplies() { return Math.max(0, replies); }
    public void setReplies(int replies) { this.replies = Math.max(0, replies); }
    public int getReposts() { return Math.max(0, reposts); }
    public void setReposts(int reposts) { this.reposts = Math.max(0, reposts); }
    public long getViews() { return Math.max(0, views); }
    public void setViews(long views) { this.views = Math.max(0, views); }
    public void incrementViews() { this.views = getViews() + 1; }
    public List<String> getLikedBy() { return likedBy == null ? new ArrayList<>() : likedBy; }
    public void setLikedBy(List<String> likedBy) { this.likedBy = likedBy == null ? new ArrayList<>() : likedBy; }
    public List<String> getRepostedBy() { return repostedBy == null ? new ArrayList<>() : repostedBy; }
    public void setRepostedBy(List<String> repostedBy) { this.repostedBy = repostedBy == null ? new ArrayList<>() : repostedBy; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public boolean isLikedBy(String uuid) { return uuid != null && !uuid.isBlank() && getLikedBy().contains(uuid); }
    public boolean isRepostedBy(String uuid) { return uuid != null && !uuid.isBlank() && getRepostedBy().contains(uuid); }

    public boolean toggleLike(String uuid) {
        if (uuid == null || uuid.isBlank()) return false;
        List<String> users = getLikedBy();
        if (users.remove(uuid)) {
            setLikedBy(users);
            setLikes(users.size());
            return false;
        }
        users.add(uuid);
        setLikedBy(users);
        setLikes(users.size());
        return true;
    }

    public boolean toggleRepost(String uuid) {
        if (uuid == null || uuid.isBlank()) return false;
        List<String> users = getRepostedBy();
        if (users.remove(uuid)) {
            setRepostedBy(users);
            setReposts(users.size());
            return false;
        }
        users.add(uuid);
        setRepostedBy(users);
        setReposts(users.size());
        return true;
    }

    public static class Attachment {
        private String type = "image";
        private String url = "";
        private String name = "";
        private int width;
        private int height;

        public String getType() { return type; }
        public void setType(String type) { this.type = type == null ? "image" : type; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url == null ? "" : url; }
        public String getName() { return name == null ? "" : name; }
        public void setName(String name) { this.name = name == null ? "" : name; }
        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }
        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
    }
}
