package org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord;

import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

public class DiscordBotStore {
    private static final String CONFIG_PATH = "discord/bot.json";
    private final ConfigManager configManager;

    public DiscordBotStore(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public DiscordBotSettings load() {
        PEXConfig config = configManager.loadConfig(CONFIG_PATH).orElseGet(PEXConfig::new);
        DiscordBotSettings settings = new DiscordBotSettings();
        settings.setToken(getString(config, "token", ""));
        settings.setGuildId(getString(config, "guildId", ""));
        settings.setWhitelistChannelId(getString(config, "whitelistChannelId", ""));
        return settings;
    }

    public boolean save(DiscordBotSettings settings) {
        PEXConfig config = new PEXConfig();
        config.put("token", settings.getToken());
        config.put("guildId", settings.getGuildId());
        config.put("whitelistChannelId", settings.getWhitelistChannelId());
        return configManager.saveConfig(CONFIG_PATH, config);
    }

    private String getString(PEXConfig config, String key, String fallback) {
        Object value = config.get(key);
        return value instanceof String s ? s : fallback;
    }
}
