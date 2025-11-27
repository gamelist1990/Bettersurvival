package org.pexserver.koukunn.bettersurvival.Modules.Feature.BedrockSkin;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * Bedrock スキンを Java 用のテクスチャレイアウトに変換するユーティリティ
 */
public final class SkinUtils {

    private SkinUtils() {}

    /**
     * RawSkin を BufferedImage に変換
     * Bedrock のスキンデータ (RGBA byte配列) を Java テクスチャ形式に変換
     */
    public static BufferedImage toBufferedImage(RawSkin rawSkin) {
        int width = rawSkin.skinWidth;
        int height = rawSkin.skinHeight;
        byte[] data = rawSkin.skinData;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (index + 3 >= data.length) break;

                int r = data[index++] & 0xFF;
                int g = data[index++] & 0xFF;
                int b = data[index++] & 0xFF;
                int a = data[index++] & 0xFF;

                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                image.setRGB(x, y, argb);
            }
        }

        // 64x64 に正規化
        return normalizeToJava(image, width, height);
    }

    /**
     * スキンを Java の標準形式 (64x64) に正規化
     */
    private static BufferedImage normalizeToJava(BufferedImage original, int width, int height) {
        // 既に 64x64 の場合はそのまま返す
        if (width == 64 && height == 64) {
            return original;
        }

        // 64x32 (旧形式) の場合は 64x64 に拡張
        if (width == 64 && height == 32) {
            BufferedImage result = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = result.createGraphics();
            g.drawImage(original, 0, 0, null);
            g.dispose();
            return result;
        }

        // 128x128 (高解像度) の場合は 64x64 に縮小
        if (width == 128 && height == 128) {
            BufferedImage result = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = result.createGraphics();
            g.drawImage(original, 0, 0, 64, 64, null);
            g.dispose();
            return result;
        }

        // その他のサイズの場合は 64x64 にリサイズ
        BufferedImage result = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(original, 0, 0, 64, 64, null);
        g.dispose();
        return result;
    }

    /**
     * スキンデータのハッシュを計算 (キャッシュキー用)
     */
    public static String computeSkinHash(byte[] skinData) {
        if (skinData == null || skinData.length == 0) {
            return "empty";
        }
        
        // 簡易ハッシュ (MurmurHash3 風)
        long hash = 0;
        for (int i = 0; i < skinData.length; i++) {
            hash = hash * 31 + (skinData[i] & 0xFF);
        }
        return Long.toHexString(hash);
    }
}
