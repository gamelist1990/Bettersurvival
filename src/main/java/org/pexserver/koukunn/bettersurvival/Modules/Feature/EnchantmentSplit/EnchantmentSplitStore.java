package org.pexserver.koukunn.bettersurvival.Modules.Feature.EnchantmentSplit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class EnchantmentSplitStore {

    private static final String CONFIG_PATH = "EnchantmentSplit/grindstones.json";

    private final ConfigManager configManager;

    public EnchantmentSplitStore(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public Set<Location> loadAll() {
        Set<Location> locations = new LinkedHashSet<>();
        PEXConfig config = configManager.loadConfig(CONFIG_PATH).orElseGet(PEXConfig::new);
        for (Map.Entry<String, Object> entry : config.getData().entrySet()) {
            Location location = fromKey(entry.getKey());
            if (location != null)
                locations.add(location);
        }
        return locations;
    }

    public boolean save(Location location) {
        PEXConfig config = configManager.loadConfig(CONFIG_PATH).orElseGet(PEXConfig::new);
        config.put(toKey(location), true);
        return configManager.saveConfig(CONFIG_PATH, config);
    }

    public boolean remove(Location location) {
        PEXConfig config = configManager.loadConfig(CONFIG_PATH).orElseGet(PEXConfig::new);
        config.getData().remove(toKey(location));
        return configManager.saveConfig(CONFIG_PATH, config);
    }

    public static String toKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    public static Location fromKey(String key) {
        String[] parts = key.split(":");
        if (parts.length != 4)
            return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null)
            return null;
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
