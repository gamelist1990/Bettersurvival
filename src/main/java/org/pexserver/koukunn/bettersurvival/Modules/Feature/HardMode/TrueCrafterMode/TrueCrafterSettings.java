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

    public boolean enabled(String group) {
        String normalized = normalizeGroup(group);
        if ("default".equals(normalized)) {
            return config.getBoolean("groups.default.enabled",
                    config.getBoolean("truecrafter", false));
        }
        return config.getBoolean("groups." + normalized + ".enabled", false);
    }

    public void enabled(String group, boolean enabled) {
        config.set("groups." + normalizeGroup(group) + ".enabled", enabled);
        save();
    }

    public int heatLevel(String group) {
        String normalized = normalizeGroup(group);
        int fallback = "default".equals(normalized) ? config.getInt("heat-level", 1) : 1;
        return Math.max(1, Math.min(5,
                config.getInt("groups." + normalized + ".heat-level", fallback)));
    }

    public void heatLevel(String group, int level) {
        config.set("groups." + normalizeGroup(group) + ".heat-level",
                Math.max(1, Math.min(5, level)));
        save();
    }

    private String normalizeGroup(String group) {
        return group == null || group.isBlank() || "selection-lobby".equals(group)
                ? "default" : group;
    }

    private void save() {
        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().warning("hardmode.ymlを保存できません: " + exception.getMessage());
        }
    }
}
