package org.pexserver.koukunn.bettersurvival.Modules.Feature.BedrockSkin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Bedrock プレイヤーのスキンを Java クライアントに適用するモジュール
 * 
 * 処理フロー:
 * 【毎回参加時】
 * 1. Floodgate で Bedrock プレイヤーを検出
 * 2. Paper API で PlayerProfile からスキンデータを取得
 * 3. キャッシュと比較
 *    - 差分なし: キャッシュからスキンを適用
 *    - 差分あり or 初回: キャッシュを更新、初回の場合は再接続を促すメッセージを表示
 */
public class BedrockSkinModule implements Listener {

    private static final String FEATURE_KEY = "bedrockskin";
    private static final Logger LOGGER = Bukkit.getLogger();

    private final Plugin plugin;
    private final ToggleModule toggleModule;
    private final GeyserSkinRetriever skinRetriever;
    private final SkinDatabase skinDatabase;
    private final SpigotSkinApplier skinApplier;

    public BedrockSkinModule(Plugin plugin, ToggleModule toggleModule, ConfigManager configManager) {
        this.plugin = plugin;
        this.toggleModule = toggleModule;
        this.skinRetriever = new GeyserSkinRetriever();
        this.skinDatabase = new SkinDatabase(configManager);
        this.skinApplier = new SpigotSkinApplier(plugin);
    }

    /**
     * プレイヤー参加時にスキンを取得して比較・適用
     * 毎回参加時にスキンデータを取得し、キャッシュと比較する
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // グローバル設定が無効なら処理しない
        if (!toggleModule.getGlobal(FEATURE_KEY)) {
            return;
        }

        // Floodgate プレイヤーでなければスキップ
        if (!skinRetriever.isFloodgatePlayer(playerId)) {
            return;
        }

        LOGGER.info("[BedrockSkin] Bedrock プレイヤーを検出: " + player.getName());

        // 毎回スキンデータを取得してキャッシュと比較
        scheduleSkinCheck(player, 1);
    }

    /**
     * スキンチェックをスケジュール
     * スキンデータを取得してキャッシュと比較
     */
    private void scheduleSkinCheck(Player player, int attempt) {
        int maxAttempts = 15; // 最大15回試行 (約30秒)
        long delay = 40L + (attempt * 20L); // 2秒 + 試行回数 * 1秒

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }

            SkinCheckResult result = checkAndUpdateSkin(player);

