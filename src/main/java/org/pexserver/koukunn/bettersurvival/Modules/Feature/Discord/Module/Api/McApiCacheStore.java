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

    public void update(Player player) {
        boolean isBedrock = FloodgateUtil.isBedrock(player);
        update(
                player.getUniqueId(),
                player.getName(),
                normalizeName(player.getName(), isBedrock),
                isBedrock
        );
    }

    public void update(OfflinePlayer player) {
        String name = player.getName();
        if (name == null || name.isBlank()) {
            return;
        }
        boolean isBedrock = FloodgateUtil.isBedrock(player.getUniqueId());
        update(player.getUniqueId(), name, normalizeName(name, isBedrock), isBedrock);
    }

    public void update(UUID uuid, String rawName, String normalizedName, boolean isBedrock) {
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
        config.put("faceUrl", McApiClient.buildFaceUrl(rawName, isBedrock));
        config.put("skinUrl", McApiClient.buildSkinUrl(rawName, isBedrock));
        config.put("bodyUrl", McApiClient.buildBodyUrl(rawName, isBedrock));
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
