package org.pexserver.koukunn.bettersurvival.Modules.Feature.Tpa;


/**
 * TPAリクエストのデータモデル
 * 送信者が受信者にテレポートをリクエストする情報を保持
 */
public class TpaRequest {

    private final String senderUuid;
    private final String senderName;
    private final String targetUuid;
    private final String targetName;
    private final long timestamp;

    public TpaRequest(String senderUuid, String senderName, String targetUuid, String targetName) {
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.timestamp = System.currentTimeMillis();
    }

    public TpaRequest(String senderUuid, String senderName, String targetUuid, String targetName, long timestamp) {
        this.senderUuid = senderUuid;
        this.senderName = senderName;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.timestamp = timestamp;
    }

    public String getSenderUuid() {
        return senderUuid;
    }

    public String getSenderName() {
        return senderName;
    }

    public String getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * リクエストの有効期限をチェック（5分）
     * @return 期限切れならtrue
     */
    public boolean isExpired() {
        return System.currentTimeMillis() - timestamp > 5 * 60 * 1000;
    }

    /**
     * リクエストの残り時間（秒）を取得
     * @return 残り秒数（期限切れなら0）
     */
    public int getRemainingSeconds() {
        long elapsed = System.currentTimeMillis() - timestamp;
        long remaining = (5 * 60 * 1000) - elapsed;
        return remaining > 0 ? (int) (remaining / 1000) : 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof TpaRequest)) return false;
        TpaRequest other = (TpaRequest) obj;
        return senderUuid.equals(other.senderUuid) && targetUuid.equals(other.targetUuid);
    }

    @Override
    public int hashCode() {
        return senderUuid.hashCode() + targetUuid.hashCode();
    }
}
