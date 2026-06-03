package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import java.util.LinkedHashMap;
import java.util.Map;

public class WebMapChunkShardData {
    private String worldKey = "";
    private String worldName = "";
    private int tileX;
    private int tileZ;
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

    public int getTileX() {
        return tileX;
    }

    public void setTileX(int tileX) {
        this.tileX = tileX;
    }

    public int getTileZ() {
        return tileZ;
    }

    public void setTileZ(int tileZ) {
        this.tileZ = tileZ;
    }

    public Map<String, WebMapChunkRecord> getChunks() {
        return chunks;
    }

    public void setChunks(Map<String, WebMapChunkRecord> chunks) {
        this.chunks = chunks == null ? new LinkedHashMap<>() : chunks;
    }
}
