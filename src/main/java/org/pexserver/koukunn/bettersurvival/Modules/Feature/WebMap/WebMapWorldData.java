package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import java.util.LinkedHashMap;
import java.util.Map;

public class WebMapWorldData {
    private String worldKey = "";
    private String worldName = "";
    private int chunkCount;
    private Map<String, WebMapMarkerRecord> waypoints = new LinkedHashMap<>();

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

    public int getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = Math.max(0, chunkCount);
    }

    public Map<String, WebMapMarkerRecord> getWaypoints() {
        return waypoints;
    }

    public void setWaypoints(Map<String, WebMapMarkerRecord> waypoints) {
        this.waypoints = waypoints == null ? new LinkedHashMap<>() : waypoints;
    }
}
