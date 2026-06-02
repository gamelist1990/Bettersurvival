package org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Api;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class McApiCacheStore {
    private static final String CACHE_ROOT = "McAPI/user/";

    private final ConfigManager configManager;

    public McApiCacheStore(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public Optional<String> getFaceUrl(UUID uuid) {
        return getString(uuid, "faceUrl");
    }

    public Optional<String> getSkinUrl(UUID uuid) {
        return getString(uuid, "skinUrl");
    }

    public Optional<String> getBodyUrl(UUID uuid) {
        return getString(uuid, "bodyUrl");
    }

    public Optional<String> getFaceBase64(UUID uuid) {
        return getString(uuid, "faceBase64");
    }

    public Optional<String> getSkinBase64(UUID uuid) {
        return getString(uuid, "skinBase64");
    }

    public Optional<String> getBodyBase64(UUID uuid) {
        return getString(uuid, "bodyBase64");
    }

    public void update(Player player) {
        boolean isBedrock = FloodgateUtil.isBedrock(player);
        update(
                player.getUniqueId(),
                player.getName(),
                normalizeName(player.getName(), isBedrock),
                isBedrock,
                McApiClient.buildFaceUrl(player.getName(), isBedrock),
                McApiClient.buildSkinUrl(player.getName(), isBedrock),
                McApiClient.buildBodyUrl(player.getName(), isBedrock),
                "",
                "",
                ""
        );
    }

    public void update(OfflinePlayer player) {
        String name = player.getName();
        if (name == null || name.isBlank()) {
            return;
        }
        boolean isBedrock = FloodgateUtil.isBedrock(player.getUniqueId());
        update(
                player.getUniqueId(),
                name,
                normalizeName(name, isBedrock),
                isBedrock,
                McApiClient.buildFaceUrl(name, isBedrock),
                McApiClient.buildSkinUrl(name, isBedrock),
                McApiClient.buildBodyUrl(name, isBedrock),
                "",
                "",
                ""
        );
    }

    public void update(
            UUID uuid,
            String rawName,
            String normalizedName,
            boolean isBedrock,
            String faceUrl,
            String skinUrl,
            String bodyUrl,
            String faceBase64,
            String skinBase64,
            String bodyBase64
    ) {
        if (uuid == null || rawName == null || rawName.isBlank()) {
            return;
        }
        String edition = isBedrock ? "bedrock" : "java";
        PEXConfig config = configManager.loadConfig(path(uuid)).orElseGet(PEXConfig::new);
        config.put("uuid", uuid.toString());
        config.put("rawName", rawName);
        config.put("normalizedName", normalizedName);
        config.put("isBedrock", isBedrock);
        config.put("edition", edition);
        config.put("faceUrl", faceUrl);
        config.put("skinUrl", skinUrl);
        config.put("bodyUrl", bodyUrl);
        config.put("faceBase64", faceBase64);
        config.put("skinBase64", skinBase64);
        config.put("bodyBase64", bodyBase64);
        config.put("updatedAt", Instant.now().toString());
        configManager.saveConfig(path(uuid), config);
    }

    private Optional<String> getString(UUID uuid, String key) {
        if (uuid == null) {
            return Optional.empty();
        }
        return configManager.loadConfig(path(uuid))
                .map(config -> config.get(key))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(value -> !value.isBlank());
    }

    private String path(UUID uuid) {
        return CACHE_ROOT + uuid + ".json";
    }

    private String normalizeName(String rawName, boolean isBedrock) {
        if (!isBedrock) {
            return rawName;
        }
        return FloodgateUtil.stripPrefix(rawName).replace("_", " ");
    }
}
