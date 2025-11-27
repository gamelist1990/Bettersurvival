package org.pexserver.koukunn.bettersurvival.Modules.Feature.BedrockSkin;

/**
 * アップロード済みスキンのキャッシュエントリ
 * value には Base64 エンコードされたテクスチャ情報（スキンと Cape の両方）が含まれる
 */
public class SkinEntry {
    private final String value;
    private final String signature;
    private final long uploadedAt;

    public SkinEntry(String value, String signature, long uploadedAt) {
        this.value = value;
        this.signature = signature;
        this.uploadedAt = uploadedAt;
    }

    public String getValue() {
        return value;
    }

    public String getSignature() {
        return signature;
    }

    public long getUploadedAt() {
        return uploadedAt;
    }

    /**
     * キャッシュが有効かどうか (24時間以内)
     */
    public boolean isValid() {
        return System.currentTimeMillis() - uploadedAt < 24 * 60 * 60 * 1000L;
    }
}
