package org.pexserver.koukunn.bettersurvival.Modules.Feature.BedrockSkin;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerTextures;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * org.bukkit.profile.PlayerTextures を使用してスキンを適用するクラス
 * 
 * 処理フロー:
 * 1. Base64 エンコードされた textures から スキン URL を抽出
 * 2. PlayerTextures.setSkin(URL, SkinModel) でスキンを設定
 * 3. player.setPlayerProfile() で適用
 * 4. 全プレイヤーに対して hidePlayer/showPlayer で更新を強制
 */
public class SpigotSkinApplier {

    private static final Logger LOGGER = Bukkit.getLogger();
    private final Plugin plugin;
    
    // スキン URL 抽出用パターン
    private static final Pattern SKIN_URL_PATTERN = Pattern.compile("\"SKIN\"\\s*:\\s*\\{[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"");
    // スリムモデル判定用パターン
    private static final Pattern SLIM_MODEL_PATTERN = Pattern.compile("\"model\"\\s*:\\s*\"slim\"");
    
    // スキン適用中のプレイヤーを追跡（重複適用防止）
    private final Set<UUID> applyingPlayers = new HashSet<>();

    public SpigotSkinApplier(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * プレイヤーにスキンを適用（メインスレッドで実行）
     */
    public void applySkin(UUID playerId, String value, String signature) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        
        // 既に適用中なら重複実行しない
        if (applyingPlayers.contains(playerId)) {
            LOGGER.fine("[BedrockSkin] スキン適用中のためスキップ: " + player.getName());
            return;
        }

        // メインスレッドで実行
        if (Bukkit.isPrimaryThread()) {
            applySkinInternal(player, value);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                applySkinInternal(player, value);
            });
        }
    }

    /**
     * スキン適用の内部処理 (org.bukkit.profile.PlayerTextures API を使用)
     */
    private void applySkinInternal(Player player, String value) {
        UUID playerId = player.getUniqueId();
        
        try {
            applyingPlayers.add(playerId);
            
            // Base64 から JSON をデコード
            String decodedJson = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            LOGGER.fine("[BedrockSkin] デコード済みJSON: " + decodedJson);
            
            // スキン URL を抽出
            URL skinUrl = null;
            Matcher skinMatcher = SKIN_URL_PATTERN.matcher(decodedJson);
            if (skinMatcher.find()) {
                skinUrl = URI.create(skinMatcher.group(1)).toURL();
                LOGGER.fine("[BedrockSkin] スキンURL: " + skinUrl);
            }
            
            if (skinUrl == null) {
                LOGGER.warning("[BedrockSkin] スキンURLが見つかりません: " + player.getName());
                return;
            }
            
            // スリムモデルかどうかを判定
            boolean isSlim = SLIM_MODEL_PATTERN.matcher(decodedJson).find();
            PlayerTextures.SkinModel skinModel = isSlim ? 
                PlayerTextures.SkinModel.SLIM : PlayerTextures.SkinModel.CLASSIC;
            
            // Paper の PlayerProfile を取得し、org.bukkit.profile.PlayerTextures を使用
            PlayerProfile profile = player.getPlayerProfile();
            PlayerTextures textures = profile.getTextures();
            
            // スキンを設定
            textures.setSkin(skinUrl, skinModel);
            
            // プロファイルを適用
            player.setPlayerProfile(profile);
            
            // 全オンラインプレイヤーに対して更新を強制
            refreshPlayerForAllPlayers(player);

            LOGGER.info("[BedrockSkin] PlayerTextures API でスキンを適用しました: " + player.getName() + 
                    " (モデル: " + skinModel + ")");
            
        } catch (Exception e) {
            LOGGER.warning("[BedrockSkin] スキン適用に失敗: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 少し遅延してから適用中フラグを解除
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                applyingPlayers.remove(playerId);
            }, 10L);
        }
    }

    /**
     * 全オンラインプレイヤーに対してこのプレイヤーを再表示（スキン更新を反映）
     * hidePlayer → 遅延 → showPlayer の順で実行
     */
    private void refreshPlayerForAllPlayers(Player targetPlayer) {
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        Location targetLocation = targetPlayer.getLocation();

        for (Player viewer : onlinePlayers) {
            // 自分自身はスキップ
            if (viewer.equals(targetPlayer)) continue;
            
            // そもそも見えていないプレイヤーはスキップ
            if (!viewer.canSee(targetPlayer)) continue;
            
            // 距離が遠すぎるプレイヤーはスキップ（最適化）
            if (viewer.getWorld() != targetPlayer.getWorld()) continue;
            if (viewer.getLocation().distanceSquared(targetLocation) > 16384) continue; // 128ブロック以上

            // hidePlayer で一度非表示
            viewer.hidePlayer(plugin, targetPlayer);
            
            // 少し遅延してから showPlayer で再表示
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (viewer.isOnline() && targetPlayer.isOnline()) {
                    viewer.showPlayer(plugin, targetPlayer);
                }
            }, 2L);
        }
    }
    
    /**
     * スキン適用中かどうかを確認
     */
    public boolean isApplying(UUID playerId) {
        return applyingPlayers.contains(playerId);
    }
}
