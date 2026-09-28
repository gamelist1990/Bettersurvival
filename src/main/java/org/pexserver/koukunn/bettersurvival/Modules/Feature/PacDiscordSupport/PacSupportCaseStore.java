package org.pexserver.koukunn.bettersurvival.Modules.Feature.PacDiscordSupport;

import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class PacSupportCaseStore {
    private static final String CONFIG_PATH = "discord/pac-support-cases.json";

    private final ConfigManager configManager;
    private final Map<String, PacSupportCaseRecord> cases = new LinkedHashMap<>();
    private final Map<String, BanNoticeReference> banNotices = new LinkedHashMap<>();
    private final Set<String> reservations = new HashSet<>();

    PacSupportCaseStore(ConfigManager configManager) {
        this.configManager = configManager;
        load();
    }

    synchronized boolean reserve(String supportId) {
        String key = normalizeSupportId(supportId);
        if (key.isBlank() || cases.containsKey(key) || !reservations.add(key)) return false;
        return true;
    }

    synchronized void releaseReservation(String supportId) {
        reservations.remove(normalizeSupportId(supportId));
    }

    synchronized boolean create(PacSupportCaseRecord record) {
        String key = normalizeSupportId(record.supportId());
        if (!reservations.contains(key) || cases.containsKey(key)) return false;
        cases.put(key, record);
        if (save()) {
            reservations.remove(key);
            return true;
        }
        cases.remove(key);
        return false;
    }

    synchronized PacSupportCaseRecord bySupportId(String supportId) {
        return cases.get(normalizeSupportId(supportId));
    }

    synchronized PacSupportCaseRecord byChannelId(String channelId) {
        if (channelId == null || channelId.isBlank()) return null;
        return cases.values().stream()
                .filter(record -> channelId.equals(record.channelId()))
                .findFirst()
                .orElse(null);
    }

    synchronized boolean rememberBanNotice(String supportId, String channelId, String messageId,
                                            long expiresAt, boolean permanent) {
        String key = normalizeSupportId(supportId);
        if (key.isBlank() || channelId == null || channelId.isBlank()
                || messageId == null || messageId.isBlank()) return false;
        BanNoticeReference previous = banNotices.get(key);
        banNotices.put(key, new BanNoticeReference(supportId, channelId, messageId,
                expiresAt, permanent, "ACTIVE", previous == null ? "NONE" : previous.appealStatus()));
        if (save()) return true;
        if (previous == null) banNotices.remove(key);
        else banNotices.put(key, previous);
        return false;
    }

    synchronized boolean updateBanNoticeStatus(String supportId, String banStatus, String appealStatus) {
        String key = normalizeSupportId(supportId);
        BanNoticeReference previous = banNotices.get(key);
        if (previous == null) return false;
        banNotices.put(key, new BanNoticeReference(previous.supportId(), previous.channelId(),
                previous.messageId(), previous.expiresAt(), previous.permanent(),
                banStatus == null ? previous.banStatus() : banStatus,
                appealStatus == null ? previous.appealStatus() : appealStatus));
        if (save()) return true;
        banNotices.put(key, previous);
        return false;
    }

    synchronized BanNoticeReference banNotice(String supportId) {
        return banNotices.get(normalizeSupportId(supportId));
    }

    synchronized List<BanNoticeReference> banNotices() {
        return List.copyOf(banNotices.values());
    }

    synchronized List<PacSupportCaseRecord> search(String query, int limit) {
        String term = query == null ? "" : query.trim();
        if (term.isBlank() || term.equalsIgnoreCase("recent") || term.equalsIgnoreCase("latest")) {
            return recent(limit);
        }
        String normalized = term.toLowerCase(Locale.ROOT);
        return cases.values().stream()
                .filter(record -> normalizeSupportId(record.supportId()).equals(normalizeSupportId(term))
                        || record.playerId().equalsIgnoreCase(term)
                        || record.playerName().toLowerCase(Locale.ROOT).equals(normalized)
                        || record.discordUserId().equals(term))
                .sorted(Comparator.comparingLong(PacSupportCaseRecord::updatedAt).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    synchronized List<PacSupportCaseRecord> recent(int limit) {
        return cases.values().stream()
                .sorted(Comparator.comparingLong(PacSupportCaseRecord::updatedAt).reversed())
                .limit(Math.max(1, limit))
                .toList();
    }

    synchronized boolean appendAction(String supportId, String actorId, String actorName,
                                      String action, String details) {
        PacSupportCaseRecord record = bySupportId(supportId);
        if (record == null) return false;
        long previousUpdatedAt = record.updatedAt();
        record.addLog(new PacSupportCaseLog(System.currentTimeMillis(), actorId, actorName, action, details));
        record.setUpdatedAt(System.currentTimeMillis());
        if (save()) return true;
        record.removeLastLog();
        record.setUpdatedAt(previousUpdatedAt);
        return false;
    }

    synchronized boolean recordMessage(String channelId, String actorId, String actorName,
                                       String details, long timestamp) {
        PacSupportCaseRecord record = byChannelId(channelId);
        if (record == null || record.status() != PacSupportCaseStatus.STARTED) return false;
        long previousUpdatedAt = record.updatedAt();
        record.addLog(new PacSupportCaseLog(timestamp, actorId, actorName, "MESSAGE", details));
        record.setUpdatedAt(Math.max(timestamp, previousUpdatedAt));
        if (save()) return true;
        record.removeLastLog();
        record.setUpdatedAt(previousUpdatedAt);
        return false;
    }

    synchronized boolean transition(String supportId, PacSupportCaseStatus next,
                                    String actorId, String actorName, String details) {
        PacSupportCaseRecord record = bySupportId(supportId);
        if (record == null || record.status() != PacSupportCaseStatus.STARTED
                || next == PacSupportCaseStatus.STARTED) return false;
        PacSupportCaseStatus previousStatus = record.status();
        long previousUpdatedAt = record.updatedAt();
        long now = System.currentTimeMillis();
        record.setStatus(next);
        record.setUpdatedAt(now);
        record.addLog(new PacSupportCaseLog(now, actorId, actorName,
                "STATUS_" + next.name(), details));
        if (save()) return true;
        record.setStatus(previousStatus);
        record.setUpdatedAt(previousUpdatedAt);
        record.removeLastLog();
        return false;
    }

    private void load() {
        PEXConfig config = configManager.loadConfig(CONFIG_PATH).orElseGet(PEXConfig::new);
        Object rawCases = config.get("cases");
        if (rawCases instanceof List<?> entries) {
            for (Object rawEntry : entries) {
                if (!(rawEntry instanceof Map<?, ?> entry)) continue;
                String supportId = readString(entry, "supportId");
                if (supportId.isBlank()) continue;
                List<PacSupportCaseLog> logs = readLogs(entry.get("logs"));
                PacSupportCaseRecord record = new PacSupportCaseRecord(
                        supportId,
                        readString(entry, "playerId"),
                        readString(entry, "playerName"),
                        readString(entry, "discordUserId"),
                        readString(entry, "discordUserName"),
                        readString(entry, "guildId"),
                        readString(entry, "channelId"),
                        readString(entry, "panelMessageId"),
                        readString(entry, "appealReason"),
                        readLong(entry, "createdAt"),
                        readLong(entry, "updatedAt"),
                        PacSupportCaseStatus.fromValue(readString(entry, "status")),
                        logs);
                cases.put(normalizeSupportId(supportId), record);
            }
        }
        Object rawBanNotices = config.get("banNotices");
        if (rawBanNotices instanceof List<?> entries) {
            for (Object rawEntry : entries) {
                if (!(rawEntry instanceof Map<?, ?> entry)) continue;
                String supportId = readString(entry, "supportId");
                if (supportId.isBlank()) continue;
                banNotices.put(normalizeSupportId(supportId), new BanNoticeReference(
                        supportId, readString(entry, "channelId"), readString(entry, "messageId"),
                        readLong(entry, "expiresAt"), readBoolean(entry, "permanent"),
                        readStatus(entry, "banStatus", "ACTIVE"),
                        readStatus(entry, "appealStatus", "NONE")));
            }
        }
    }

    private List<PacSupportCaseLog> readLogs(Object value) {
        if (!(value instanceof List<?> entries)) return List.of();
        List<PacSupportCaseLog> logs = new ArrayList<>();
        for (Object rawEntry : entries) {
            if (!(rawEntry instanceof Map<?, ?> entry)) continue;
            logs.add(new PacSupportCaseLog(
                    readLong(entry, "timestamp"),
                    readString(entry, "actorId"),
                    readString(entry, "actorName"),
                    readString(entry, "action"),
                    readString(entry, "details")));
        }
        return logs;
    }

    private boolean save() {
        PEXConfig config = new PEXConfig();
        List<Map<String, Object>> entries = new ArrayList<>(cases.size());
        for (PacSupportCaseRecord record : cases.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("supportId", record.supportId());
            entry.put("playerId", record.playerId());
            entry.put("playerName", record.playerName());
            entry.put("discordUserId", record.discordUserId());
            entry.put("discordUserName", record.discordUserName());
            entry.put("guildId", record.guildId());
            entry.put("channelId", record.channelId());
            entry.put("panelMessageId", record.panelMessageId());
            entry.put("appealReason", record.appealReason());
            entry.put("createdAt", record.createdAt());
            entry.put("updatedAt", record.updatedAt());
            entry.put("status", record.status().name());
            List<Map<String, Object>> logs = new ArrayList<>();
            for (PacSupportCaseLog log : record.logs()) {
                Map<String, Object> logEntry = new HashMap<>();
                logEntry.put("timestamp", log.timestamp());
                logEntry.put("actorId", log.actorId());
                logEntry.put("actorName", log.actorName());
                logEntry.put("action", log.action());
                logEntry.put("details", log.details());
                logs.add(logEntry);
            }
            entry.put("logs", logs);
            entries.add(entry);
        }
        config.put("cases", entries);
        List<Map<String, Object>> noticeEntries = new ArrayList<>(banNotices.size());
        for (BanNoticeReference notice : banNotices.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("supportId", notice.supportId());
            entry.put("channelId", notice.channelId());
            entry.put("messageId", notice.messageId());
            entry.put("expiresAt", notice.expiresAt());
            entry.put("permanent", notice.permanent());
            entry.put("banStatus", notice.banStatus());
            entry.put("appealStatus", notice.appealStatus());
            noticeEntries.add(entry);
        }
        config.put("banNotices", noticeEntries);
        return configManager.saveConfig(CONFIG_PATH, config);
    }

    private String readString(Map<?, ?> entry, String key) {
        Object value = entry.get(key);
        return value instanceof String string ? string : "";
    }

    private long readLong(Map<?, ?> entry, String key) {
        Object value = entry.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private boolean readBoolean(Map<?, ?> entry, String key) {
        Object value = entry.get(key);
        return value instanceof Boolean booleanValue && booleanValue;
    }

    private String readStatus(Map<?, ?> entry, String key, String fallback) {
        String status = readString(entry, key);
        return status.isBlank() ? fallback : status;
    }

    private String normalizeSupportId(String supportId) {
        return supportId == null ? "" : supportId.trim().toUpperCase(Locale.ROOT);
    }

    record BanNoticeReference(String supportId, String channelId, String messageId,
                              long expiresAt, boolean permanent, String banStatus, String appealStatus) {
    }
}
