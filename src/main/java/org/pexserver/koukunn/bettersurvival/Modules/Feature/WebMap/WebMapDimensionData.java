package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class WebMapDimensionData {
    private String worldKey = "";
    private String worldName = "";
    private Map<String, WebMapChunkRecord> chunks = new LinkedHashMap<>();

    public String getWorldKey() {
        return worldKey;
    }

    public void setWorldKey(String worldKey) {
        this.worldKey = worldKey;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public Map<String, WebMapChunkRecord> getChunks() {
        return chunks;
    }

    public void setChunks(Map<String, WebMapChunkRecord> chunks) {
        this.chunks = chunks == null ? new LinkedHashMap<>() : chunks;
    }

    public Collection<WebMapChunkRecord> chunkValues() {
        return chunks.values();
    }
}
