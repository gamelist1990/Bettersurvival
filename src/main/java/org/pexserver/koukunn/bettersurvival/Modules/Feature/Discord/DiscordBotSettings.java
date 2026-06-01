package org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord;

public class DiscordBotSettings {
    private String token = "";
    private String guildId = "";
    private String whitelistChannelId = "";

    public String getToken() {
        return token == null ? "" : token;
    }

    public void setToken(String token) {
        this.token = token == null ? "" : token.trim();
    }

    public String getGuildId() {
        return guildId == null ? "" : guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId == null ? "" : guildId.trim();
    }

    public String getWhitelistChannelId() {
        return whitelistChannelId == null ? "" : whitelistChannelId;
    }

    public void setWhitelistChannelId(String whitelistChannelId) {
        this.whitelistChannelId = whitelistChannelId == null ? "" : whitelistChannelId.trim();
    }

    public boolean isConfigured() {
        return !getToken().isEmpty();
    }
}
