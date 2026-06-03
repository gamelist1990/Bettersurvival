package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChunkLoader;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class ChunkLoaderStore {

    private static final String CONFIG_PATH = "ChunkLoader/chunk_loaders.json";

    private final ConfigManager configManager;

    public ChunkLoaderStore(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public Map<Location, UUID> loadAll() {
        Map<Location, UUID> loaded = new LinkedHashMap<>();
        PEXConfig config = configManager.loadConfig(CONFIG_PATH).orElseGet(PEXConfig::new);
        for (Map.Entry<String, Object> entry : config.getData().entrySet()) {
            if (!(entry.getValue() instanceof String rawOwner)) {
                continue;
            }
            Location location = fromKey(entry.getKey());
            if (location == null) {
                continue;
            }
            try {
                loaded.put(location, UUID.fromString(rawOwner));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return loaded;
    }

    public boolean save(Location location, UUID owner) {
        if (location == null || owner == null || location.getWorld() == null) {
            return false;
        }
        PEXConfig config = configManager.loadConfig(CONFIG_PATH).orElseGet(PEXConfig::new);
        config.put(toKey(location), owner.toString());
        return configManager.saveConfig(CONFIG_PATH, config);
    }

    public boolean remove(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        PEXConfig config = configManager.loadConfig(CONFIG_PATH).orElseGet(PEXConfig::new);
        config.getData().remove(toKey(location));
        return configManager.saveConfig(CONFIG_PATH, config);
    }

    public boolean removeByKey(String locationKey) {
        if (locationKey == null || locationKey.isBlank()) {
            return false;
        }
        PEXConfig config = configManager.loadConfig(CONFIG_PATH).orElseGet(PEXConfig::new);
        config.getData().remove(locationKey);
        return configManager.saveConfig(CONFIG_PATH, config);
    }

    public static String toKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    public static Location fromKey(String key) {
        String[] parts = key.split(":");
        if (parts.length != 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
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
