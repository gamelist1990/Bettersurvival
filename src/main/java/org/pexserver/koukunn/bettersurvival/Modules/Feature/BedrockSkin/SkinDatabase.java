package org.pexserver.koukunn.bettersurvival.Modules.Feature.BedrockSkin;

import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤーごとのスキンキャッシュを管理するデータベース
 * - JSON ファイルへの永続化
 * - メモリ内キャッシュ
 * - 最新 5 件のスキンエントリを保持
 */
public class SkinDatabase {

    private static final String CONFIG_PATH = "BedrockSkin/skins.json";
    private static final int MAX_ENTRIES_PER_PLAYER = 5;

    private final ConfigManager configManager;
    private final Map<UUID, List<SkinEntry>> memoryCache = new ConcurrentHashMap<>();

    public SkinDatabase(ConfigManager configManager) {
        this.configManager = configManager;
        loadFromDisk();
    }

    /**
     * プレイヤーの最新スキンエントリを取得
     */
    public Optional<SkinEntry> getLatestSkin(UUID playerId) {
        List<SkinEntry> entries = memoryCache.get(playerId);
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }
        // 最新で有効なエントリを探す
        for (int i = entries.size() - 1; i >= 0; i--) {
            SkinEntry entry = entries.get(i);
            if (entry.isValid()) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /**
     * プレイヤーのスキンエントリを保存
     */
    public void saveSkinEntry(UUID playerId, SkinEntry entry) {
        List<SkinEntry> entries = memoryCache.computeIfAbsent(playerId, k -> new ArrayList<>());
        entries.add(entry);

        // 最大件数を超えたら古いものを削除
        while (entries.size() > MAX_ENTRIES_PER_PLAYER) {
            entries.remove(0);
        }

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
        Map<String, Object> data = cfg.getData();

        for (Map.Entry<String, Object> e : data.entrySet()) {
            try {
                UUID playerId = UUID.fromString(e.getKey());
                List<Map<String, Object>> entryList = (List<Map<String, Object>>) e.getValue();
                List<SkinEntry> skinEntries = new ArrayList<>();

                for (Map<String, Object> entryMap : entryList) {
                    String value = (String) entryMap.get("value");
                    String signature = (String) entryMap.get("signature");
                    String skinUrl = (String) entryMap.get("skinUrl");
                    String model = (String) entryMap.get("model");
                    long uploadedAt = ((Number) entryMap.get("uploadedAt")).longValue();

                    skinEntries.add(new SkinEntry(value, signature, skinUrl, model, uploadedAt));
                }

                memoryCache.put(playerId, skinEntries);
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

        for (Map.Entry<UUID, List<SkinEntry>> e : memoryCache.entrySet()) {
            List<Map<String, Object>> entryList = new ArrayList<>();
            for (SkinEntry entry : e.getValue()) {
                Map<String, Object> entryMap = new LinkedHashMap<>();
                entryMap.put("value", entry.getValue());
                entryMap.put("signature", entry.getSignature());
                entryMap.put("skinUrl", entry.getSkinUrl());
                entryMap.put("model", entry.getModel());
                entryMap.put("uploadedAt", entry.getUploadedAt());
                entryList.add(entryMap);
            }
            cfg.put(e.getKey().toString(), entryList);
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
