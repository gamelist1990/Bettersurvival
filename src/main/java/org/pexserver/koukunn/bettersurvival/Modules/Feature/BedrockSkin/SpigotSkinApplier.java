package org.pexserver.koukunn.bettersurvival.Modules.Feature.BedrockSkin;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerTextures;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Paper API で PlayerTextures を使用してスキンを適用するクラス
 * 
 * 処理フロー:
 * 1. Base64 encoded textures から スキンURL を抽出
 * 2. PlayerTextures.setSkin(URL) でスキンを設定
 * 3. player.setPlayerProfile() で適用
 * 4. 全プレイヤーに対して hidePlayer/showPlayer で更新を強制
 */
public class SpigotSkinApplier {

    private static final Logger LOGGER = Bukkit.getLogger();
    private final Plugin plugin;
    
    // スリムモデル判定用
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
            applySkinInternal(player, value, signature);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                applySkinInternal(player, value, signature);
            });
        }
    }

    /**
     * スキン適用の内部処理 (PlayerTextures API を使用)
     */
    private void applySkinInternal(Player player, String value, String signature) {
        UUID playerId = player.getUniqueId();
        
        try {
            applyingPlayers.add(playerId);
            
            // 1. Base64 から JSON をデコードしてスキンURL を抽出
            String decodedJson = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            LOGGER.fine("[BedrockSkin] デコード済みJSON: " + decodedJson);
            
            
            // 3. スリムモデルかどうかを判定
            boolean isSlim = SLIM_MODEL_PATTERN.matcher(decodedJson).find();
            PlayerTextures.SkinModel skinModel = isSlim ? 
                PlayerTextures.SkinModel.SLIM : PlayerTextures.SkinModel.CLASSIC;
            

            
            // 4. PlayerProfile を取得して PlayerTextures を使用
            PlayerProfile profile = player.getPlayerProfile();
            PlayerTextures textures = profile.getTextures();
            
            // 5. スキンを設定
            textures.setSkin(null, skinModel);
            
            // 6. プロファイルを適用
            player.setPlayerProfile(profile);

            // 7. 全オンラインプレイヤーに対して更新を強制
            refreshPlayerForAllPlayers(player);

            LOGGER.info("[BedrockSkin] PlayerTextures API でスキンを適用しました: " + player.getName());
            
        } catch (Exception e) {
            LOGGER.warning("[BedrockSkin] PlayerTextures API でのスキン適用に失敗、フォールバック: " + e.getMessage());
            // フォールバック: 従来の ProfileProperty 方式
            applyUsingProfileProperty(player, value, signature);
        } finally {
            // 少し遅延してから適用中フラグを解除
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                applyingPlayers.remove(playerId);
            }, 10L);
        }
    }
    
    /**
     * 従来の ProfileProperty を使用したスキン適用（フォールバック）
     */
    private void applyUsingProfileProperty(Player player, String value, String signature) {
        try {
            PlayerProfile profile = player.getPlayerProfile();
            profile.removeProperty("textures");
            ProfileProperty skinProperty = new ProfileProperty("textures", value, signature);
            profile.setProperty(skinProperty);
            player.setPlayerProfile(profile);
            refreshPlayerForAllPlayers(player);
            LOGGER.info("[BedrockSkin] ProfileProperty でスキンを適用しました: " + player.getName());
        } catch (Exception e) {
            LOGGER.warning("[BedrockSkin] フォールバック適用も失敗: " + e.getMessage());
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
