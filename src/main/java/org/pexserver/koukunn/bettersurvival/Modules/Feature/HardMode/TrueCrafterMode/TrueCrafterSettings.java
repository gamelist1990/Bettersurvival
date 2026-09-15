package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.TrueCrafterMode;

import org.bukkit.configuration.file.YamlConfiguration;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.io.File;
import java.io.IOException;

/** TrueCrafterModeの永続設定を管理する。 */
public final class TrueCrafterSettings {
    private final Loader plugin;
    private final File file;
    private final YamlConfiguration config;

    public TrueCrafterSettings(Loader plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "hardmode.yml");
        config = YamlConfiguration.loadConfiguration(file);
        config.addDefault("truecrafter", false);
        config.addDefault("heat-level", 1);
        config.options().copyDefaults(true);
        save();
    }

    public boolean enabled() {
        return config.getBoolean("truecrafter", false);
    }

    public void enabled(boolean enabled) {
        config.set("truecrafter", enabled);
        save();
    }

    public int heatLevel() {
        return Math.max(1, Math.min(5, config.getInt("heat-level", 1)));
    }

    public void heatLevel(int level) {
        config.set("heat-level", Math.max(1, Math.min(5, level)));
        save();
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("hardmode.ymlを保存できません: " + exception.getMessage());
        }
    }
}
