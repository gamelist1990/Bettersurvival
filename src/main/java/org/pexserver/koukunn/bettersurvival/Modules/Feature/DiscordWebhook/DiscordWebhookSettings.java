package org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook;

public class DiscordWebhookSettings {
    private boolean enabled;
    private String eventWebhookUrl;
    private boolean botModeEnabled;
    private String botChannelId;
    private boolean botJoinEnabled;
    private boolean botLeaveEnabled;
    private boolean botChatRelayEnabled;
    private boolean botWebServiceIntegrationEnabled;
    private String statusWebhookUrl;
    private String statusMessageId;
    private boolean joinEnabled;
    private boolean leaveEnabled;
    private boolean statusEnabled;
    private boolean statusEmbedEnabled;
    private boolean statusAutoUpdateEnabled;

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

    public boolean isBotModeEnabled() {
        return botModeEnabled;
    }

    public void setBotModeEnabled(boolean botModeEnabled) {
        this.botModeEnabled = botModeEnabled;
    }

    public String getBotChannelId() {
        return botChannelId == null ? "" : botChannelId;
    }

    public void setBotChannelId(String botChannelId) {
        this.botChannelId = botChannelId == null ? "" : botChannelId.trim();
    }

    public boolean isBotChatRelayEnabled() {
        return botChatRelayEnabled;
    }

    public void setBotChatRelayEnabled(boolean botChatRelayEnabled) {
        this.botChatRelayEnabled = botChatRelayEnabled;
    }

    public boolean isBotWebServiceIntegrationEnabled() {
        return botWebServiceIntegrationEnabled;
    }

    public void setBotWebServiceIntegrationEnabled(boolean botWebServiceIntegrationEnabled) {
        this.botWebServiceIntegrationEnabled = botWebServiceIntegrationEnabled;
    }

    public boolean isBotJoinEnabled() {
        return botJoinEnabled;
    }

    public void setBotJoinEnabled(boolean botJoinEnabled) {
        this.botJoinEnabled = botJoinEnabled;
    }

    public boolean isBotLeaveEnabled() {
        return botLeaveEnabled;
    }

    public void setBotLeaveEnabled(boolean botLeaveEnabled) {
        this.botLeaveEnabled = botLeaveEnabled;
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

    public boolean isStatusEmbedEnabled() {
        return statusEmbedEnabled;
    }

    public void setStatusEmbedEnabled(boolean statusEmbedEnabled) {
        this.statusEmbedEnabled = statusEmbedEnabled;
    }

    public boolean isStatusAutoUpdateEnabled() {
        return statusAutoUpdateEnabled;
    }

    public void setStatusAutoUpdateEnabled(boolean statusAutoUpdateEnabled) {
        this.statusAutoUpdateEnabled = statusAutoUpdateEnabled;
    }
}
