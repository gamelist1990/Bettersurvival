package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable Bukkit-free view consumed by the WebMap HTTP worker threads.
 *
 * <p>The snapshot is built on the main server thread. HTTP handlers must only read this
 * object (plus the WebMap data store) instead of dereferencing Bukkit World/Player objects.</p>
 */
public record WebMapHttpSnapshot(
        List<WorldView> worlds,
        Map<String, WorldView> worldsById,
        List<Map<String, Object>> players,
        int maxPlayers,
        String serverName,
        String serverDescription,
        String serverIp,
        long updatedAt
) {
    public WebMapHttpSnapshot {
        worlds = worlds == null ? List.of() : List.copyOf(worlds);
        worldsById = worldsById == null ? Map.of() : Map.copyOf(worldsById);
        players = players == null ? List.of() : players.stream()
                .map(row -> Map.<String, Object>copyOf(row))
                .toList();
        serverName = serverName == null || serverName.isBlank() ? "Minecraft Server" : serverName;
        serverDescription = serverDescription == null ? "" : serverDescription;
        serverIp = serverIp == null ? "" : serverIp;
    }

    public static WebMapHttpSnapshot empty() {
        return new WebMapHttpSnapshot(List.of(), Map.of(), List.of(), 0,
                "Minecraft Server", "", "", 0L);
    }

    public WorldView findWorld(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return worldsById.get(id);
    }

    public static Map<String, WorldView> indexWorlds(List<WorldView> worlds) {
        if (worlds == null || worlds.isEmpty()) {
            return Map.of();
        }
        Map<String, WorldView> result = new LinkedHashMap<>();
        for (WorldView world : worlds) {
            result.put(world.name(), world);
            result.put(world.key(), world);
        }
        return result;
    }

    public record WorldView(
            String key,
            String name,
            String displayName,
            String type,
            String environment,
            int spawnX,
            int spawnZ,
            boolean visible,
            boolean published
    ) {
    }
}
