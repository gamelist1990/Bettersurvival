package org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook;

public class DiscordWebhookSettings {
    private boolean enabled;
    private String eventWebhookUrl;
    private String statusWebhookUrl;
    private String statusMessageId;
    private boolean joinEnabled;
    private boolean leaveEnabled;
    private boolean statusEnabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEventWebhookUrl() {
        return eventWebhookUrl == null ? "" : eventWebhookUrl;
    }

    public void setEventWebhookUrl(String eventWebhookUrl) {
        this.eventWebhookUrl = eventWebhookUrl == null ? "" : eventWebhookUrl.trim();
    }

    public String getStatusWebhookUrl() {
        return statusWebhookUrl == null ? "" : statusWebhookUrl;
    }

    public void setStatusWebhookUrl(String statusWebhookUrl) {
        this.statusWebhookUrl = statusWebhookUrl == null ? "" : statusWebhookUrl.trim();
    }

    public String getStatusMessageId() {
        return statusMessageId == null ? "" : statusMessageId;
    }

    public void setStatusMessageId(String statusMessageId) {
        this.statusMessageId = statusMessageId == null ? "" : statusMessageId.trim();
    }

    public boolean isJoinEnabled() {
        return joinEnabled;
    }

    public void setJoinEnabled(boolean joinEnabled) {
        this.joinEnabled = joinEnabled;
    }

    public boolean isLeaveEnabled() {
        return leaveEnabled;
    }

    public void setLeaveEnabled(boolean leaveEnabled) {
        this.leaveEnabled = leaveEnabled;
    }

    public boolean isStatusEnabled() {
        return statusEnabled;
    }

    public void setStatusEnabled(boolean statusEnabled) {
        this.statusEnabled = statusEnabled;
    }
}
