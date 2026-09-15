package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

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
    private String publicationMode = "default";
    private String publicationGroup = "default";
    private Set<String> publicationGroups = new LinkedHashSet<>();
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

    public String getPublicationMode() { return publicationMode; }
    public void setPublicationMode(String mode) { this.publicationMode = mode == null ? "default" : mode; }
    public String getPublicationGroup() { return publicationGroup; }
    public void setPublicationGroup(String group) { this.publicationGroup = group == null ? "default" : group; }
    public Set<String> getPublicationGroups() { return publicationGroups; }
    public void setPublicationGroups(Set<String> groups) { this.publicationGroups = groups == null ? new LinkedHashSet<>() : new LinkedHashSet<>(groups); }

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
