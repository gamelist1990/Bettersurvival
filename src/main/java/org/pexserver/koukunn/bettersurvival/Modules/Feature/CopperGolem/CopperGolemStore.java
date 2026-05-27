package org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("unchecked")
public class CopperGolemStore {

    private static final String CONFIG_PATH = "CopperGolem/golems.json";

    private final ConfigManager configManager;

    public CopperGolemStore(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public Map<String, StoredGolem> loadAll() {
        Map<String, StoredGolem> result = new LinkedHashMap<>();
        PEXConfig config = configManager.loadConfig(CONFIG_PATH).orElseGet(PEXConfig::new);
        for (Map.Entry<String, Object> entry : config.getData().entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> raw)) {
                continue;
            }
            result.put(entry.getKey(), StoredGolem.deserialize(entry.getKey(), (Map<String, Object>) raw));
        }
        return result;
    }

    public boolean saveAll(Map<String, StoredGolem> data) {
        PEXConfig config = new PEXConfig();
        for (Map.Entry<String, StoredGolem> entry : data.entrySet()) {
            config.put(entry.getKey(), entry.getValue().serialize());
        }
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
            return new Location(world,
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public static class StoredGolem {
        private final String id;
        private final String ownerUuid;
        private final String entityUuid;
        private final int level;
        private final int progress;
        private final int availablePoints;
        private final int harvestPoints;
        private final int rangePoints;
        private final int replantPoints;
        private final int boneMealPoints;
        private final int combatHealthPoints;
        private final int moveSpeedPoints;
        private final int range;
        private final boolean autoReplant;
        private final boolean autoBoneMeal;
        private final String cropRouteMode;
        private final String mode;
        private final List<List<String>> targetContainers;
        private final List<List<String>> boneMealSourceContainers;
        private final List<String> cropFilters;
        private final ItemStack combatMainHand;
        private final ItemStack combatOffHand;
        private final ItemStack combatHelmet;
        private final ItemStack combatChestplate;
        private final ItemStack combatLeggings;
        private final ItemStack combatBoots;

        public StoredGolem(
                String id,
                String ownerUuid,
                String entityUuid,
                int level,
                int progress,
                int availablePoints,
                int harvestPoints,
                int rangePoints,
                int replantPoints,
                int boneMealPoints,
                int combatHealthPoints,
                int moveSpeedPoints,
                int range,
                boolean autoReplant,
                boolean autoBoneMeal,
                String cropRouteMode,
                String mode,
                List<List<String>> targetContainers,
                List<List<String>> boneMealSourceContainers,
                List<String> cropFilters,
                ItemStack combatMainHand,
                ItemStack combatOffHand,
                ItemStack combatHelmet,
                ItemStack combatChestplate,
                ItemStack combatLeggings,
                ItemStack combatBoots) {
            this.id = id;
            this.ownerUuid = ownerUuid;
            this.entityUuid = entityUuid;
            this.level = level;
            this.progress = progress;
            this.availablePoints = availablePoints;
            this.harvestPoints = harvestPoints;
            this.rangePoints = rangePoints;
            this.replantPoints = replantPoints;
            this.boneMealPoints = boneMealPoints;
            this.combatHealthPoints = combatHealthPoints;
            this.moveSpeedPoints = moveSpeedPoints;
            this.range = range;
            this.autoReplant = autoReplant;
            this.autoBoneMeal = autoBoneMeal;
            this.cropRouteMode = cropRouteMode;
            this.mode = mode;
            this.targetContainers = targetContainers;
            this.boneMealSourceContainers = boneMealSourceContainers;
            this.cropFilters = cropFilters;
            this.combatMainHand = cloneSingle(combatMainHand);
            this.combatOffHand = cloneSingle(combatOffHand);
            this.combatHelmet = cloneSingle(combatHelmet);
            this.combatChestplate = cloneSingle(combatChestplate);
            this.combatLeggings = cloneSingle(combatLeggings);
            this.combatBoots = cloneSingle(combatBoots);
        }

        public String getId() {
            return id;
        }

        public String getOwnerUuid() {
            return ownerUuid;
        }

        public String getEntityUuid() {
            return entityUuid;
        }

        public int getLevel() {
            return level;
        }

        public int getProgress() {
            return progress;
        }

        public int getAvailablePoints() {
            return availablePoints;
        }

        public int getHarvestPoints() {
            return harvestPoints;
        }

        public int getRangePoints() {
            return rangePoints;
        }

        public int getReplantPoints() {
            return replantPoints;
        }

        public int getBoneMealPoints() {
            return boneMealPoints;
        }

        public int getCombatHealthPoints() {
            return combatHealthPoints;
        }

        public int getMoveSpeedPoints() {
            return moveSpeedPoints;
        }

        public int getRange() {
            return range;
        }

        public boolean isAutoReplant() {
            return autoReplant;
        }

        public boolean isAutoBoneMeal() {
            return autoBoneMeal;
        }

        public String getCropRouteMode() {
            return cropRouteMode;
        }

        public String getMode() {
            return mode;
        }

        public List<List<String>> getTargetContainers() {
            return targetContainers;
        }

        public List<List<String>> getBoneMealSourceContainers() {
            return boneMealSourceContainers;
        }

        public List<String> getCropFilters() {
            return cropFilters;
        }

        public ItemStack getCombatMainHand() {
            return cloneSingle(combatMainHand);
        }

        public ItemStack getCombatOffHand() {
            return cloneSingle(combatOffHand);
        }

        public ItemStack getCombatHelmet() {
            return cloneSingle(combatHelmet);
        }

        public ItemStack getCombatChestplate() {
            return cloneSingle(combatChestplate);
        }

        public ItemStack getCombatLeggings() {
            return cloneSingle(combatLeggings);
        }

        public ItemStack getCombatBoots() {
            return cloneSingle(combatBoots);
        }

        public Map<String, Object> serialize() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ownerUuid", ownerUuid);
            data.put("entityUuid", entityUuid);
            data.put("level", level);
            data.put("progress", progress);
            data.put("availablePoints", availablePoints);
            data.put("harvestPoints", harvestPoints);
            data.put("rangePoints", rangePoints);
            data.put("replantPoints", replantPoints);
            data.put("boneMealPoints", boneMealPoints);
            data.put("combatHealthPoints", combatHealthPoints);
            data.put("moveSpeedPoints", moveSpeedPoints);
            data.put("range", range);
            data.put("autoReplant", autoReplant);
            data.put("autoBoneMeal", autoBoneMeal);
            data.put("cropRouteMode", cropRouteMode);
            data.put("mode", mode);
            data.put("targetContainers", targetContainers);
            data.put("boneMealSourceContainers", boneMealSourceContainers);
            data.put("cropFilters", cropFilters);
            putSerializedItem(data, "combatMainHand", combatMainHand);
            putSerializedItem(data, "combatOffHand", combatOffHand);
            putSerializedItem(data, "combatHelmet", combatHelmet);
            putSerializedItem(data, "combatChestplate", combatChestplate);
            putSerializedItem(data, "combatLeggings", combatLeggings);
            putSerializedItem(data, "combatBoots", combatBoots);
            return data;
        }

        public static StoredGolem deserialize(String id, Map<String, Object> data) {
            String ownerUuid = data.get("ownerUuid") instanceof String rawOwner ? rawOwner : "";
            String entityUuid = data.get("entityUuid") instanceof String rawEntity ? rawEntity : "";
            int level = data.get("level") instanceof Number number ? number.intValue() : 0;
            int progress = data.get("progress") instanceof Number number ? number.intValue() : 0;
            int availablePoints = data.get("availablePoints") instanceof Number number ? number.intValue() : 0;
            int harvestPoints = data.get("harvestPoints") instanceof Number number ? number.intValue() : 0;
            int rangePoints = data.get("rangePoints") instanceof Number number ? number.intValue() : 0;
            int replantPoints = data.get("replantPoints") instanceof Number number ? number.intValue() : 0;
            int boneMealPoints = data.get("boneMealPoints") instanceof Number number ? number.intValue() : 0;
            int combatHealthPoints = data.get("combatHealthPoints") instanceof Number number ? number.intValue() : 0;
            int moveSpeedPoints = data.get("moveSpeedPoints") instanceof Number number ? number.intValue() : 0;
            int range = data.get("range") instanceof Number number ? number.intValue() : 1;
            boolean autoReplant = data.get("autoReplant") instanceof Boolean bool && bool;
            boolean autoBoneMeal = data.get("autoBoneMeal") instanceof Boolean bool && bool;
            String cropRouteMode = data.get("cropRouteMode") instanceof String rawRouteMode ? rawRouteMode : "NEAR_ORIGIN";
            String mode = data.get("mode") instanceof String rawMode ? rawMode : "IDLE";

            List<List<String>> targetContainers = new ArrayList<>();
            if (data.get("targetContainers") instanceof List<?> list) {
                for (Object entry : list) {
                    if (!(entry instanceof List<?> innerList)) {
                        continue;
                    }
                    List<String> keys = new ArrayList<>();
                    for (Object key : innerList) {
                        if (key instanceof String rawKey && !rawKey.isBlank()) {
                            keys.add(rawKey);
                        }
                    }
                    if (!keys.isEmpty()) {
                        targetContainers.add(keys);
                    }
                }
            }
            List<List<String>> boneMealSourceContainers = new ArrayList<>();
            if (data.get("boneMealSourceContainers") instanceof List<?> list) {
                for (Object entry : list) {
                    if (!(entry instanceof List<?> innerList)) {
                        continue;
                    }
                    List<String> keys = new ArrayList<>();
                    for (Object key : innerList) {
                        if (key instanceof String rawKey && !rawKey.isBlank()) {
                            keys.add(rawKey);
                        }
                    }
                    if (!keys.isEmpty()) {
                        boneMealSourceContainers.add(keys);
                    }
                }
            }

            Set<String> filterSet = new LinkedHashSet<>();
            if (data.get("cropFilters") instanceof List<?> list) {
                for (Object entry : list) {
                    if (entry instanceof String raw && !raw.isBlank()) {
                        filterSet.add(raw);
                    }
                }
            }

            return new StoredGolem(
                    id,
                    ownerUuid,
                    entityUuid,
                    level,
                    progress,
                    availablePoints,
                    harvestPoints,
                    rangePoints,
                    replantPoints,
                    boneMealPoints,
                    combatHealthPoints,
                    moveSpeedPoints,
                    range,
                    autoReplant,
                    autoBoneMeal,
                    cropRouteMode,
                    mode,
                    targetContainers,
                    boneMealSourceContainers,
                    new ArrayList<>(filterSet),
                    deserializeItem(data.get("combatMainHand")),
                    deserializeItem(data.get("combatOffHand")),
                    deserializeItem(data.get("combatHelmet")),
                    deserializeItem(data.get("combatChestplate")),
                    deserializeItem(data.get("combatLeggings")),
                    deserializeItem(data.get("combatBoots")));
        }

        private static void putSerializedItem(Map<String, Object> data, String key, ItemStack item) {
            if (data == null || key == null || key.isBlank()) {
                return;
            }
            ItemStack single = cloneSingle(item);
            if (single == null) {
                data.put(key, null);
                return;
            }
            data.put(key, single.serialize());
        }

        private static ItemStack deserializeItem(Object raw) {
            if (!(raw instanceof Map<?, ?> map)) {
                return null;
            }
            Map<String, Object> serialized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    serialized.put(key, entry.getValue());
                }
            }
            if (serialized.isEmpty()) {
                return null;
            }
            return cloneSingle(ItemStack.deserialize(serialized));
        }

        private static ItemStack cloneSingle(ItemStack item) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                return null;
            }
            ItemStack clone = item.clone();
            clone.setAmount(1);
            return clone;
        }
    }
}
