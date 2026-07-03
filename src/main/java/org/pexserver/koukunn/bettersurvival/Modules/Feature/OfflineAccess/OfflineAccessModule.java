package org.pexserver.koukunn.bettersurvival.Modules.Feature.OfflineAccess;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

/**
 * OfflineAccess 機能のエントリーポイント。
 * <p>
 * 有効時に Netty パイプラインへ {@link LoginInterceptor} を注入し、
 * 許可されたオフラインプレイヤーのログインを通過させる。
 * </p>
 */
public class OfflineAccessModule implements Listener {

    private final Plugin plugin;
    private final OfflineAccessManager manager;

    public OfflineAccessModule(Plugin plugin, ConfigManager configManager, ToggleModule toggleModule) {
        this.plugin = plugin;
        this.manager = new OfflineAccessManager(plugin, configManager, toggleModule);
    }

    /**
     * サーバー接続に Netty ハンドラを注入する。
     * <p>
     * 有効/無効は {@link LoginInterceptor} 実行時に判定されるため、
     * ここでは常にパイプラインを登録する。
     * </p>
     */
    public void inject() {
        try {
            NettyInjector.inject(manager);
            plugin.getLogger().info("[OfflineAccess] Netty パイプラインにインジェクションしました（有効/無効は /toggle op で制御）");
        } catch (Exception e) {
            plugin.getLogger().severe("[OfflineAccess] Netty インジェクションに失敗しました: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * マネージャーを取得する。
     *
     * @return OfflineAccessManager
     */
    public OfflineAccessManager getManager() {
        return manager;
    }
}
