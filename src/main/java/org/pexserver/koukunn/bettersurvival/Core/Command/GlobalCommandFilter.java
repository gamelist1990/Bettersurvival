package org.pexserver.koukunn.bettersurvival.Core.Command;

import org.bukkit.Bukkit;
import org.bukkit.help.HelpTopic;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * すべてのプラグインコマンドに対して、権限フィルタリングを適用する
 * 別プラグインのコマンドにも干渉し、権限がないユーザーには非表示にする
 * Lumplus方式の実装
 */
public class GlobalCommandFilter implements Listener {

    private final Plugin plugin;

    public GlobalCommandFilter(Plugin plugin, CommandBlockManager blockManager) {
        this.plugin = plugin;
    }

    /**
     * すべてのプラグインコマンドをラップして権限フィルタリングを適用
     * これを呼び出すことで、別プラグインのコマンドにも干渉する
     * HelpMap から実際に登録されているコマンドを取得
     */
    public void applyGlobalFilter() {
        // 初回試行：HelpMap がまだ初期化されていない可能性があるため、100 ms 後にリトライ
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            applyGlobalFilterInternal(0);
        }, 2L); // 2 ticks = 100 ms
    }

    /**
     * applyGlobalFilter の実装
     * @param retryCount リトライ回数
     */
    private void applyGlobalFilterInternal(int retryCount) {
        try {
            Collection<HelpTopic> helpTopics = Bukkit.getHelpMap().getHelpTopics();
            plugin.getLogger().info("[applyGlobalFilter] HelpMap has " + helpTopics.size() + " topics (retry=" + retryCount + ")");
            
            // HelpMap がまだ初期化されていない場合、リトライ
            if (helpTopics.isEmpty() && retryCount < 3) {
                plugin.getLogger().info("[applyGlobalFilter] HelpMap is empty, retrying in 1 second...");
                plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                    applyGlobalFilterInternal(retryCount + 1);
                }, 20L); // 20 ticks = 1 second
                return;
            }

            if (helpTopics.isEmpty()) {
                plugin.getLogger().warning("[applyGlobalFilter] HelpMap is still empty after retries");
                return;
            }

            // 注意：実行ブロックは CommandBlockerListener で処理される
            plugin.getLogger().info("グローバルコマンドフィルタをリスナーモードで有効にしました");
        } catch (Exception e) {
            plugin.getLogger().warning("グローバルコマンドフィルタの適用に失敗しました: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * TabComplete イベント - 現在は何もしない
     * 表示フィルタリングは applyGlobalFilter で行わない（CommandBlockerListener で実行ブロックで十分）
     */
    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        // フィルタリング不要 - CommandBlockerListener で実行がブロックされるため
    }
}
