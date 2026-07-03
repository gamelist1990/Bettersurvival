package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService;

/**
 * 個人情報保護法対応の本人請求（開示・訂正・削除・利用停止）1件分。
 */
public class PrivacyRequest {

    public static final String STATUS_OPEN = "open";
    public static final String STATUS_RESOLVED = "resolved";

    private String id = "";
    /** 申請者の Minecraft UUID（ログインセッションから取得＝本人確認） */
    private String uuid = "";
    private String username = "";
    /** disclosure / correction / deletion / suspension */
    private String type = "disclosure";
    private String detail = "";
    private String status = STATUS_OPEN;
    private long createdAt = System.currentTimeMillis();
    private long resolvedAt;

    public String getId() { return id == null ? "" : id; }
    public void setId(String id) { this.id = id == null ? "" : id; }
    public String getUuid() { return uuid == null ? "" : uuid; }
    public void setUuid(String uuid) { this.uuid = uuid == null ? "" : uuid; }
    public String getUsername() { return username == null ? "" : username; }
    public void setUsername(String username) { this.username = username == null ? "" : username; }
    public String getType() { return type == null ? "disclosure" : type; }
    public void setType(String type) { this.type = type == null ? "disclosure" : type; }
    public String getDetail() { return detail == null ? "" : detail; }
    public void setDetail(String detail) { this.detail = detail == null ? "" : detail; }
    public String getStatus() { return status == null ? STATUS_OPEN : status; }
    public void setStatus(String status) { this.status = status == null ? STATUS_OPEN : status; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public long getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(long resolvedAt) { this.resolvedAt = resolvedAt; }

    public boolean isOpen() {
        return STATUS_OPEN.equals(getStatus());
    }

    /** 申請種別の日本語表示。 */
    public String typeDisplayName() {
        return switch (getType()) {
            case "correction" -> "訂正";
            case "deletion" -> "削除";
            case "suspension" -> "利用停止";
            default -> "開示";
        };
    }
}
