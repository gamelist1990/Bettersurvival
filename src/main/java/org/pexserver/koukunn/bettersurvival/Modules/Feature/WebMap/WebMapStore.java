package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import org.bukkit.World;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.JsonUtils;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class WebMapStore {
    private static final String SETTINGS_PATH = "WebMap/settings.json";

    private final ConfigManager configManager;

    public WebMapStore(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public WebMapSettings loadSettings() {
        return configManager.loadConfig(SETTINGS_PATH)
                .map(this::toSettings)
                .orElseGet(WebMapSettings::new);
    }

    public boolean saveSettings(WebMapSettings settings) {
        return configManager.saveConfig(SETTINGS_PATH, toConfig(settings));
    }

    public WebMapDimensionData loadDimensionData(String worldKey) {
        File file = dataFile(worldKey);
        if (!file.exists()) {
            WebMapDimensionData data = new WebMapDimensionData();
            data.setWorldKey(worldKey);
            data.setWorldName(worldKey);
            return data;
        }
        try {
            WebMapDimensionData data = JsonUtils.fromJson(file, WebMapDimensionData.class);
            if (data.getWorldKey() == null || data.getWorldKey().isBlank()) {
                data.setWorldKey(worldKey);
            }
            if (data.getWorldName() == null || data.getWorldName().isBlank()) {
                data.setWorldName(worldKey);
            }
            return data;
        } catch (IOException ignored) {
            WebMapDimensionData data = new WebMapDimensionData();
            data.setWorldKey(worldKey);
            data.setWorldName(worldKey);
            return data;
        }
    }

    public void saveDimensionData(WebMapDimensionData data) throws IOException {
        JsonUtils.toJson(dataFile(data.getWorldKey()), data);
    }

    public WebMapDimensionSettings ensureDimensionSettings(WebMapSettings settings, World world) {
        String key = world.getKey().toString();
        WebMapDimensionSettings dimension = settings.getDimensions().get(key);
        if (dimension == null) {
            dimension = new WebMapDimensionSettings();
            dimension.setWorldKey(key);
            dimension.setDisplayName(world.getName());
            dimension.setVisible(isDefaultWorld(world));
            dimension.setAutoTrack(true);
            dimension.setChunkGenEnabled(false);
            settings.getDimensions().put(key, dimension);
        } else {
            if (dimension.getDisplayName() == null || dimension.getDisplayName().isBlank()) {
                dimension.setDisplayName(world.getName());
            }
        }
        return dimension;
    }

    private File dataFile(String worldKey) {
        return new File(configManager.getBaseDir(), "WebMap/" + sanitizeWorldKey(worldKey) + ".json");
    }

    private boolean isDefaultWorld(World world) {
        return switch (world.getEnvironment()) {
            case NORMAL, NETHER, THE_END -> true;
            default -> false;
        };
    }

    private String sanitizeWorldKey(String worldKey) {
        return worldKey.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    private org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig toConfig(WebMapSettings settings) {
        org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig config = new org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig();
        config.put("enabled", settings.isEnabled());
        config.put("paused", settings.isPaused());
        config.put("port", settings.getPort());
        config.put("publicAccess", settings.isPublicAccess());
        config.put("autoTrackPlayers", settings.isAutoTrackPlayers());
        config.put("events", settings.getEvents());
        config.put("dimensions", settings.getDimensions());
        return config;
    }

    private WebMapSettings toSettings(org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig config) {
        WebMapSettings settings = new WebMapSettings();
        Object enabled = config.get("enabled");
        Object paused = config.get("paused");
        Object port = config.get("port");
        Object publicAccess = config.get("publicAccess");
        Object autoTrackPlayers = config.get("autoTrackPlayers");
        Object events = config.get("events");
        Object dimensions = config.get("dimensions");
        if (enabled instanceof Boolean value) {
            settings.setEnabled(value);
        }
        if (paused instanceof Boolean value) {
            settings.setPaused(value);
        }
        if (port instanceof Number value) {
            settings.setPort(value.intValue());
        }
        if (publicAccess instanceof Boolean value) {
            settings.setPublicAccess(value);
        }
        if (autoTrackPlayers instanceof Boolean value) {
            settings.setAutoTrackPlayers(value);
        }
        if (events instanceof java.util.Map<?, ?> rawEvents) {
            WebMapSettings.EventSettings eventSettings = new WebMapSettings.EventSettings();
            Object playerMove = rawEvents.get("playerMove");
            Object chunkLoad = rawEvents.get("chunkLoad");
            Object chunkPopulate = rawEvents.get("chunkPopulate");
            Object blockUpdate = rawEvents.get("blockUpdate");
            if (playerMove instanceof Boolean value) {
                eventSettings.setPlayerMove(value);
            }
            if (chunkLoad instanceof Boolean value) {
                eventSettings.setChunkLoad(value);
            }
            if (chunkPopulate instanceof Boolean value) {
                eventSettings.setChunkPopulate(value);
            }
            if (blockUpdate instanceof Boolean value) {
                eventSettings.setBlockUpdate(value);
            }
            settings.setEvents(eventSettings);
        }
        if (dimensions instanceof java.util.Map<?, ?> rawMap) {
            java.util.Map<String, WebMapDimensionSettings> mapped = new java.util.LinkedHashMap<>();
            for (java.util.Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof java.util.Map<?, ?> rawDimension)) {
                    continue;
                }
                WebMapDimensionSettings dimension = new WebMapDimensionSettings();
                dimension.setWorldKey(key);
                Object displayName = rawDimension.get("displayName");
                Object visible = rawDimension.get("visible");
                Object autoTrack = rawDimension.get("autoTrack");
                Object chunkGenEnabled = rawDimension.get("chunkGenEnabled");
                if (displayName instanceof String value) {
                    dimension.setDisplayName(value);
                }
                if (visible instanceof Boolean value) {
                    dimension.setVisible(value);
                }
                if (autoTrack instanceof Boolean value) {
                    dimension.setAutoTrack(value);
                }
                if (chunkGenEnabled instanceof Boolean value) {
                    dimension.setChunkGenEnabled(value);
                }
                mapped.put(key, dimension);
            }
            settings.setDimensions(mapped);
        }
        return settings;
    }
}
