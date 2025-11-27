package org.pexserver.koukunn.bettersurvival.Modules.Feature.BedrockSkin;

/**
 * Bedrock スキンの生データを保持するクラス
 */
public class RawSkin {
    public final byte[] skinData;
    public final int skinWidth;
    public final int skinHeight;
    public final boolean alex;
    public final String geometryName;

    public RawSkin(byte[] skinData, int skinWidth, int skinHeight, boolean alex, String geometryName) {
        this.skinData = skinData;
        this.skinWidth = skinWidth;
        this.skinHeight = skinHeight;
        this.alex = alex;
        this.geometryName = geometryName;
    }

    /**
     * 64x64 のスキンかどうか
     */
    public boolean isStandard64x64() {
        return skinWidth == 64 && skinHeight == 64;
    }

    /**
     * 64x32 の旧形式スキンかどうか
     */
    public boolean isLegacy64x32() {
        return skinWidth == 64 && skinHeight == 32;
    }

    /**
     * 128x128 の高解像度スキンかどうか
     */
    public boolean isHighRes128x128() {
        return skinWidth == 128 && skinHeight == 128;
    }
}
