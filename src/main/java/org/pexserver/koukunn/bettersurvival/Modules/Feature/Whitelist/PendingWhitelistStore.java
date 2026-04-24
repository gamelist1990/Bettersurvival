package org.pexserver.koukunn.bettersurvival.Modules.Feature.Whitelist;

import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PendingWhitelistStore {
    private static final String CONFIG_PATH = "Whitelist/pending_names.json";

    private final ConfigManager configManager;
    private final Map<String, String> pendingNames = new LinkedHashMap<>();

    public PendingWhitelistStore(ConfigManager configManager) {
        this.configManager = configManager;
        loadFromDisk();
    }

    public synchronized boolean add(String name) {
        String normalized = normalize(name);
        String displayName = sanitize(name);
        if (normalized.isEmpty()) {
            return false;
        }
        boolean added = !pendingNames.containsKey(normalized);
        pendingNames.put(normalized, displayName);
        saveToDisk();
        return added;
    }

    public synchronized boolean remove(String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            return false;
        }
        String removed = pendingNames.remove(normalized);
        if (removed == null) {
            return false;
        }
        saveToDisk();
        return true;
    }

    public synchronized boolean contains(String name) {
        return pendingNames.containsKey(normalize(name));
    }

    public synchronized boolean complete(String name) {
        return remove(name);
    }

    public synchronized List<String> getPendingNames() {
        List<String> names = new ArrayList<>(pendingNames.values());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private void loadFromDisk() {
        PEXConfig config = configManager.loadConfig(CONFIG_PATH).orElseGet(PEXConfig::new);
        pendingNames.clear();
        config.getData().entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> {
                    if (entry.getValue() instanceof String displayName) {
                        String normalized = normalize(entry.getKey());
                        if (!normalized.isEmpty()) {
                            pendingNames.put(normalized, sanitize(displayName));
                        }
                    }
                });
    }

    private synchronized void saveToDisk() {
        PEXConfig config = new PEXConfig();
        pendingNames.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> config.put(entry.getKey(), entry.getValue()));
        configManager.saveConfig(CONFIG_PATH, config);
    }

    private String sanitize(String name) {
        return name == null ? "" : name.trim();
    }

    private String normalize(String name) {
        return sanitize(name).toLowerCase(Locale.ROOT);
    }
}
