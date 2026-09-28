package org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Bot;

import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Whitelist.DiscordWhitelistApprovalMode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class DiscordBotSettings {
    private String token = "";
    private String guildId = "";
    private String whitelistChannelId = "";
    private String pacAppealChannelId = "";
    private String pacAppealInboxChannelId = "";
    private String pacBanListChannelId = "";
    private List<String> pacBanListMessageIds = new ArrayList<>();
    private List<String> pacSupportStaffRoleIds = new ArrayList<>();
    private String pacSupportDashboardMessageId = "";
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

    public String getPacAppealChannelId() {
        return pacAppealChannelId == null ? "" : pacAppealChannelId;
    }

    public void setPacAppealChannelId(String pacAppealChannelId) {
        this.pacAppealChannelId = pacAppealChannelId == null ? "" : pacAppealChannelId.trim();
    }

    public String getPacAppealInboxChannelId() {
        return pacAppealInboxChannelId == null ? "" : pacAppealInboxChannelId;
    }

    public void setPacAppealInboxChannelId(String pacAppealInboxChannelId) {
        this.pacAppealInboxChannelId = pacAppealInboxChannelId == null ? "" : pacAppealInboxChannelId.trim();
    }

    public String getPacBanListChannelId() {
        return pacBanListChannelId == null ? "" : pacBanListChannelId;
    }

    public void setPacBanListChannelId(String pacBanListChannelId) {
        this.pacBanListChannelId = pacBanListChannelId == null ? "" : pacBanListChannelId.trim();
    }

    public List<String> getPacBanListMessageIds() {
        return new ArrayList<>(pacBanListMessageIds);
    }

    public void setPacBanListMessageIds(List<String> pacBanListMessageIds) {
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>();
        if (pacBanListMessageIds != null) {
            for (String messageId : pacBanListMessageIds) {
                if (messageId != null && messageId.trim().matches("\\d+")) {
                    uniqueIds.add(messageId.trim());
                }
            }
        }
        this.pacBanListMessageIds = new ArrayList<>(uniqueIds);
    }

    public List<String> getPacSupportStaffRoleIds() {
        return new ArrayList<>(pacSupportStaffRoleIds);
    }

    public void setPacSupportStaffRoleIds(List<String> roleIds) {
        LinkedHashSet<String> uniqueIds = new LinkedHashSet<>();
        if (roleIds != null) {
            for (String roleId : roleIds) {
                if (roleId != null && roleId.trim().matches("\\d+")) {
                    uniqueIds.add(roleId.trim());
                }
            }
        }
        this.pacSupportStaffRoleIds = new ArrayList<>(uniqueIds);
    }

    public void setPacSupportStaffRoleIdsFromText(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            setPacSupportStaffRoleIds(List.of());
            return;
        }
        setPacSupportStaffRoleIds(List.of(rawValue.trim().split("[,\\s]+")));
    }

    public String getPacSupportStaffRoleIdsText() {
        return String.join(", ", pacSupportStaffRoleIds);
    }

    public String getPacSupportDashboardMessageId() {
        return pacSupportDashboardMessageId == null ? "" : pacSupportDashboardMessageId;
    }

    public void setPacSupportDashboardMessageId(String messageId) {
        this.pacSupportDashboardMessageId = messageId == null || !messageId.trim().matches("\\d+")
                ? "" : messageId.trim();
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
