package org.pexserver.koukunn.bettersurvival.Modules.Feature.PacDiscordSupport;

import java.util.ArrayList;
import java.util.List;

final class PacSupportCaseRecord {
    private final String supportId;
    private final String playerId;
    private final String playerName;
    private final String discordUserId;
    private final String discordUserName;
    private final String guildId;
    private final String channelId;
    private final String panelMessageId;
    private final String appealReason;
    private final long createdAt;
    private final List<PacSupportCaseLog> logs;
    private PacSupportCaseStatus status;
    private long updatedAt;

    PacSupportCaseRecord(String supportId, String playerId, String playerName,
                         String discordUserId, String discordUserName, String guildId,
                         String channelId, String panelMessageId, String appealReason, long createdAt,
                         long updatedAt, PacSupportCaseStatus status, List<PacSupportCaseLog> logs) {
        this.supportId = valueOrEmpty(supportId);
        this.playerId = valueOrEmpty(playerId);
        this.playerName = valueOrEmpty(playerName);
        this.discordUserId = valueOrEmpty(discordUserId);
        this.discordUserName = valueOrEmpty(discordUserName);
        this.guildId = valueOrEmpty(guildId);
        this.channelId = valueOrEmpty(channelId);
        this.panelMessageId = valueOrEmpty(panelMessageId);
        this.appealReason = valueOrEmpty(appealReason);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.status = status == null ? PacSupportCaseStatus.STARTED : status;
        this.logs = new ArrayList<>(logs == null ? List.of() : logs);
    }

    String supportId() { return supportId; }
    String playerId() { return playerId; }
    String playerName() { return playerName; }
    String discordUserId() { return discordUserId; }
    String discordUserName() { return discordUserName; }
    String guildId() { return guildId; }
    String channelId() { return channelId; }
    String panelMessageId() { return panelMessageId; }
    String appealReason() { return appealReason; }
    long createdAt() { return createdAt; }
    long updatedAt() { return updatedAt; }
    PacSupportCaseStatus status() { return status; }
    List<PacSupportCaseLog> logs() { return List.copyOf(logs); }

    void setStatus(PacSupportCaseStatus status) {
        this.status = status;
    }

    void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    void addLog(PacSupportCaseLog log) {
        logs.add(log);
    }

    void removeLastLog() {
        if (!logs.isEmpty()) logs.remove(logs.size() - 1);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
