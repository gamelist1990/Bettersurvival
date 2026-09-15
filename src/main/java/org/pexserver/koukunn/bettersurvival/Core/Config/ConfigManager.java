package org.pexserver.koukunn.bettersurvival.Core.Config;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PEXConfig フォルダ配下の JSON ファイルを管理するシンプルなマネージャ
 * - ベースフォルダ: plugin.getDataFolder()/PEXConfig
 * - サブフォルダやネストした JSON を扱える
 */
public class ConfigManager {

    private final File baseDir;
    private final Plugin plugin;
    private final Map<String, Optional<PEXConfig>> cache = new ConcurrentHashMap<>();

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
        this.baseDir = new File(plugin.getDataFolder(), "PEXConfig");
        if (!baseDir.exists()) baseDir.mkdirs();
    }

    public File getBaseDir() {
        return baseDir;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    /**
     * 指定された相対パスにある JSON ファイルを読み込み、PEXConfig にデシリアライズする
     * @param relativePath 例: "sample.json" または "nested/example.json"
     */
    public Optional<PEXConfig> loadConfig(String relativePath) {
        String normalizedPath = normalizePath(relativePath);
        return cache.computeIfAbsent(normalizedPath, this::loadFromDisk);
    }

    public Optional<PEXConfig> reloadConfig(String relativePath) {
        String normalizedPath = normalizePath(relativePath);
        Optional<PEXConfig> loaded = loadFromDisk(normalizedPath);
        cache.put(normalizedPath, loaded);
        return loaded;
    }

    public void invalidate(String relativePath) {
        cache.remove(normalizePath(relativePath));
    }

    public void clearCache() {
        cache.clear();
    }

    private Optional<PEXConfig> loadFromDisk(String normalizedPath) {
        File target = new File(baseDir, normalizedPath);
        if (!target.exists()) return Optional.empty();

        try {
            PEXConfig cfg = JsonUtils.fromJson(target, PEXConfig.class);
            return Optional.ofNullable(cfg);
        } catch (IOException e) {
            plugin.getLogger().warning("PEXConfig 読み込み失敗: " + target.getPath() + " - " + e.getMessage());
            return Optional.empty();
        }
    }

    private String normalizePath(String relativePath) {
        Path normalized = Path.of(relativePath).normalize();
        return normalized.toString();
    }

    /**
     * 指定された相対パスへ PEXConfig をシリアライズして保存する
     */
    public boolean saveConfig(String relativePath, PEXConfig cfg) {
        String normalizedPath = normalizePath(relativePath);
        File target = new File(baseDir, normalizedPath);
        try {
            JsonUtils.toJson(target, cfg);
            cache.put(normalizedPath, Optional.of(cfg));
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("PEXConfig 保存失敗: " + target.getPath() + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * ファイルが存在するか
     */
    public boolean exists(String relativePath) {
        String normalizedPath = normalizePath(relativePath);
        Optional<PEXConfig> cached = cache.get(normalizedPath);
        if (cached != null) return cached.isPresent();
        return loadConfig(normalizedPath).isPresent();
    }
}
