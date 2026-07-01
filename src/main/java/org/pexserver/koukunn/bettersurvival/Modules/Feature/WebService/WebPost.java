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

    public static class Attachment {
        private String type = "image";
        private String url = "";
        private int width;
        private int height;

        public String getType() { return type; }
        public void setType(String type) { this.type = type == null ? "image" : type; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url == null ? "" : url; }
        public int getWidth() { return width; }
        public void setWidth(int width) { this.width = width; }
        public int getHeight() { return height; }
        public void setHeight(int height) { this.height = height; }
    }
}
