package org.pexserver.koukunn.bettersurvival.Modules.Feature.Recycler;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * リサイクラーの永続化 (PEXConfig/Recycler/recyclers.json)。
 */
public class RecyclerStore {

    private static final String CONFIG_PATH = "Recycler/recyclers.json";

    private final ConfigManager configManager;

    public RecyclerStore(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public Map<String, RecyclerData> loadAll() {
        Map<String, RecyclerData> loaded = new LinkedHashMap<>();
        PEXConfig config = configManager.loadConfig(CONFIG_PATH).orElseGet(PEXConfig::new);
        for (Map.Entry<String, Object> entry : config.getData().entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> raw)) {
                continue;
            }
            Location location = fromKey(entry.getKey());
            if (location == null) {
                continue;
            }
            UUID owner = parseUuid(str(raw.get("owner")));
            if (owner == null) {
                continue;
            }
            RecyclerData data = new RecyclerData(owner, location);
            data.setRecycledCount(longOf(raw.get("recycled")));
            data.setDestroyedCount(longOf(raw.get("destroyed")));
            data.setXpBank(doubleOf(raw.get("xpBank")));
            readItems(str(raw.get("input")), data.input());
            readItems(str(raw.get("output")), data.output());
            loaded.put(entry.getKey(), data);
        }
        return loaded;
    }

    public boolean saveAll(Collection<RecyclerData> recyclers) {
        PEXConfig config = new PEXConfig();
        for (RecyclerData data : recyclers) {
            if (data.location().getWorld() == null) {
                continue;
            }
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("owner", data.owner().toString());
            raw.put("recycled", data.getRecycledCount());
            raw.put("destroyed", data.getDestroyedCount());
            raw.put("xpBank", data.getXpBank());
            raw.put("input", writeItems(data.input()));
            raw.put("output", writeItems(data.output()));
            config.put(toKey(data.location()), raw);
        }
        return configManager.saveConfig(CONFIG_PATH, config);
    }

    private static String writeItems(ItemStack[] storage) {
        ItemStack[] normalized = new ItemStack[storage.length];
        for (int i = 0; i < storage.length; i++) {
            ItemStack stack = storage[i];
            normalized[i] = stack == null ? new ItemStack(Material.AIR) : stack;
        }
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(normalized));
    }

    private static void readItems(String encoded, ItemStack[] into) {
        if (encoded == null || encoded.isBlank()) {
            return;
        }
        try {
            ItemStack[] loaded = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(encoded));
            for (int i = 0; i < Math.min(into.length, loaded.length); i++) {
                ItemStack stack = loaded[i];
                into[i] = stack == null || stack.getType().isAir() || stack.getAmount() <= 0 ? null : stack;
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static String str(Object value) {
        return value instanceof String s ? s : null;
    }

    private static long longOf(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static double doubleOf(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0D;
    }

    private static UUID parseUuid(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
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
