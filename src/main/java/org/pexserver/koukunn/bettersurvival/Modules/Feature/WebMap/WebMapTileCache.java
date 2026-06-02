package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import java.util.LinkedHashMap;
import java.util.Map;

public class WebMapTileCache {
    private final Map<String, TileEntry> cache;

    public WebMapTileCache(int maxEntries) {
        this.cache = new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, TileEntry> eldest) {
                return size() > maxEntries;
            }
        };
    }

    public synchronized TileEntry get(String key) {
        return cache.get(key);
    }

    public synchronized void put(String key, byte[] payload, String etag, long updatedAt) {
        cache.put(key, new TileEntry(payload, etag, updatedAt));
    }

    public record TileEntry(byte[] payload, String etag, long updatedAt) {
    }
}
