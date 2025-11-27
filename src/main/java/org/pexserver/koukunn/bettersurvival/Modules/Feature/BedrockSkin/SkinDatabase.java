package org.pexserver.koukunn.bettersurvival.Modules.Feature.BedrockSkin;

import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤーごとのスキンキャッシュを管理するデータベース
 * - JSON ファイルへの永続化
 * - メモリ内キャッシュ
 * - 1 プレイヤー 1 エントリ（上書き方式）
 */
public class SkinDatabase {

    private static final String CONFIG_PATH = "BedrockSkin/skins.json";

    private final ConfigManager configManager;
    private final Map<UUID, SkinEntry> memoryCache = new ConcurrentHashMap<>();

    public SkinDatabase(ConfigManager configManager) {
        this.configManager = configManager;
        loadFromDisk();
    }

    /**
     * プレイヤーのスキンエントリを取得
     */
    public Optional<SkinEntry> getLatestSkin(UUID playerId) {
        SkinEntry entry = memoryCache.get(playerId);
        if (entry != null && entry.isValid()) {
            return Optional.of(entry);
        }
        return Optional.empty();
    }

    /**
     * プレイヤーのスキンエントリを保存（上書き）
     */
    public void saveSkinEntry(UUID playerId, SkinEntry entry) {
        memoryCache.put(playerId, entry);
        saveToDisk();
    }

    /**
     * ディスクから読み込み
     */
    @SuppressWarnings("unchecked")
    private void loadFromDisk() {
        Optional<PEXConfig> cfgOpt = configManager.loadConfig(CONFIG_PATH);
        if (!cfgOpt.isPresent()) return;

        PEXConfig cfg = cfgOpt.get();
        Map<String, Object> rawData = cfg.getData();
        
        // PEXConfig は "data" キーでラップされている場合がある
        Map<String, Object> data = rawData;
        if (rawData.containsKey("data") && rawData.get("data") instanceof Map) {
            data = (Map<String, Object>) rawData.get("data");
        }

        for (Map.Entry<String, Object> e : data.entrySet()) {
            // "data" キー自体はスキップ
            if ("data".equals(e.getKey())) continue;
            
            try {
                UUID playerId = UUID.fromString(e.getKey());
                
                // 既存のリスト形式に対応（最新のエントリのみ使用）
                if (e.getValue() instanceof List) {
                    List<Map<String, Object>> entryList = (List<Map<String, Object>>) e.getValue();
                    if (!entryList.isEmpty()) {
                        // 最新のエントリを取得
                        Map<String, Object> entryMap = entryList.get(entryList.size() - 1);
                        String value = (String) entryMap.get("value");
                        String signature = (String) entryMap.get("signature");
                        long uploadedAt = ((Number) entryMap.get("uploadedAt")).longValue();
                        memoryCache.put(playerId, new SkinEntry(value, signature, uploadedAt));
                    }
                } else if (e.getValue() instanceof Map) {
                    // 新形式（単一エントリ）
                    Map<String, Object> entryMap = (Map<String, Object>) e.getValue();
                    String value = (String) entryMap.get("value");
                    String signature = (String) entryMap.get("signature");
                    long uploadedAt = ((Number) entryMap.get("uploadedAt")).longValue();
                    memoryCache.put(playerId, new SkinEntry(value, signature, uploadedAt));
                }
            } catch (Exception ex) {
                // 破損したエントリはスキップ
            }
        }
    }

    /**
     * ディスクへ保存
     */
    private void saveToDisk() {
        PEXConfig cfg = new PEXConfig();

        for (Map.Entry<UUID, SkinEntry> e : memoryCache.entrySet()) {
            Map<String, Object> entryMap = new LinkedHashMap<>();
            entryMap.put("value", e.getValue().getValue());
            entryMap.put("signature", e.getValue().getSignature());
            entryMap.put("uploadedAt", e.getValue().getUploadedAt());
            cfg.put(e.getKey().toString(), entryMap);
        }

        configManager.saveConfig(CONFIG_PATH, cfg);
    }

    /**
     * メモリキャッシュをクリア (必要に応じて)
     */
    public void clearCache() {
        memoryCache.clear();
    }

    /**
     * プレイヤーのキャッシュをクリア
     */
    public void clearPlayerCache(UUID playerId) {
        memoryCache.remove(playerId);
        saveToDisk();
    }
}
