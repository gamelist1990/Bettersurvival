package org.pexserver.koukunn.bettersurvival.Modules.Feature.WarpStone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ワープストーンの永続化。
 *
 * <pre>
 * ws:&lt;world:x:y:z&gt;  → { name, owner }
 * seen:&lt;uuid&gt;      → [ locationKey... ]   (プレイヤーが発見済みのワープストーン)
 * gta:&lt;uuid&gt;       → boolean               (GTA アニメーション設定)
 * </pre>
 */
public class WarpStoneStore {

    private static final String CONFIG_PATH = "WarpStone/warpstones.json";

    private final ConfigManager configManager;

    public WarpStoneStore(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public record StoneData(String name, UUID owner) {
    }

    public record LoadResult(Map<String, StoneData> stones, Map<UUID, Set<String>> discovered, Map<UUID, Boolean> gta) {
    }

    public LoadResult loadAll() {
        Map<String, StoneData> stones = new LinkedHashMap<>();
        Map<UUID, Set<String>> discovered = new LinkedHashMap<>();
        Map<UUID, Boolean> gta = new LinkedHashMap<>();
        PEXConfig config = configManager.loadConfig(CONFIG_PATH).orElseGet(PEXConfig::new);
        for (Map.Entry<String, Object> entry : config.getData().entrySet()) {
            String key = entry.getKey();
            try {
                if (key.startsWith("ws:") && entry.getValue() instanceof Map<?, ?> raw) {
                    String name = raw.get("name") instanceof String s ? s : "Waystone";
                    UUID owner = raw.get("owner") instanceof String s ? UUID.fromString(s) : null;
                    if (owner != null) {
                        stones.put(key.substring(3), new StoneData(name, owner));
                    }
                } else if (key.startsWith("seen:") && entry.getValue() instanceof List<?> raw) {
                    UUID uuid = UUID.fromString(key.substring(5));
                    Set<String> keys = new LinkedHashSet<>();
                    for (Object o : raw) {
                        if (o instanceof String s) {
                            keys.add(s);
                        }
                    }
                    discovered.put(uuid, keys);
                } else if (key.startsWith("gta:") && entry.getValue() instanceof Boolean b) {
                    gta.put(UUID.fromString(key.substring(4)), b);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return new LoadResult(stones, discovered, gta);
    }

    public void saveAll(Map<String, StoneData> stones, Map<UUID, Set<String>> discovered, Map<UUID, Boolean> gta) {
        PEXConfig config = new PEXConfig();
        for (Map.Entry<String, StoneData> entry : stones.entrySet()) {
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("name", entry.getValue().name());
            raw.put("owner", entry.getValue().owner().toString());
            config.put("ws:" + entry.getKey(), raw);
        }
        for (Map.Entry<UUID, Set<String>> entry : discovered.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                config.put("seen:" + entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }
        for (Map.Entry<UUID, Boolean> entry : gta.entrySet()) {
            config.put("gta:" + entry.getKey(), entry.getValue());
        }
        configManager.saveConfig(CONFIG_PATH, config);
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
            return new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