            switch (result) {
                case FIRST_TIME_UPDATED:
                    // 初回参加でスキンを保存した → 再接続を促すメッセージを表示
                    LOGGER.info("[BedrockSkin] 初回参加、再接続メッセージを表示: " + player.getName());
                    player.sendMessage(Component.text()
                            .append(Component.text("スキンデータを取得しました！", NamedTextColor.GREEN))
                            .appendNewline()
                            .append(Component.text("再接続すると他のプレイヤーにもスキンが表示されます。", NamedTextColor.YELLOW))
                            .build());
                    break;
                case SKIN_UPDATED:
                    // スキンが変更された → キャッシュ更新済み、次回接続で反映
                    LOGGER.info("[BedrockSkin] スキン変更を検出、キャッシュを更新: " + player.getName());
                    player.sendMessage(Component.text()
                            .append(Component.text("スキンの変更を検出しました。", NamedTextColor.GREEN))
                            .appendNewline()
                            .append(Component.text("再接続すると新しいスキンが他のプレイヤーにも表示されます。", NamedTextColor.YELLOW))
                            .build());
                    break;
                case SKIN_UNCHANGED:
                    // スキンに変更なし → キャッシュからそのまま適用
                    LOGGER.info("[BedrockSkin] スキン変更なし、キャッシュから適用: " + player.getName());
                    Optional<SkinEntry> cached = skinDatabase.getLatestSkin(player.getUniqueId());
                    if (cached.isPresent()) {
                        SkinEntry entry = cached.get();
                        applySkinWithDelay(player, entry.getValue(), entry.getSignature());
                    }
                    break;
                case SKIN_NOT_READY:
                    // スキンがまだ取得できない → 再試行
                    if (attempt < maxAttempts) {
                        if (attempt % 3 == 0) { // 3回ごとにログ
                            LOGGER.info("[BedrockSkin] スキン取得待機中... (試行 " + attempt + "/" + maxAttempts + "): " + player.getName());
                        }
                        scheduleSkinCheck(player, attempt + 1);
                    } else {
                        LOGGER.warning("[BedrockSkin] スキン取得タイムアウト: " + player.getName());
                    }
                    break;
            }
        }, delay);
    }

    /**
     * スキンチェックの結果
     */
    private enum SkinCheckResult {
        FIRST_TIME_UPDATED, // 初回参加でスキンを保存した
        SKIN_UPDATED,       // スキンが変更された
        SKIN_UNCHANGED,     // スキンに変更なし
        SKIN_NOT_READY      // スキンがまだ取得できない
    }

    /**
     * PlayerProfile からスキンを取得してキャッシュと比較
     * 差分があればキャッシュを更新
     */
    private SkinCheckResult checkAndUpdateSkin(Player player) {
        UUID playerId = player.getUniqueId();
        String playerName = player.getName();

        try {
            // Paper API を使用してプレイヤーのプロファイルからスキンを取得
            com.destroystokyo.paper.profile.PlayerProfile profile = player.getPlayerProfile();
            
            for (com.destroystokyo.paper.profile.ProfileProperty property : profile.getProperties()) {
                if ("textures".equals(property.getName())) {
                    String value = property.getValue();
                    String signature = property.getSignature();
                    
                    if (value != null && !value.isEmpty() && signature != null && !signature.isEmpty()) {
                        LOGGER.info("[BedrockSkin] PlayerProfile からスキン取得成功: " + playerName);
                        
                        // キャッシュと比較
                        Optional<SkinEntry> cached = skinDatabase.getLatestSkin(playerId);
                        
                        if (cached.isPresent()) {
                            SkinEntry cachedEntry = cached.get();
                            // value を比較して差分チェック
                            if (value.equals(cachedEntry.getValue())) {
                                return SkinCheckResult.SKIN_UNCHANGED;
                            } else {
                                // スキンが変更された → キャッシュを更新
                                SkinEntry entry = new SkinEntry(value, signature, null, null, System.currentTimeMillis());
                                skinDatabase.saveSkinEntry(playerId, entry);
                                return SkinCheckResult.SKIN_UPDATED;
                            }
                        } else {
                            // 初回参加 → キャッシュに保存
                            SkinEntry entry = new SkinEntry(value, signature, null, null, System.currentTimeMillis());
                            skinDatabase.saveSkinEntry(playerId, entry);
                            return SkinCheckResult.FIRST_TIME_UPDATED;
                        }
                    }
                }
            }

            return SkinCheckResult.SKIN_NOT_READY;

        } catch (Exception e) {
            LOGGER.warning("[BedrockSkin] スキン取得中にエラー: " + e.getMessage());
            e.printStackTrace();
            return SkinCheckResult.SKIN_NOT_READY;
        }
    }

    /**
     * スキンを適用（遅延付き）
     */
    private void applySkinWithDelay(Player player, String value, String signature) {
        // 複数回適用で確実にスキンを反映
        int[] delays = {20, 60, 100}; // 1秒、3秒、5秒後
        
        for (int i = 0; i < delays.length; i++) {
            int delay = delays[i];
            final int attempt = i + 1;
            
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    LOGGER.info("[BedrockSkin] スキン適用 (試行" + attempt + "): " + player.getName());
                    skinApplier.applySkin(player.getUniqueId(), value, signature);
                }
            }, delay);
        }
    }

    /**
     * プレイヤーのスキンキャッシュをクリア
     */
    public void clearPlayerCache(UUID playerId) {
        skinDatabase.clearPlayerCache(playerId);
    }

    /**
     * 全キャッシュをクリア
     */
    public void clearAllCache() {
        skinDatabase.clearCache();
    }
}
