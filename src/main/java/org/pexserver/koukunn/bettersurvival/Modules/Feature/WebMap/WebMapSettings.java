package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import java.util.LinkedHashMap;
import java.util.Map;

public class WebMapSettings {
    public static class EventSettings {
        private boolean playerMove = true;
        private boolean chunkLoad = false;
        private boolean chunkPopulate = false;
        private boolean blockUpdate = false;

        public boolean isPlayerMove() {
            return playerMove;
        }

        public void setPlayerMove(boolean playerMove) {
            this.playerMove = playerMove;
        }

        public boolean isChunkLoad() {
            return chunkLoad;
        }

        public void setChunkLoad(boolean chunkLoad) {
            this.chunkLoad = chunkLoad;
        }

        public boolean isChunkPopulate() {
            return chunkPopulate;
        }

        public void setChunkPopulate(boolean chunkPopulate) {
            this.chunkPopulate = chunkPopulate;
        }

        public boolean isBlockUpdate() {
            return blockUpdate;
        }

        public void setBlockUpdate(boolean blockUpdate) {
            this.blockUpdate = blockUpdate;
        }
    }

    private boolean enabled = true;
    private boolean paused = false;
    private int port = 8123;
    private boolean publicAccess = false;
    private boolean autoTrackPlayers = true;
    private boolean showTpsBar = false;
    private EventSettings events = new EventSettings();
    private Map<String, WebMapDimensionSettings> dimensions = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isPublicAccess() {
        return publicAccess;
    }

    public void setPublicAccess(boolean publicAccess) {
        this.publicAccess = publicAccess;
    }

    public boolean isAutoTrackPlayers() {
        return autoTrackPlayers;
    }

    public void setAutoTrackPlayers(boolean autoTrackPlayers) {
        this.autoTrackPlayers = autoTrackPlayers;
    }

    public boolean isShowTpsBar() {
        return showTpsBar;
    }

    public void setShowTpsBar(boolean showTpsBar) {
        this.showTpsBar = showTpsBar;
    }

    public EventSettings getEvents() {
        return events;
    }

    public void setEvents(EventSettings events) {
        this.events = events == null ? new EventSettings() : events;
    }

    public Map<String, WebMapDimensionSettings> getDimensions() {
        return dimensions;
    }

    public void setDimensions(Map<String, WebMapDimensionSettings> dimensions) {
        this.dimensions = dimensions == null ? new LinkedHashMap<>() : dimensions;
    }
}
