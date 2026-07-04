package org.pexserver.koukunn.bettersurvival.Core.Util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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


    public static String getBedrockPrefix() {
        FloodgateApi api = api();
        if (api != null) {
            try {
                String prefix = api.getPlayerPrefix();
                return prefix == null ? "" : prefix;
            } catch (NoClassDefFoundError e) {
                return getBedrockPrefixFromConfig();
            }
        }
        return getBedrockPrefixFromConfig();
    }

    private static String getBedrockPrefixFromConfig() {
        Path configPath = Bukkit.getWorldContainer().toPath().resolve("plugins").resolve("floodgate").resolve("config.yml");
        if (!Files.isRegularFile(configPath)) {
            return "";
        }
        try {
            for (String line : Files.readAllLines(configPath)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("#") || !trimmed.startsWith("username-prefix:")) {
                    continue;
                }
                String value = trimmed.substring("username-prefix:".length()).trim();
                if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        } catch (IOException ignored) {
            return "";
        }
        return "";
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

    /**
     * プレイヤー名が Bedrock (Floodgate) 形式か (プレフィックスで判定)
     */
    public static boolean isBedrockName(String name) {
        if (name == null) return false;
        String prefix = getBedrockPrefix();
        if (prefix != null && !prefix.isEmpty() && name.startsWith(prefix)) {
            return true;
        }
        return false;
    }

    /**
     * Bedrock プレイヤー名からプレフィックスを取り除く
     */
    public static String stripPrefix(String name) {
        if (name == null) return "";
        String prefix = getBedrockPrefix();
        if (prefix != null && !prefix.isEmpty() && name.startsWith(prefix)) {
            return name.substring(prefix.length());
        }
        return name;
    }
}
