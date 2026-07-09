package org.pexserver.koukunn.bettersurvival.Modules.Feature.Performance;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.pexserver.koukunn.bettersurvival.Core.Config.JsonUtils;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap.WebMapModule;

import java.io.File;
import java.io.IOException;

/**
 * 省電力モード。有効 (true) の場合、サーバーに誰もいない間は無駄な更新スレッドを停止し、
 * 消費電力をほぼアイドルまで落とすことを目指す。プレイヤーが接続すると自動的に復帰する。
 * <p>
 * デフォルトは無効 (false)。状態は {@code PEXConfig/performance.json} に保存される。
 * </p>
 */
public class PerformanceModule implements Listener {
    private final Loader plugin;
    private final File stateFile;

    private volatile boolean enabled;
    private volatile boolean powerSaving;

    public PerformanceModule(Loader plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getConfigManager().getBaseDir(), "performance.json");
        load();
        // 起動直後の在室状況を反映する (無人かつ有効ならすぐ省電力へ)。
        plugin.getServer().getScheduler().runTask(plugin, this::recompute);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isPowerSaving() {
        return powerSaving;
    }

    /** 省電力モードの有効/無効を切り替え、状態を保存して即時反映する。 */
    public void setEnabled(boolean value) {
        this.enabled = value;
        save();
        recompute();
    }

    /** 現在のオンライン人数と設定から、省電力へ入る/出るを判定して反映する。 */
    private void recompute() {
        boolean shouldSave = enabled && plugin.getServer().getOnlinePlayers().isEmpty();
        if (shouldSave == powerSaving) {
            return;
        }
        powerSaving = shouldSave;
        if (shouldSave) {
            enterPowerSave();
        } else {
            exitPowerSave();
        }
    }

    private void enterPowerSave() {
        plugin.getLogger().info("[Performance] 省電力モードに入ります (無人): 無駄な更新スレッドを停止します");
        WebMapModule webMap = plugin.getWebMapModule();
        if (webMap != null) {
            webMap.suspendBackgroundWork();
        }
    }

    private void exitPowerSave() {
        plugin.getLogger().info("[Performance] 省電力モードを解除します: 更新スレッドを再開します");
        WebMapModule webMap = plugin.getWebMapModule();
        if (webMap != null) {
            webMap.resumeBackgroundWork();
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        recompute();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, this::recompute);
    }

    private void load() {
        if (!stateFile.isFile()) {
            return;
        }
        try {
            State state = JsonUtils.fromJson(stateFile, State.class);
            if (state != null) {
                this.enabled = state.enabled;
            }
        } catch (IOException error) {
            plugin.getLogger().warning("[Performance] performance.json の読み込みに失敗しました: " + error.getMessage());
        }
    }

    private void save() {
        State state = new State();
        state.enabled = enabled;
        try {
            JsonUtils.toJson(stateFile, state);
        } catch (IOException error) {
            plugin.getLogger().warning("[Performance] performance.json の保存に失敗しました: " + error.getMessage());
        }
    }

    /** 保存用の状態。 */
    public static class State {
        public boolean enabled = false;
    }
}
