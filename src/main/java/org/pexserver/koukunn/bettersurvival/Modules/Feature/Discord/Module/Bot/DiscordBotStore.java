package org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Bot;

import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Whitelist.DiscordWhitelistApprovalMode;

import java.util.ArrayList;
import java.util.List;

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
        settings.setPacAppealChannelId(getString(config, "pacAppealChannelId", ""));
        settings.setPacAppealInboxChannelId(getString(config, "pacAppealInboxChannelId", ""));
        settings.setPacBanListChannelId(getString(config, "pacBanListChannelId", ""));
        settings.setPacBanListMessageIds(readStringList(config.get("pacBanListMessageIds")));
        settings.setPacSupportStaffRoleIds(readStringList(config.get("pacSupportStaffRoleIds")));
        settings.setPacSupportDashboardMessageId(getString(config, "pacSupportDashboardMessageId", ""));
        settings.setWhitelistApprovalMode(DiscordWhitelistApprovalMode.fromName(getString(config, "whitelistApprovalMode", "DEFAULT")));
        settings.setWhitelistApproverUserIds(readStringList(config.get("whitelistApproverUserIds")));
        return settings;
    }

    public boolean save(DiscordBotSettings settings) {
        PEXConfig config = new PEXConfig();
        config.put("token", settings.getToken());
        config.put("guildId", settings.getGuildId());
        config.put("whitelistChannelId", settings.getWhitelistChannelId());
        config.put("pacAppealChannelId", settings.getPacAppealChannelId());
        config.put("pacAppealInboxChannelId", settings.getPacAppealInboxChannelId());
        config.put("pacBanListChannelId", settings.getPacBanListChannelId());
        config.put("pacBanListMessageIds", settings.getPacBanListMessageIds());
        config.put("pacSupportStaffRoleIds", settings.getPacSupportStaffRoleIds());
        config.put("pacSupportDashboardMessageId", settings.getPacSupportDashboardMessageId());
        config.put("whitelistApprovalMode", settings.getWhitelistApprovalMode().name());
        config.put("whitelistApproverUserIds", settings.getWhitelistApproverUserIds());
        return configManager.saveConfig(CONFIG_PATH, config);
    }

    private String getString(PEXConfig config, String key, String fallback) {
        Object value = config.get(key);
        return value instanceof String s ? s : fallback;
    }

    private List<String> readStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return result;
        }
        for (Object entry : list) {
            if (entry instanceof String stringValue && !stringValue.isBlank()) {
                result.add(stringValue.trim());
            }
        }
        return result;
    }
}
