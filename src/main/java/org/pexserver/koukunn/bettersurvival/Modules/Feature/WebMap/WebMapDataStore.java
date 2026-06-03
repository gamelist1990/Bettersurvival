package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WebMapDataStore {
    private final WebMapStore store;
    private final Map<String, WebMapDimensionData> loaded = new ConcurrentHashMap<>();
    private final Set<String> dirtyWorlds = ConcurrentHashMap.newKeySet();

    public WebMapDataStore(WebMapStore store) {
        this.store = store;
    }

    public WebMapDimensionData get(String worldKey) {
        return loaded.computeIfAbsent(worldKey, store::loadDimensionData);
    }

    public void updateChunk(String worldKey, String worldName, int chunkX, int chunkZ, String color, String[] pixels, long updatedAt) {
        WebMapDimensionData data = get(worldKey);
        synchronized (data) {
            data.setWorldKey(worldKey);
            data.setWorldName(worldName);
            data.getChunks().put(chunkKey(chunkX, chunkZ), new WebMapChunkRecord(chunkX, chunkZ, color, pixels, updatedAt));
        }
        dirtyWorlds.add(worldKey);
    }

    public WebMapChunkRecord getChunk(String worldKey, int chunkX, int chunkZ) {
        WebMapDimensionData data = get(worldKey);
        synchronized (data) {
            return data.getChunks().get(chunkKey(chunkX, chunkZ));
        }
    }

    public Collection<WebMapChunkRecord> snapshotChunks(String worldKey) {
        WebMapDimensionData data = get(worldKey);
        synchronized (data) {
            return new ArrayList<>(data.chunkValues());
        }
    }

    public Map<String, WebMapMarkerRecord> snapshotWaypoints(String worldKey) {
        WebMapDimensionData data = get(worldKey);
        synchronized (data) {
            return new LinkedHashMap<>(data.getWaypoints());
        }
    }

    public void replaceWaypoints(String worldKey, String worldName, Map<String, WebMapMarkerRecord> waypoints) {
        WebMapDimensionData data = get(worldKey);
        boolean changed;
        synchronized (data) {
            data.setWorldKey(worldKey);
            data.setWorldName(worldName);
            Map<String, WebMapMarkerRecord> nextWaypoints = new LinkedHashMap<>(waypoints);
            changed = !data.getWaypoints().equals(nextWaypoints);
            if (changed) {
                data.setWaypoints(nextWaypoints);
            }
        }
        if (changed) {
            dirtyWorlds.add(worldKey);
        }
    }

    public int chunkCount(String worldKey) {
        WebMapDimensionData data = get(worldKey);
        synchronized (data) {
            return data.getChunks().size();
        }
    }

    public synchronized void flushDirty() {
        for (String worldKey : new ArrayList<>(dirtyWorlds)) {
            WebMapDimensionData data = loaded.get(worldKey);
            if (data == null) {
                dirtyWorlds.remove(worldKey);
                continue;
            }
            WebMapDimensionData snapshot = snapshot(data);
            try {
                store.saveDimensionData(snapshot);
                dirtyWorlds.remove(worldKey);
            } catch (IOException ignored) {
            }
        }
    }

    public synchronized void flushAll() {
        dirtyWorlds.addAll(loaded.keySet());
        flushDirty();
    }

    private WebMapDimensionData snapshot(WebMapDimensionData source) {
        WebMapDimensionData copy = new WebMapDimensionData();
        synchronized (source) {
            copy.setWorldKey(source.getWorldKey());
            copy.setWorldName(source.getWorldName());
            Map<String, WebMapChunkRecord> copiedChunks = new LinkedHashMap<>();
            for (Map.Entry<String, WebMapChunkRecord> entry : source.getChunks().entrySet()) {
                WebMapChunkRecord chunk = entry.getValue();
                String[] copiedPixels = chunk.getPixels() == null ? null : chunk.getPixels().clone();
                copiedChunks.put(entry.getKey(), new WebMapChunkRecord(chunk.getX(), chunk.getZ(), chunk.getColor(), copiedPixels, chunk.getUpdatedAt()));
            }
            copy.setChunks(copiedChunks);
            copy.setWaypoints(new LinkedHashMap<>(source.getWaypoints()));
        }
        return copy;
    }

    private String chunkKey(int chunkX, int chunkZ) {
        return chunkX + "," + chunkZ;
    }
}
