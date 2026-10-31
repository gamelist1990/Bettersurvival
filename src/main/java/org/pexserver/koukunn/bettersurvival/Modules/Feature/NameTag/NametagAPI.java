package org.pexserver.koukunn.bettersurvival.Modules.Feature.NameTag;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * NametagHandlerのシンプルAPIラッパー
 * デフォルト名（§f + プレイヤー名）とカスタム名の2モードのみをサポート
 */
public class NametagAPI {
    /**
     * NametagHandlerを初期化（プラグイン起動時に一度だけ呼ぶ）
     */
    public static void initialize(Plugin plugin) {
        if (NametagHandler.getInstance() == null) {
            new NametagHandler(plugin);
        }
    }

    /**
     * プレイヤーのネームタグを生成
     */
    public static void spawnNametag(Player player) {
        NametagHandler handler = NametagHandler.getInstance();
        if (handler != null) handler.spawnNametag(player);
    }

    /**
     * プレイヤーのネームタグを削除
     */
    public static void removeNametag(Player player) {
        NametagHandler handler = NametagHandler.getInstance();
        if (handler != null) handler.removeNametag(player);
    }

    /**
     * プレイヤーのカスタムネームタグを設定（即時反映）
     * 例: NametagAPI.setName(player, Arrays.asList(Component.text("Name"), Component.text("HP: 10/10")))
     */
    public static void setName(Player player, List<Component> lines) {
        NametagHandler handler = NametagHandler.getInstance();
        if (handler != null) handler.setCustomName(player, lines);
    }

    /**
     * カスタムネームタグをクリアしてデフォルト名に戻す
     */
    public static void clearName(Player player) {
        NametagHandler handler = NametagHandler.getInstance();
        if (handler != null) handler.clearCustomName(player);
    }

    /**
     * ネームタグの更新間隔を設定
     */
    public static void setUpdateInterval(int ticks) {
        NametagHandler handler = NametagHandler.getInstance();
        if (handler != null) handler.setUpdateInterval(ticks);
    }

    /**
     * ネームタグの表示距離を設定
     */
    public static void setViewDistance(int blocks) {
        NametagHandler handler = NametagHandler.getInstance();
        if (handler != null) handler.setViewDistance(blocks);
    }

    /**
     * ネームタグの高さを設定
     */
    public static void setNametagHeight(double height) {
        NametagHandler handler = NametagHandler.getInstance();
        if (handler != null) handler.setNametagHeight(height);
    }

    /**
     * ネームタグの行間隔を設定
     */
    public static void setLineSpacing(double spacing) {
        NametagHandler handler = NametagHandler.getInstance();
        if (handler != null) handler.setLineSpacing(spacing);
    }

    /**
     * ネームタグの seeThrough を設定
     */
    public static void setSeeThrough(boolean seeThrough) {
        NametagHandler handler = NametagHandler.getInstance();
        if (handler != null) handler.setSeeThrough(seeThrough);
    }

    public static boolean isSeeThrough() {
        NametagHandler handler = NametagHandler.getInstance();
        return handler != null && handler.isSeeThrough();
    }

    public static void setSeeThroughResolver(BiPredicate<Player, Player> resolver) {
        NametagHandler handler = NametagHandler.getInstance();
        if (handler != null) handler.setSeeThroughResolver(resolver);
    }

    public static void setVisibilityResolver(BiPredicate<Player, Player> resolver) {
        NametagHandler handler = NametagHandler.getInstance();
        if (handler != null) handler.setVisibilityResolver(resolver);
    }

    /**
     * 視線に向けられたプレイヤーのネームタグを自動的に表示するモードを切り替えます 
     */
    public static void setLookModeEnabled(Player viewer, boolean enabled) {
        NametagHandler handler = NametagHandler.getInstance();
        if (handler != null) handler.setLookModeEnabled(viewer, enabled);
    }

    public static boolean isLookModeEnabled(Player viewer) {
        NametagHandler handler = NametagHandler.getInstance();
        return handler != null && handler.isLookModeEnabled(viewer);
    }

    /**
     * 個別プレイヤーのネームタグを完全に非表示にします（人狼用:機能テストはまだ)
     */
    public static void hideNametag(Player owner) {
        NametagHandler handler = NametagHandler.getInstance();
        if (handler != null) handler.hideNametag(owner);
    }

    public static void showNametag(Player owner) {
        NametagHandler handler = NametagHandler.getInstance();
        if (handler != null) handler.showNametag(owner);
    }

    public static boolean isNametagHidden(Player owner) {
        NametagHandler handler = NametagHandler.getInstance();
        return handler != null && handler.isNametagHidden(owner);
    }

    /**
     * ネームタグをリロード
     */
    public static void reload() {
        NametagHandler handler = NametagHandler.getInstance();
        if (handler != null) handler.reload();
    }

    /**
     * シャットダウン処理
     */
    public static void shutdown() {
        NametagHandler handler = NametagHandler.getInstance();
        if (handler != null) handler.shutdown();
    }
}
