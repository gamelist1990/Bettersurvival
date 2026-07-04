package org.pexserver.koukunn.bettersurvival.Modules.Feature.ParallelFurnace;

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
 * 並列かまどの永続化 (PEXConfig/ParallelFurnace/parallel_furnaces.json)。
 *
 * 焼成中ジョブの素材は保存時に素材ストレージへ合流させるため、
 * 再起動すると焼成進捗だけがリセットされる（アイテムは失われない）。
 */
public class ParallelFurnaceStore {

    private static final String CONFIG_PATH = "ParallelFurnace/parallel_furnaces.json";

    private final ConfigManager configManager;

    public ParallelFurnaceStore(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public Map<String, ParallelFurnaceData> loadAll() {
        Map<String, ParallelFurnaceData> loaded = new LinkedHashMap<>();
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
            ParallelFurnaceData data = new ParallelFurnaceData(owner, location);
            data.setExtraCores(intOf(raw.get("extraCores")));
            data.setFuelBankTicks(longOf(raw.get("fuelBank")));
            data.setFuelBankMaxTicks(longOf(raw.get("fuelMax")));
            String overflow = str(raw.get("overflow"));
            if (overflow != null && !overflow.isBlank()) {
                data.setOverflowChest(fromKey(overflow));
            }
            readItems(str(raw.get("input")), data.input());
            readItems(str(raw.get("fuel")), data.fuel());
            readItems(str(raw.get("output")), data.output());
            loaded.put(entry.getKey(), data);
        }
        return loaded;
    }

    public boolean saveAll(Collection<ParallelFurnaceData> furnaces) {
        PEXConfig config = new PEXConfig();
        for (ParallelFurnaceData data : furnaces) {
            if (data.location().getWorld() == null) {
                continue;
            }
            Map<String, Object> raw = new LinkedHashMap<>();
            raw.put("owner", data.owner().toString());
            raw.put("extraCores", data.getExtraCores());
            raw.put("fuelBank", data.getFuelBankTicks());
            raw.put("fuelMax", data.getFuelBankMaxTicks());
            Location overflow = data.getOverflowChest();
            raw.put("overflow", overflow == null || overflow.getWorld() == null ? "" : toKey(overflow));
            // 焼成中の素材は素材ストレージへ合流させた状態で保存する
            ItemStack[] inputCopy = copyOf(data.input());
            ItemStack[] outputCopy = copyOf(data.output());
            for (ParallelFurnaceData.SmeltJob job : data.jobs()) {
                if (job == null || job.source == null) {
                    continue;
                }
                int leftover = ParallelFurnaceData.addToStorage(inputCopy, job.source.clone());
                if (leftover > 0) {
                    ItemStack rest = job.source.clone();
                    rest.setAmount(leftover);
                    ParallelFurnaceData.addToStorage(outputCopy, rest);
                }
            }
            raw.put("input", writeItems(inputCopy));
            raw.put("fuel", writeItems(data.fuel()));
            raw.put("output", writeItems(outputCopy));
            config.put(toKey(data.location()), raw);
        }
        return configManager.saveConfig(CONFIG_PATH, config);
    }

    private static ItemStack[] copyOf(ItemStack[] storage) {
        ItemStack[] copy = new ItemStack[storage.length];
        for (int i = 0; i < storage.length; i++) {
            copy[i] = storage[i] == null ? null : storage[i].clone();
        }
        return copy;
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

    private static int intOf(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private static long longOf(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
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
