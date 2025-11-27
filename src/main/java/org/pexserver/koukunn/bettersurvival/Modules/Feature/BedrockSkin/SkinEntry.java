package org.pexserver.koukunn.bettersurvival.Modules.Feature.BedrockSkin;

/**
 * アップロード済みスキンのキャッシュエントリ
 */
public class SkinEntry {
    private final String value;
    private final String signature;
    private final String skinUrl;
    private final String model;
    private final long uploadedAt;

    public SkinEntry(String value, String signature, String skinUrl, String model, long uploadedAt) {
        this.value = value;
        this.signature = signature;
        this.skinUrl = skinUrl;
        this.model = model;
        this.uploadedAt = uploadedAt;
    }

    public String getValue() {
        return value;
    }

    public String getSignature() {
        return signature;
    }

    public String getSkinUrl() {
        return skinUrl;
    }

    public String getModel() {
        return model;
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
