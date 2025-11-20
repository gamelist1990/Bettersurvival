package org.pexserver.koukunn.bettersurvival.Modules;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.util.*;

/**
 * ToggleModule は登録された機能を一覧化し、Chest UI から有効/無効を切り替えます。
 */
public class ToggleModule implements Listener {

    public static final String TOGGLE_INVENTORY_TITLE = "Toggle Features";

    private final ConfigManager configManager;

    private final Map<String, ToggleFeature> features = new LinkedHashMap<>();

    public ToggleModule(Loader plugin) {
        this.configManager = plugin.getConfigManager();
    }

    public void registerFeature(ToggleFeature feature) {
        features.put(feature.getKey(), feature);

        // 説明のデフォルトを保存（設定が存在しない場合のみ）
        String descPath = "features/descriptions.json";
        PEXConfig cfg = configManager.loadConfig(descPath).orElseGet(PEXConfig::new);
        if (cfg.get(feature.getKey()) == null) {
            cfg.put(feature.getKey(), feature.getDescription());
            configManager.saveConfig(descPath, cfg);
        }
    }

    public Collection<ToggleFeature> getFeatures() {
        return features.values();
    }

    public List<ToggleFeature> getVisibleFeatures(boolean adminMode) {
        List<ToggleFeature> visible = new ArrayList<>();
        for (ToggleFeature f : features.values()) {
            if (adminMode) {
                visible.add(f);
                continue;
            }

            // 管理者モードでなければ、グローバルで明示的に無効化されているものは隠す
            if (hasGlobal(f.getKey()) && !getGlobal(f.getKey())) {
                continue;
            }

            visible.add(f);
        }
        return visible;
    }

    public String getFeatureDescription(String key) {
        String descPath = "features/descriptions.json";
        PEXConfig cfg = configManager.loadConfig(descPath).orElseGet(PEXConfig::new);
        Object v = cfg.get(key);
        if (v instanceof String) return (String) v;
        ToggleFeature f = features.get(key);
        return f == null ? "" : f.getDescription();
    }

    public void openToggleUI(Player player, boolean adminMode) {
        List<ToggleFeature> list = new ArrayList<>(getVisibleFeatures(adminMode));
        int size = 9 * ((list.size() + 8) / 9);
        if (size == 0) size = 9;
        Inventory inv = Bukkit.createInventory(null, size, adminMode ? TOGGLE_INVENTORY_TITLE + " (OP)" : TOGGLE_INVENTORY_TITLE);

        int slot = 0;
        for (ToggleFeature f : list) {
            ItemStack item = new ItemStack(f.getIcon());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(f.getDisplayName());
                List<String> lore = new ArrayList<>();
                String desc = getFeatureDescription(f.getKey());
                if (desc != null && !desc.isEmpty()) lore.add(desc);
                boolean enabled = isEnabledFor(player.getUniqueId().toString(), f.getKey());
                if (adminMode) {
                    boolean global = getGlobal(f.getKey());
                    lore.add((global ? "§aグローバル: 有効" : "§cグローバル: 無効"));
                } else {
                    lore.add((enabled ? "§a状態: 有効" : "§c状態: 無効"));
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            inv.setItem(slot++, item);
        }

        player.openInventory(inv);
    }

    private String getUserConfigPath(String uuid) {
        return "toggles/users/" + uuid + ".json";
    }

    private String getGlobalConfigPath() {
        return "toggles/global.json";
    }

    public boolean isEnabledFor(String uuid, String featureKey) {
        Optional<PEXConfig> cfg = configManager.loadConfig(getUserConfigPath(uuid));
        if (!cfg.isPresent()) return false;
        Object obj = cfg.get().get(featureKey);
        return obj instanceof Boolean && (Boolean) obj;
    }

    public boolean setEnabledFor(String uuid, String featureKey, boolean value) {
        PEXConfig cfg = configManager.loadConfig(getUserConfigPath(uuid)).orElseGet(PEXConfig::new);
        cfg.put(featureKey, value);
        return configManager.saveConfig(getUserConfigPath(uuid), cfg);
    }

    public boolean getGlobal(String key) {
        PEXConfig cfg = configManager.loadConfig(getGlobalConfigPath()).orElseGet(PEXConfig::new);
        Object v = cfg.get(key);
        return v instanceof Boolean && (Boolean) v;
    }

    public boolean hasGlobal(String key) {
        PEXConfig cfg = configManager.loadConfig(getGlobalConfigPath()).orElseGet(PEXConfig::new);
        return cfg.get(key) != null;
    }

    public boolean setGlobal(String key, boolean value) {
        PEXConfig cfg = configManager.loadConfig(getGlobalConfigPath()).orElseGet(PEXConfig::new);
        cfg.put(key, value);
        return configManager.saveConfig(getGlobalConfigPath(), cfg);
    }

    public static class ToggleFeature {
        private final String key;
        private final String displayName;
        private final String description;
        private final Material icon;

        public ToggleFeature(String key, String displayName, String description, Material icon) {
            this.key = key;
            this.displayName = displayName;
            this.description = description;
            this.icon = icon;
        }
    public String getDescription() { return description; }

        public String getKey() { return key; }
        public String getDisplayName() { return displayName; }
        public Material getIcon() { return icon; }
    }
}
