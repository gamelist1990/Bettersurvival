package org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook;

import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

public class DiscordWebhookStore {
    private static final String CONFIG_PATH = "discord/webhook.json";

    private final ConfigManager configManager;

    public DiscordWebhookStore(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public DiscordWebhookSettings load() {
        PEXConfig config = configManager.loadConfig(CONFIG_PATH).orElseGet(PEXConfig::new);
        DiscordWebhookSettings settings = new DiscordWebhookSettings();
        settings.setEnabled(getBoolean(config, "enabled", false));
        settings.setEventWebhookUrl(getString(config, "eventWebhookUrl", ""));
        settings.setStatusWebhookUrl(getString(config, "statusWebhookUrl", ""));
        settings.setStatusMessageId(getString(config, "statusMessageId", ""));
        settings.setJoinEnabled(getBoolean(config, "joinEnabled", true));
        settings.setLeaveEnabled(getBoolean(config, "leaveEnabled", true));
        settings.setStatusEnabled(getBoolean(config, "statusEnabled", true));
        return settings;
    }

    public boolean save(DiscordWebhookSettings settings) {
        PEXConfig config = new PEXConfig();
        config.put("enabled", settings.isEnabled());
        config.put("eventWebhookUrl", settings.getEventWebhookUrl());
        config.put("statusWebhookUrl", settings.getStatusWebhookUrl());
        config.put("statusMessageId", settings.getStatusMessageId());
        config.put("joinEnabled", settings.isJoinEnabled());
        config.put("leaveEnabled", settings.isLeaveEnabled());
        config.put("statusEnabled", settings.isStatusEnabled());
        return configManager.saveConfig(CONFIG_PATH, config);
    }

    private String getString(PEXConfig config, String key, String fallback) {
        Object value = config.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    private boolean getBoolean(PEXConfig config, String key, boolean fallback) {
        Object value = config.get(key);
        return value instanceof Boolean ? (Boolean) value : fallback;
    }
}
