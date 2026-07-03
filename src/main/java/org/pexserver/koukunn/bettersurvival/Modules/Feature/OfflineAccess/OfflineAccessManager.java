package org.pexserver.koukunn.bettersurvival.Modules.Feature.OfflineAccess;

import org.bukkit.plugin.Plugin;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.Set;
import java.util.logging.Level;

/**
 * オフラインアクセス機能の状態と許可リストを管理する。
 */
public class OfflineAccessManager {

    private static final String TOGGLE_KEY = "offlineaccess";

    private final Plugin plugin;
    private final OfflineAccessStore store;
    private final ToggleModule toggleModule;

    public OfflineAccessManager(Plugin plugin, ConfigManager configManager, ToggleModule toggleModule) {
        this.plugin = plugin;
        this.store = new OfflineAccessStore(configManager);
        this.toggleModule = toggleModule;
    }

    /**
     * プラグインインスタンスを返す。
     *
     * @return プラグイン
     */
    public Plugin getPlugin() {
        return plugin;
    }

    /**
     * 機能がグローバル有効かどうか。
     *
     * @return 有効なら true
     */
    public boolean isEnabled() {
        return toggleModule.getGlobal(TOGGLE_KEY);
    }

    public boolean isDebugEnabled() {
        return OfflineAccessModule.Debug;
    }

    public void debug(String message) {
        if (!isDebugEnabled()) {
            return;
        }
        plugin.getLogger().info("[OfflineAccess Debug] " + message);
        if (plugin instanceof Loader loader && loader.getOfflineAccessModule() != null) {
            loader.getOfflineAccessModule().appendDebug(message);
        }
    }

    public void debug(String message, Throwable throwable) {
        if (!isDebugEnabled()) {
            return;
        }
        plugin.getLogger().log(Level.WARNING, "[OfflineAccess Debug] " + message, throwable);
        if (plugin instanceof Loader loader && loader.getOfflineAccessModule() != null) {
            loader.getOfflineAccessModule().appendDebug(message, throwable);
        }
    }

    /**
     * 指定プレイヤー名が許可されているか。
     * 機能が無効な場合は常に false。
     *
     * @param name プレイヤー名
     * @return 許可されている場合 true
     */
    public boolean isAllowed(String name) {
        if (!isEnabled() || plugin instanceof Loader loader
                && loader.getOfflineAccessModule() != null
                && loader.getOfflineAccessModule().isInjectionFailed()) {
            debug("isAllowed=false name=" + name + " enabled=" + isEnabled() + " injectionFailed="
                    + (plugin instanceof Loader l && l.getOfflineAccessModule() != null && l.getOfflineAccessModule().isInjectionFailed()));
            return false;
        }
        boolean allowed = store.contains(name);
        debug("isAllowed=" + allowed + " name=" + name);
        return allowed;
    }

    /**
     * 許可リストに追加する。
     *
     * @param name プレイヤー名
     * @return 追加された場合 true
     */
    public boolean add(String name) {
        return store.add(name);
    }

    /**
     * 許可リストから削除する。
     *
     * @param name プレイヤー名
     * @return 削除された場合 true
     */
    public boolean remove(String name) {
        return store.remove(name);
    }

    /**
     * 許可されているプレイヤー名の一覧を返す。
     *
     * @return プレイヤー名セット
     */
    public Set<String> getAll() {
        return store.getAll();
    }
}

