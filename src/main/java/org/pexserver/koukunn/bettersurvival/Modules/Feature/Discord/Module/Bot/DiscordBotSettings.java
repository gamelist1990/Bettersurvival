package org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Bot;

import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Whitelist.DiscordWhitelistApprovalMode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class DiscordBotSettings {
    private String token = "";
    private String guildId = "";
    private String whitelistChannelId = "";
    private String whitelistApprovalMode = DiscordWhitelistApprovalMode.DEFAULT.name();
    private List<String> whitelistApproverUserIds = new ArrayList<>();

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

    public DiscordWhitelistApprovalMode getWhitelistApprovalMode() {
        return DiscordWhitelistApprovalMode.fromName(whitelistApprovalMode);
    }

    public void setWhitelistApprovalMode(DiscordWhitelistApprovalMode whitelistApprovalMode) {
        this.whitelistApprovalMode = whitelistApprovalMode == null
                ? DiscordWhitelistApprovalMode.DEFAULT.name()
                : whitelistApprovalMode.name();
    }

    public List<String> getWhitelistApproverUserIds() {
        return new ArrayList<>(whitelistApproverUserIds);
    }

    public void setWhitelistApproverUserIds(List<String> whitelistApproverUserIds) {
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>();
        if (whitelistApproverUserIds != null) {
            for (String userId : whitelistApproverUserIds) {
                if (userId == null) {
                    continue;
                }
                String normalized = userId.trim();
                if (!normalized.isEmpty() && normalized.matches("\\d+")) {
                    uniqueIds.add(normalized);
                }
            }
        }
        this.whitelistApproverUserIds = new ArrayList<>(uniqueIds);
    }

    public void setWhitelistApproverUserIdsFromText(String rawValue) {
        List<String> userIds = new ArrayList<>();
        if (rawValue != null && !rawValue.isBlank()) {
            String[] parts = rawValue.split("[,\\s]+");
            for (String part : parts) {
                if (part == null || part.isBlank()) {
                    continue;
                }
                userIds.add(part.trim());
            }
        }
        setWhitelistApproverUserIds(userIds);
    }

    public String getWhitelistApproverUserIdsText() {
        return String.join(", ", whitelistApproverUserIds);
    }

    public boolean isConfigured() {
        return !getToken().isEmpty();
    }
}
