package org.pexserver.koukunn.bettersurvival.Core.Util;

// Bukkit import not required
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.UUID;

/**
 * Floodgate / Bedrock 判定用ユーティリティ
 * - Floodgate API は compileOnly で依存されているため、実行時に存在しない環境でも安全に動作する
 */
public final class FloodgateUtil {

    private FloodgateUtil() {}

    private static FloodgateApi api() {
        try {
            return FloodgateApi.getInstance();
        } catch (NoClassDefFoundError | Exception e) {
            return null;
        }
    }

    /**
     * Floodgate がインストールされているか
     */
    public static boolean isFloodgateInstalled() {
        return api() != null;
    }

    /**
     * 指定UUIDがBedrock (Floodgate) プレイヤーか
     */
    public static boolean isBedrock(UUID uuid) {
        FloodgateApi api = api();
        if (api == null) return false;
        try {
            return api.isFloodgatePlayer(uuid);
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    /**
     * プレイヤーがBedrock (Floodgate) か
     */
    public static boolean isBedrock(Player p) {
        if (p == null) return false;
        return isBedrock(p.getUniqueId());
    }
}
