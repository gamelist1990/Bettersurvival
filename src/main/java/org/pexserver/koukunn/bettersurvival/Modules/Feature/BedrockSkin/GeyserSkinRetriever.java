package org.pexserver.koukunn.bettersurvival.Modules.Feature.BedrockSkin;

import org.bukkit.Bukkit;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Floodgate プレイヤーを検出するクラス
 * 
 * 注意: Geyser/Floodgate はスキンのアップロードを自動で行い、
 * SkinApplyEvent 経由で適用します。このクラスはプレイヤーの検出のみを担当し、
 * スキンデータの取得は行いません（リフレクション禁止のため）。
 * 
 * Floodgate が自動的にスキンを処理するため、追加のスキン取得処理は不要です。
 */
public class GeyserSkinRetriever {

    private static final Logger LOGGER = Bukkit.getLogger();

    /**
     * Floodgate プレイヤーかどうかを確認
     */
    public boolean isFloodgatePlayer(UUID playerId) {
        try {
            FloodgateApi api = FloodgateApi.getInstance();
            return api != null && api.isFloodgatePlayer(playerId);
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    /**
     * Floodgate プレイヤーの情報を取得
     */
    public Optional<FloodgatePlayer> getFloodgatePlayer(UUID playerId) {
        try {
            FloodgateApi api = FloodgateApi.getInstance();
            if (api == null) {
                return Optional.empty();
            }

            if (!api.isFloodgatePlayer(playerId)) {
                return Optional.empty();
            }

            return Optional.ofNullable(api.getPlayer(playerId));

        } catch (NoClassDefFoundError e) {
            LOGGER.warning("[BedrockSkin] Floodgate がインストールされていません");
            return Optional.empty();
        } catch (Exception e) {
            LOGGER.warning("[BedrockSkin] Floodgate プレイヤー取得中にエラー: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Floodgate API が利用可能かどうかを確認
     */
    public boolean isFloodgateAvailable() {
        try {
            FloodgateApi api = FloodgateApi.getInstance();
            return api != null;
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }
}
