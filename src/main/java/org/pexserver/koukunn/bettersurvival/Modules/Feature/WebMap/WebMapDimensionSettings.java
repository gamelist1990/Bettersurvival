package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

public class WebMapDimensionSettings {
    private String worldKey = "";
    private String displayName = "";
    private boolean visible = false;
    private boolean autoTrack = true;
    private boolean chunkGenEnabled = false;

    public String getWorldKey() {
        return worldKey;
    }

    public void setWorldKey(String worldKey) {
        this.worldKey = worldKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isAutoTrack() {
        return autoTrack;
    }

    public void setAutoTrack(boolean autoTrack) {
        this.autoTrack = autoTrack;
    }

    public boolean isChunkGenEnabled() {
        return chunkGenEnabled;
    }

    public void setChunkGenEnabled(boolean chunkGenEnabled) {
        this.chunkGenEnabled = chunkGenEnabled;
    }
}
