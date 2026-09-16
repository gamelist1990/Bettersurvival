package org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("unchecked")
public class SharedStorageStore {
    private static final String CONFIG_PATH = "SharedStorage/networks.json";
    private final ConfigManager configManager;

    public SharedStorageStore(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public Map<String, StoredNetwork> loadAll() {
        Map<String, StoredNetwork> networks = new LinkedHashMap<>();
        PEXConfig config = configManager.loadConfig(CONFIG_PATH).orElseGet(PEXConfig::new);
        for (Map.Entry<String, Object> entry : config.getData().entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> raw)) continue;
            StoredNetwork network = StoredNetwork.deserialize(entry.getKey(), (Map<String, Object>) raw);
            if (network != null) networks.put(storageKey(network.getScope(), network.getId()), network);
        }
        return networks;
    }

    public boolean saveAll(Map<String, StoredNetwork> networks) {
        PEXConfig config = new PEXConfig();
        for (StoredNetwork network : networks.values()) {
            config.put(storageKey(network.getScope(), network.getId()), network.serialize());
        }
        return configManager.saveConfig(CONFIG_PATH, config);
    }

    /** JSON top-level key。IDに区切り文字が含まれても衝突しない。 */
    public static String storageKey(String scope, String id) {
        String normalized = normalizeScope(scope) + "\u0000" + (id == null ? "" : id);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(normalized.getBytes(StandardCharsets.UTF_8));
    }

    public static String toKey(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    public static Location fromKey(String key) {
        String[] parts = key.split(":");
        if (parts.length != 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            return new Location(world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String normalizeScope(String scope) {
        return scope == null || scope.isBlank() ? "default" : scope.toLowerCase(Locale.ROOT);
    }

    public static class StoredNetwork {
        private final String scope;
        private final String id;
        private final Location main;
        private final List<Location> subs;
        private final boolean allowSubInsert;
        private final boolean allowSubExtract;
        private final boolean allowSubHopperInsert;
        private final boolean allowSubHopperExtract;
        private final boolean allowMainInsert;
        private final boolean allowMainExtract;
        private final boolean enableTransferParticles;
        private final int subRange;
        private final boolean enableSubFrameFilter;
        private final String subFrameFilterMode;
        private final boolean enableChestPage;

        public StoredNetwork(String scope, String id, Location main, List<Location> subs,
                             boolean allowSubInsert, boolean allowSubExtract,
                             boolean allowSubHopperInsert, boolean allowSubHopperExtract,
                             boolean allowMainInsert, boolean allowMainExtract, boolean enableTransferParticles,
                             int subRange, boolean enableSubFrameFilter, String subFrameFilterMode, boolean enableChestPage) {
            this.scope = normalizeScope(scope);
            this.id = id;
            this.main = main;
            this.subs = subs;
            this.allowSubInsert = allowSubInsert;
            this.allowSubExtract = allowSubExtract;
            this.allowSubHopperInsert = allowSubHopperInsert;
            this.allowSubHopperExtract = allowSubHopperExtract;
            this.allowMainInsert = allowMainInsert;
            this.allowMainExtract = allowMainExtract;
            this.enableTransferParticles = enableTransferParticles;
            this.subRange = subRange;
            this.enableSubFrameFilter = enableSubFrameFilter;
            this.subFrameFilterMode = subFrameFilterMode;
            this.enableChestPage = enableChestPage;
        }

        public String getScope() { return scope; }
        public String getId() { return id; }
        public Location getMain() { return main; }
        public List<Location> getSubs() { return subs; }
        public boolean isAllowSubInsert() { return allowSubInsert; }
        public boolean isAllowSubExtract() { return allowSubExtract; }
        public boolean isAllowSubHopperInsert() { return allowSubHopperInsert; }
        public boolean isAllowSubHopperExtract() { return allowSubHopperExtract; }
        public boolean isAllowMainInsert() { return allowMainInsert; }
        public boolean isAllowMainExtract() { return allowMainExtract; }
        public boolean isEnableTransferParticles() { return enableTransferParticles; }
        public int getSubRange() { return subRange; }
        public boolean isEnableSubFrameFilter() { return enableSubFrameFilter; }
        public String getSubFrameFilterMode() { return subFrameFilterMode; }
        public boolean isEnableChestPage() { return enableChestPage; }

        public Map<String, Object> serialize() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("scope", scope);
            data.put("id", id);
            data.put("main", main == null ? null : toKey(main));
            List<String> subKeys = new ArrayList<>();
            for (Location sub : subs) if (sub != null) subKeys.add(toKey(sub));
            data.put("subs", subKeys);
            data.put("allowSubInsert", allowSubInsert);
            data.put("allowSubExtract", allowSubExtract);
            data.put("allowSubHopperInsert", allowSubHopperInsert);
            data.put("allowSubHopperExtract", allowSubHopperExtract);
            data.put("allowMainInsert", allowMainInsert);
            data.put("allowMainExtract", allowMainExtract);
            data.put("enableTransferParticles", enableTransferParticles);
            data.put("subRange", subRange);
            data.put("enableSubFrameFilter", enableSubFrameFilter);
            data.put("subFrameFilterMode", subFrameFilterMode);
            data.put("enableChestPage", enableChestPage);
            return data;
        }

        public static StoredNetwork deserialize(String legacyKey, Map<String, Object> data) {
            String id = data.get("id") instanceof String storedId ? storedId : legacyKey;
            String scope = data.get("scope") instanceof String storedScope ? storedScope : null;

            Location main = null;
            Object rawMain = data.get("main");
            if (rawMain instanceof String mainKey) main = fromKey(mainKey);
            List<Location> subs = new ArrayList<>();
            Object rawSubs = data.get("subs");
            if (rawSubs instanceof List<?> list) {
                for (Object value : list) {
                    if (!(value instanceof String key)) continue;
                    Location sub = fromKey(key);
                    if (sub != null) subs.add(sub);
                }
            }

            // 旧保存形式にはscopeがない。main/最初のsubからModule側が正しいscopeへ再判定する。
            if (scope == null || scope.isBlank()) scope = "";
            boolean allowSubInsert = data.get("allowSubInsert") instanceof Boolean allowed && allowed;
            boolean allowSubExtract = !(data.get("allowSubExtract") instanceof Boolean allowed && !allowed);
            boolean allowSubHopperInsert = data.get("allowSubHopperInsert") instanceof Boolean allowed && allowed;
            boolean allowSubHopperExtract = !(data.get("allowSubHopperExtract") instanceof Boolean allowed && !allowed);
            boolean allowMainInsert = !(data.get("allowMainInsert") instanceof Boolean allowed && !allowed);
            boolean allowMainExtract = !(data.get("allowMainExtract") instanceof Boolean allowed && !allowed);
            boolean enableTransferParticles = !(data.get("enableTransferParticles") instanceof Boolean allowed && !allowed);
            int subRange = data.get("subRange") instanceof Number number ? number.intValue() : 15;
            boolean enableSubFrameFilter = data.get("enableSubFrameFilter") instanceof Boolean allowed && allowed;
            String subFrameFilterMode = data.get("subFrameFilterMode") instanceof String rawMode ? rawMode : "EXACT";
            boolean enableChestPage = data.get("enableChestPage") instanceof Boolean allowed && allowed;
            return new StoredNetwork(scope, id, main, subs, allowSubInsert, allowSubExtract,
                    allowSubHopperInsert, allowSubHopperExtract, allowMainInsert, allowMainExtract,
                    enableTransferParticles, subRange, enableSubFrameFilter, subFrameFilterMode, enableChestPage);
        }
    }
}
