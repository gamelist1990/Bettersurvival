package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class WebMapDataStore {
    private static final int CHUNKS_PER_SHARD = 32;
    private static final int MAX_LOADED_SHARDS = 12;

    private final WebMapStore store;
    private final Map<String, WebMapWorldData> loadedWorlds = new HashMap<>();
    private final LinkedHashMap<String, WebMapChunkShardData> loadedShards = new LinkedHashMap<>(16, 0.75F, true);
    private final Set<String> dirtyWorlds = new HashSet<>();
    private final Set<String> dirtyShards = new HashSet<>();

    public WebMapDataStore(WebMapStore store) {
        this.store = store;
    }

    public synchronized WebMapChunkRecord getChunk(String worldKey, int chunkX, int chunkZ) {
        WebMapChunkShardData shard = getOrLoadShard(worldKey, chunkX, chunkZ);
        return shard.getChunks().get(chunkKey(chunkX, chunkZ));
    }

    public synchronized void updateChunk(String worldKey, String worldName, int chunkX, int chunkZ, String color, String[] pixels, long updatedAt) {
        WebMapWorldData world = getOrLoadWorld(worldKey);
        world.setWorldKey(worldKey);
        world.setWorldName(worldName);

        WebMapChunkShardData shard = getOrLoadShard(worldKey, chunkX, chunkZ);
        shard.setWorldKey(worldKey);
        shard.setWorldName(worldName);

        String key = chunkKey(chunkX, chunkZ);
        boolean isNew = shard.getChunks().put(key, new WebMapChunkRecord(chunkX, chunkZ, color, pixels, updatedAt)) == null;
        if (isNew) {
            world.setChunkCount(world.getChunkCount() + 1);
            dirtyWorlds.add(worldKey);
        }
        dirtyShards.add(shardKey(worldKey, shard.getTileX(), shard.getTileZ()));
        trimShardCache();
    }

    public synchronized Collection<WebMapChunkRecord> snapshotChunks(String worldKey) {
        Collection<WebMapChunkRecord> chunks = new ArrayList<>();
        String prefix = shardPrefix(worldKey);
        for (Map.Entry<String, WebMapChunkShardData> entry : loadedShards.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            chunks.addAll(new ArrayList<>(entry.getValue().getChunks().values()));
        }
        return chunks;
    }

    public synchronized Map<String, WebMapMarkerRecord> snapshotWaypoints(String worldKey) {
        WebMapWorldData world = getOrLoadWorld(worldKey);
        return new LinkedHashMap<>(world.getWaypoints());
    }

    public synchronized void replaceWaypoints(String worldKey, String worldName, Map<String, WebMapMarkerRecord> waypoints) {
        WebMapWorldData world = getOrLoadWorld(worldKey);
        world.setWorldKey(worldKey);
        world.setWorldName(worldName);
        Map<String, WebMapMarkerRecord> nextWaypoints = new LinkedHashMap<>(waypoints);
        boolean changed = !world.getWaypoints().equals(nextWaypoints);
        if (changed) {
            world.setWaypoints(nextWaypoints);
            dirtyWorlds.add(worldKey);
        }
    }

    public synchronized int chunkCount(String worldKey) {
        return getOrLoadWorld(worldKey).getChunkCount();
    }

    public synchronized void flushDirty() {
        for (String worldKey : new ArrayList<>(dirtyWorlds)) {
            WebMapWorldData world = loadedWorlds.get(worldKey);
            if (world == null) {
                dirtyWorlds.remove(worldKey);
                continue;
            }
            try {
                store.saveWorldData(snapshotWorld(world));
                dirtyWorlds.remove(worldKey);
            } catch (IOException ignored) {
            }
        }

        for (String shardKey : new ArrayList<>(dirtyShards)) {
            WebMapChunkShardData shard = loadedShards.get(shardKey);
            if (shard == null) {
                dirtyShards.remove(shardKey);
                continue;
            }
            try {
                store.saveChunkShard(snapshotShard(shard));
                dirtyShards.remove(shardKey);
            } catch (IOException ignored) {
            }
        }

        trimShardCache();
    }

    public synchronized void flushAll() {
        dirtyWorlds.addAll(loadedWorlds.keySet());
        dirtyShards.addAll(loadedShards.keySet());
        flushDirty();
    }

    private WebMapWorldData getOrLoadWorld(String worldKey) {
        WebMapWorldData world = loadedWorlds.get(worldKey);
        if (world != null) {
            return world;
        }
        world = store.loadWorldData(worldKey);
        loadedWorlds.put(worldKey, world);
        return world;
    }

    private WebMapChunkShardData getOrLoadShard(String worldKey, int chunkX, int chunkZ) {
        int tileX = shardTile(chunkX);
        int tileZ = shardTile(chunkZ);
        String key = shardKey(worldKey, tileX, tileZ);
        WebMapChunkShardData shard = loadedShards.get(key);
        if (shard != null) {
            return shard;
        }
        shard = store.loadChunkShard(worldKey, tileX, tileZ);
        loadedShards.put(key, shard);
        trimShardCache();
        return shard;
    }

    private void trimShardCache() {
        if (loadedShards.size() <= MAX_LOADED_SHARDS) {
            return;
        }
        Iterator<Map.Entry<String, WebMapChunkShardData>> iterator = loadedShards.entrySet().iterator();
        while (loadedShards.size() > MAX_LOADED_SHARDS && iterator.hasNext()) {
            Map.Entry<String, WebMapChunkShardData> entry = iterator.next();
            if (dirtyShards.contains(entry.getKey())) {
                continue;
            }
            iterator.remove();
        }
    }

    private WebMapWorldData snapshotWorld(WebMapWorldData source) {
        WebMapWorldData copy = new WebMapWorldData();
        copy.setWorldKey(source.getWorldKey());
        copy.setWorldName(source.getWorldName());
        copy.setChunkCount(source.getChunkCount());
        copy.setWaypoints(new LinkedHashMap<>(source.getWaypoints()));
        return copy;
    }

    private WebMapChunkShardData snapshotShard(WebMapChunkShardData source) {
        WebMapChunkShardData copy = new WebMapChunkShardData();
        copy.setWorldKey(source.getWorldKey());
        copy.setWorldName(source.getWorldName());
        copy.setTileX(source.getTileX());
        copy.setTileZ(source.getTileZ());
        copy.setChunks(new LinkedHashMap<>(source.getChunks()));
        return copy;
    }

    private String shardTileKey(String worldKey, int tileX, int tileZ) {
        return worldKey + ":" + tileX + ":" + tileZ;
    }

    private String shardKey(String worldKey, int tileX, int tileZ) {
        return shardTileKey(worldKey, tileX, tileZ);
    }

    private String shardPrefix(String worldKey) {
        return worldKey + ":";
    }

    private int shardTile(int chunkCoordinate) {
        return Math.floorDiv(chunkCoordinate, CHUNKS_PER_SHARD);
    }

    private String chunkKey(int chunkX, int chunkZ) {
        return chunkX + "," + chunkZ;
    }
}
