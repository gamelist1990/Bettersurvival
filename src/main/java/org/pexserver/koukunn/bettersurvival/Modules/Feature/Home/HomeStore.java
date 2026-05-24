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
    public static final int DEFAULT_HOME_SLOTS = 1;
    public static final int MAX_HOME_SLOTS = 10;

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
        return saveHome(player, name, location, getUnlockedSlots(player));
    }

    public SaveResult saveHome(Player player, String name, Location location, int maxSlots) {
        String normalized = normalizeName(name);
        PEXConfig config = configManager.loadConfig(path(player)).orElseGet(PEXConfig::new);
        String existingKey = findExistingKey(config, normalized);
        if (existingKey == null && countHomes(config) >= Math.max(DEFAULT_HOME_SLOTS, Math.min(MAX_HOME_SLOTS, maxSlots))) {
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

    public int getUnlockedSlots(Player player) {
        PEXConfig cfg = configManager.loadConfig(slotPath(player)).orElseGet(PEXConfig::new);
        Object raw = cfg.get("slots");
        int slots = raw instanceof Number number ? number.intValue() : DEFAULT_HOME_SLOTS;
        slots = Math.max(DEFAULT_HOME_SLOTS, Math.min(MAX_HOME_SLOTS, slots));
        int homeCount = getHomes(player).size();
        if (homeCount > slots) {
            slots = Math.min(MAX_HOME_SLOTS, homeCount);
            cfg.put("slots", slots);
            configManager.saveConfig(slotPath(player), cfg);
        }
        return slots;
    }

    public boolean setUnlockedSlots(Player player, int slots) {
        int normalized = Math.max(DEFAULT_HOME_SLOTS, Math.min(MAX_HOME_SLOTS, slots));
        PEXConfig cfg = configManager.loadConfig(slotPath(player)).orElseGet(PEXConfig::new);
        cfg.put("slots", normalized);
        return configManager.saveConfig(slotPath(player), cfg);
    }

    private String slotPath(Player player) {
        return "homes/slots/" + player.getUniqueId() + ".json";
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
