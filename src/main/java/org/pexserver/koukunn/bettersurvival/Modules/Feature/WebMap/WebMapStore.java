package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import org.bukkit.World;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class WebMapStore {
    private static final String SETTINGS_PATH = "WebMap/settings.json";
    private static final Gson GSON = new Gson();
    private static final java.lang.reflect.Type WAYPOINTS_TYPE = new TypeToken<Map<String, WebMapMarkerRecord>>() {
    }.getType();

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

    public WebMapWorldData loadWorldData(String worldKey) {
        File file = worldDataFile(worldKey);
        if (file.exists()) {
            try {
                WebMapWorldData data = loadJson(file, WebMapWorldData.class);
                if (data == null) {
                    throw new IOException("Empty WebMap world data: " + file.getPath());
                }
                return normalize(data, worldKey);
            } catch (IOException | RuntimeException error) {
                return recoverWorldData(file, worldKey, true, error);
            }
        }
        File legacy = legacyWorldDataFile(worldKey);
        if (legacy.exists()) {
            try {
                WebMapWorldData data = loadWorldDataFromLegacy(legacy, worldKey);
                saveWorldData(data);
                return data;
            } catch (IOException | RuntimeException error) {
                return recoverWorldData(legacy, worldKey, false, error);
            }
        }
        return normalize(new WebMapWorldData(), worldKey);
    }

    public void saveWorldData(WebMapWorldData data) throws IOException {
        writeJsonAtomic(worldDataFile(data.getWorldKey()), normalize(data, data.getWorldKey()));
    }

    public WebMapChunkShardData loadChunkShard(String worldKey, int tileX, int tileZ) {
        File file = chunkShardFile(worldKey, tileX, tileZ);
        if (file.exists()) {
            try {
                WebMapChunkShardData shard = loadJson(file, WebMapChunkShardData.class);
                if (shard == null) {
                    throw new IOException("Empty WebMap chunk shard: " + file.getPath());
                }
                return normalize(shard, worldKey, tileX, tileZ);
            } catch (IOException | RuntimeException error) {
                return recoverChunkShard(file, worldKey, tileX, tileZ, true, false, error);
            }
        }
        File legacy = legacyWorldDataFile(worldKey);
        if (legacy.exists()) {
            try {
                WebMapChunkShardData data = loadChunkShardFromLegacy(legacy, worldKey, tileX, tileZ);
                writeJsonAtomic(file, normalize(data, worldKey, tileX, tileZ));
                return data;
            } catch (IOException | RuntimeException error) {
                return recoverChunkShard(legacy, worldKey, tileX, tileZ, false, true, error);
            }
        }
        return normalize(new WebMapChunkShardData(), worldKey, tileX, tileZ);
    }

    public void saveChunkShard(WebMapChunkShardData shard) throws IOException {
        writeJsonAtomic(chunkShardFile(shard.getWorldKey(), shard.getTileX(), shard.getTileZ()),
                normalize(shard, shard.getWorldKey(), shard.getTileX(), shard.getTileZ()));
    }

    private WebMapWorldData recoverWorldData(File source, String worldKey, boolean archiveSource, Exception error) {
        WebMapWorldData data = normalize(new WebMapWorldData(), worldKey);
        try {
            data = recoverWorldDataFromRaw(WebMapJsonRecovery.readRaw(source), worldKey);
            persistRecoveredWorldData(source, worldKey, data, archiveSource);
            configManager.getPlugin().getLogger().warning(
                    "WebMap world data を復旧しました: " + source.getPath() + " (" + error.getMessage() + ")");
            return data;
        } catch (IOException | RuntimeException ioError) {
            configManager.getPlugin().getLogger().warning(
                    "WebMap world data の復旧に失敗しました: " + source.getPath() + " - " + ioError.getMessage());
            return data;
        }
    }

    private WebMapChunkShardData recoverChunkShard(File source, String worldKey, int tileX, int tileZ, boolean archiveSource, boolean filterToTile, Exception error) {
        WebMapChunkShardData shard = normalize(new WebMapChunkShardData(), worldKey, tileX, tileZ);
        try {
            shard = recoverChunkShardFromRaw(WebMapJsonRecovery.readRaw(source), worldKey, tileX, tileZ, filterToTile);
            persistRecoveredChunkShard(source, worldKey, tileX, tileZ, shard, archiveSource);
            configManager.getPlugin().getLogger().warning(
                    "WebMap chunk shard を復旧しました: " + source.getPath() + " (" + error.getMessage() + ")");
            return shard;
        } catch (IOException | RuntimeException ioError) {
            configManager.getPlugin().getLogger().warning(
                    "WebMap chunk shard の復旧に失敗しました: " + source.getPath() + " - " + ioError.getMessage());
            return shard;
        }
    }

    private WebMapWorldData recoverWorldDataFromRaw(String raw, String worldKey) {
        WebMapWorldData data = normalize(new WebMapWorldData(), worldKey);
        String recoveredWorldKey = WebMapJsonRecovery.recoverStringField(raw, "worldKey");
        if (recoveredWorldKey != null && !recoveredWorldKey.isBlank()) {
            data.setWorldKey(recoveredWorldKey);
        }
        String recoveredWorldName = WebMapJsonRecovery.recoverStringField(raw, "worldName");
        if (recoveredWorldName != null && !recoveredWorldName.isBlank()) {
            data.setWorldName(recoveredWorldName);
        }
        Integer recoveredChunkCount = WebMapJsonRecovery.recoverIntField(raw, "chunkCount");
        if (recoveredChunkCount != null) {
            data.setChunkCount(recoveredChunkCount);
        }
        if (raw.contains("\"waypoints\"")) {
            WebMapJsonRecovery.RecoveryMap<WebMapMarkerRecord> waypoints = WebMapJsonRecovery.recoverMapField(raw, "waypoints", WebMapMarkerRecord.class);
            data.setWaypoints(waypoints.values());
        }
        if (data.getChunkCount() <= 0 && raw.contains("\"chunks\"")) {
            data.setChunkCount(WebMapJsonRecovery.countMapEntries(raw, "chunks"));
        }
        return normalize(data, worldKey);
    }

    private WebMapChunkShardData recoverChunkShardFromRaw(String raw, String worldKey, int tileX, int tileZ, boolean filterToTile) {
        WebMapChunkShardData shard = normalize(new WebMapChunkShardData(), worldKey, tileX, tileZ);
        String recoveredWorldKey = WebMapJsonRecovery.recoverStringField(raw, "worldKey");
        if (recoveredWorldKey != null && !recoveredWorldKey.isBlank()) {
            shard.setWorldKey(recoveredWorldKey);
        }
        String recoveredWorldName = WebMapJsonRecovery.recoverStringField(raw, "worldName");
        if (recoveredWorldName != null && !recoveredWorldName.isBlank()) {
            shard.setWorldName(recoveredWorldName);
        }
        Integer recoveredTileX = WebMapJsonRecovery.recoverIntField(raw, "tileX");
        if (recoveredTileX != null) {
            shard.setTileX(recoveredTileX);
        }
        Integer recoveredTileZ = WebMapJsonRecovery.recoverIntField(raw, "tileZ");
        if (recoveredTileZ != null) {
            shard.setTileZ(recoveredTileZ);
        }
        if (raw.contains("\"chunks\"")) {
            WebMapJsonRecovery.RecoveryMap<WebMapChunkRecord> chunks = WebMapJsonRecovery.recoverMapField(raw, "chunks", WebMapChunkRecord.class,
                    chunk -> !filterToTile || isChunkInTile(chunk, tileX, tileZ));
            Map<String, WebMapChunkRecord> recovered = new LinkedHashMap<>();
            for (Map.Entry<String, WebMapChunkRecord> entry : chunks.values().entrySet()) {
                WebMapChunkRecord chunk = entry.getValue();
                if (chunk != null) {
                    recovered.put(entry.getKey(), chunk);
                }
            }
            shard.setChunks(recovered);
        }
        return normalize(shard, worldKey, tileX, tileZ);
    }

    private void persistRecoveredWorldData(File source, String worldKey, WebMapWorldData data, boolean archiveSource) throws IOException {
        if (archiveSource) {
            archiveCorruptFile(source);
        }
        data.setWorldKey(worldKey);
        writeJsonAtomic(worldDataFile(worldKey), normalize(data, worldKey));
    }

    private void persistRecoveredChunkShard(File source, String worldKey, int tileX, int tileZ, WebMapChunkShardData shard, boolean archiveSource) throws IOException {
        if (archiveSource) {
            archiveCorruptFile(source);
        }
        shard.setWorldKey(worldKey);
        shard.setTileX(tileX);
        shard.setTileZ(tileZ);
        writeJsonAtomic(chunkShardFile(worldKey, tileX, tileZ), normalize(shard, worldKey, tileX, tileZ));
    }

    private void archiveCorruptFile(File source) {
        if (!source.exists()) {
            return;
        }
        File parent = source.getParentFile();
        if (parent == null) {
            return;
        }
        String name = source.getName();
        String baseName = name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
        File backup = new File(parent, baseName + ".corrupt-" + System.currentTimeMillis() + ".json");
        try {
            Files.move(source.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            configManager.getPlugin().getLogger().warning("壊れた WebMap JSON を退避しました: " + source.getPath() + " -> " + backup.getPath());
        } catch (IOException error) {
            configManager.getPlugin().getLogger().warning("壊れた WebMap JSON の退避に失敗しました: " + source.getPath() + " - " + error.getMessage());
        }
    }

    private boolean isChunkInTile(WebMapChunkRecord chunk, int tileX, int tileZ) {
        return Math.floorDiv(chunk.getX(), 32) == tileX && Math.floorDiv(chunk.getZ(), 32) == tileZ;
    }

    private void writeJsonAtomic(File file, Object value) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        File tempFile = File.createTempFile(file.getName(), ".tmp", parent);
        try {
            try (Writer writer = Files.newBufferedWriter(tempFile.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(value, writer);
            }
            try {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (tempFile.exists() && !tempFile.equals(file)) {
                tempFile.delete();
            }
        }
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

    private WebMapWorldData loadWorldDataFromLegacy(File file, String worldKey) throws IOException {
        WebMapWorldData data = normalize(new WebMapWorldData(), worldKey);
        try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                switch (name) {
                    case "worldKey" -> {
                        String value = readNullableString(reader);
                        if (value != null && !value.isBlank()) {
                            data.setWorldKey(value);
                        }
                    }
                    case "worldName" -> {
                        String value = readNullableString(reader);
                        if (value != null && !value.isBlank()) {
                            data.setWorldName(value);
                        }
                    }
                    case "chunks" -> data.setChunkCount(readLegacyChunkCount(reader));
                    case "waypoints" -> data.setWaypoints(readWaypoints(reader));
                    default -> reader.skipValue();
                }
            }
            reader.endObject();
        }
        return normalize(data, worldKey);
    }

    private WebMapChunkShardData loadChunkShardFromLegacy(File file, String worldKey, int tileX, int tileZ) throws IOException {
        WebMapChunkShardData shard = normalize(new WebMapChunkShardData(), worldKey, tileX, tileZ);
        try (JsonReader reader = new JsonReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                switch (name) {
                    case "worldKey" -> {
                        String value = readNullableString(reader);
                        if (value != null && !value.isBlank()) {
                            shard.setWorldKey(value);
                        }
                    }
                    case "worldName" -> {
                        String value = readNullableString(reader);
                        if (value != null && !value.isBlank()) {
                            shard.setWorldName(value);
                        }
                    }
                    case "chunks" -> shard.setChunks(readLegacyChunkShard(reader, tileX, tileZ));
                    default -> reader.skipValue();
                }
            }
            reader.endObject();
        }
        return normalize(shard, worldKey, tileX, tileZ);
    }

    private Map<String, WebMapMarkerRecord> readWaypoints(JsonReader reader) throws IOException {
        Map<String, WebMapMarkerRecord> waypoints = GSON.fromJson(reader, WAYPOINTS_TYPE);
        return waypoints == null ? new LinkedHashMap<>() : waypoints;
    }

    private Map<String, WebMapChunkRecord> readLegacyChunkShard(JsonReader reader, int tileX, int tileZ) throws IOException {
        Map<String, WebMapChunkRecord> chunks = new LinkedHashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            WebMapChunkRecord chunk = GSON.fromJson(reader, WebMapChunkRecord.class);
            if (chunk == null) {
                continue;
            }
            if (Math.floorDiv(chunk.getX(), 32) != tileX || Math.floorDiv(chunk.getZ(), 32) != tileZ) {
                continue;
            }
            chunks.put(key, chunk);
        }
        reader.endObject();
        return chunks;
    }

    private int readLegacyChunkCount(JsonReader reader) throws IOException {
        int count = 0;
        reader.beginObject();
        while (reader.hasNext()) {
            reader.nextName();
            reader.skipValue();
            count++;
        }
        reader.endObject();
        return count;
    }

    private String readNullableString(JsonReader reader) throws IOException {
        if (reader.peek() == com.google.gson.stream.JsonToken.NULL) {
            reader.nextNull();
            return null;
        }
        return reader.nextString();
    }

    private <T> T loadJson(File file, Class<T> type) {
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, type);
        } catch (IOException ignored) {
            return null;
        }
    }

    private WebMapWorldData normalize(WebMapWorldData data, String worldKey) {
        WebMapWorldData value = data == null ? new WebMapWorldData() : data;
        value.setWorldKey(worldKey);
        if (value.getWorldName() == null || value.getWorldName().isBlank()) {
            value.setWorldName(worldKey);
        }
        if (value.getWaypoints() == null) {
            value.setWaypoints(new LinkedHashMap<>());
        }
        value.setChunkCount(value.getChunkCount());
        return value;
    }

    private WebMapChunkShardData normalize(WebMapChunkShardData data, String worldKey, int tileX, int tileZ) {
        WebMapChunkShardData value = data == null ? new WebMapChunkShardData() : data;
        value.setWorldKey(worldKey);
        if (value.getWorldName() == null || value.getWorldName().isBlank()) {
            value.setWorldName(worldKey);
        }
        value.setTileX(tileX);
        value.setTileZ(tileZ);
        if (value.getChunks() == null) {
            value.setChunks(new LinkedHashMap<>());
        }
        return value;
    }

    private File worldDataFile(String worldKey) {
        return new File(configManager.getBaseDir(), "WebMap/worlds/" + sanitizeWorldKey(worldKey) + ".json");
    }

    private File legacyWorldDataFile(String worldKey) {
        return new File(configManager.getBaseDir(), "WebMap/" + sanitizeWorldKey(worldKey) + ".json");
    }

    private File chunkShardFile(String worldKey, int tileX, int tileZ) {
        return new File(configManager.getBaseDir(),
                "WebMap/chunks/" + sanitizeWorldKey(worldKey) + "/" + tileX + "_" + tileZ + ".json");
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
