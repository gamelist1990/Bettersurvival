package org.pexserver.koukunn.bettersurvival.Core.Config;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

/**
 * PEXConfig フォルダ配下の JSON ファイルを管理するシンプルなマネージャ
 * - ベースフォルダ: plugin.getDataFolder()/PEXConfig
 * - サブフォルダやネストした JSON を扱える
 */
public class ConfigManager {

    private final File baseDir;
    private final Plugin plugin;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
        this.baseDir = new File(plugin.getDataFolder(), "PEXConfig");
        if (!baseDir.exists()) baseDir.mkdirs();
    }

    public File getBaseDir() {
        return baseDir;
    }

    /**
     * 指定された相対パスにある JSON ファイルを読み込み、PEXConfig にデシリアライズする
     * @param relativePath 例: "sample.json" または "nested/example.json"
     */
    public Optional<PEXConfig> loadConfig(String relativePath) {
        File target = new File(baseDir, relativePath);
        if (!target.exists()) return Optional.empty();

        try {
            PEXConfig cfg = JsonUtils.fromJson(target, PEXConfig.class);
            return Optional.ofNullable(cfg);
        } catch (IOException e) {
            plugin.getLogger().warning("PEXConfig 読み込み失敗: " + target.getPath() + " - " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 指定された相対パスへ PEXConfig をシリアライズして保存する
     */
    public boolean saveConfig(String relativePath, PEXConfig cfg) {
        File target = new File(baseDir, relativePath);
        try {
            JsonUtils.toJson(target, cfg);
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
        return new File(baseDir, relativePath).exists();
    }
}
