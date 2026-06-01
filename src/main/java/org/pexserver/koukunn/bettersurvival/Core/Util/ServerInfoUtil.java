package org.pexserver.koukunn.bettersurvival.Core.Util;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * サーバー情報を取得するユーティリティ。
 * <p>
 * MOTD（server.properties の motd）を優先的にサーバー名として使用し、
 * 取得できない場合は実装名にフォールバックする。
 * </p>
 */
public final class ServerInfoUtil {

    private ServerInfoUtil() {}

    /**
     * サーバーの表示名を返す。
     * <p>
     * MOTD が ASCII 1 行に収まる場合はそれを使用し、
     * 取得できない場合は {@code Bukkit.getServer().getName()} を使用する。
     * それも空であれば {@code "Minecraft Server"} を返す。
     * </p>
     *
     * @return サーバー表示名（空文字にはならない）
     */
    public static String getServerName() {
        String motd = PlainTextComponentSerializer.plainText().serialize(Bukkit.motd());
        if (motd != null && !motd.isBlank()) {
            String singleLine = motd.replace('\n', ' ').trim();
            if (!singleLine.isBlank()) {
                return singleLine;
            }
        }
        String impl = Bukkit.getServer().getName();
        if (impl != null && !impl.isBlank()) {
            return impl.trim();
        }
        return "Minecraft Server";
    }

    /**
     * サーバーの説明文（MOTD 全文）を返す。
     * <p>
     * 改行を含む MOTD をそのまま返す。取得できない場合は空文字を返す。
     * </p>
     *
     * @return MOTD テキスト
     */
    public static String getServerDescription() {
        String motd = PlainTextComponentSerializer.plainText().serialize(Bukkit.motd());
        return motd != null ? motd.trim() : "";
    }

    /**
     * サーバーのバージョン文字列を返す。
     *
     * @return バージョン文字列
     */
    public static String getServerVersion() {
        return Bukkit.getServer().getVersion();
    }

    /**
     * サーバーのポート番号を返す。
     *
     * @param plugin 設定読み取り用のプラグインインスタンス
     * @return ポート番号
     */
    public static int getServerPort(Plugin plugin) {
        return Bukkit.getServer().getPort();
    }
}
