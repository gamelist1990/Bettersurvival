package org.pexserver.koukunn.bettersurvival.Modules.Feature.Home;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings("unchecked")
public class HomeStore {
    public static final int MAX_HOMES = 3;

    private final ConfigManager configManager;

    public HomeStore(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public List<HomePoint> getHomes(Player player) {
        PEXConfig config = configManager.loadConfig(path(player)).orElseGet(PEXConfig::new);
        List<HomePoint> homes = new ArrayList<>();
        for (Map.Entry<String, Object> entry : config.getData().entrySet()) {
            if (entry.getValue() instanceof Map) {
                homes.add(HomePoint.deserialize(entry.getKey(), (Map<String, Object>) entry.getValue()));
            }
        }
        homes.sort(Comparator.comparing(home -> home.getName().toLowerCase(Locale.ROOT)));
        return homes;
    }

    public Optional<HomePoint> getHome(Player player, String name) {
        String normalized = normalizeName(name);
        for (HomePoint home : getHomes(player)) {
            if (home.getName().equalsIgnoreCase(normalized)) {
                return Optional.of(home);
            }
        }
        return Optional.empty();
    }

    public SaveResult saveHome(Player player, String name, Location location) {
        String normalized = normalizeName(name);
        PEXConfig config = configManager.loadConfig(path(player)).orElseGet(PEXConfig::new);
        String existingKey = findExistingKey(config, normalized);
        if (existingKey == null && countHomes(config) >= MAX_HOMES) {
            return SaveResult.LIMIT;
        }
        if (existingKey != null && !existingKey.equals(normalized)) {
            config.getData().remove(existingKey);
        }
        config.put(normalized, new HomePoint(normalized, location).serialize());
        return configManager.saveConfig(path(player), config) ? SaveResult.SAVED : SaveResult.FAILED;
    }

    public boolean removeHome(Player player, String name) {
        String normalized = normalizeName(name);
        PEXConfig config = configManager.loadConfig(path(player)).orElseGet(PEXConfig::new);
        String existingKey = findExistingKey(config, normalized);
        if (existingKey == null) return false;
        config.getData().remove(existingKey);
        return configManager.saveConfig(path(player), config);
    }

    public String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    private String findExistingKey(PEXConfig config, String name) {
        for (String key : config.getData().keySet()) {
            if (key.equalsIgnoreCase(name)) {
                return key;
            }
        }
        return null;
    }

    private int countHomes(PEXConfig config) {
        int count = 0;
        for (Object value : config.getData().values()) {
            if (value instanceof LinkedHashMap || value instanceof Map) {
                count++;
            }
        }
        return count;
    }

    private String path(Player player) {
        return "homes/" + player.getUniqueId() + ".json";
    }

    public enum SaveResult {
        SAVED,
        LIMIT,
        FAILED
    }
}
