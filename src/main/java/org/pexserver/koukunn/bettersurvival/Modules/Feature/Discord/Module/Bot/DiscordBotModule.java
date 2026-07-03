package org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Bot;

import net.dv8tion.jda.api.JDA;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Api.McApiClient;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Whitelist.DiscordWhitelistMessageService;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Whitelist.DiscordWhitelistSettingsMenu;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.OfflineAccess.OfflineAccessManager;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Whitelist.PendingWhitelistModule;

/**
 * Discord Bot を管理するモジュール。
 * Discord 連携全体の公開窓口として、設定保存と各機能モジュールの仲介を担当する。
 */
public class DiscordBotModule {
    private final DiscordBotStore store;
    private final DiscordBotRuntime runtime;
    private final DiscordBotSettingsMenu settingsMenu;
    private final DiscordWhitelistMessageService whitelistMessageService;
    private final DiscordWhitelistSettingsMenu whitelistSettingsMenu;

    private DiscordBotSettings settings;

    public DiscordBotModule(Loader plugin, ConfigManager configManager, PendingWhitelistModule whitelistModule, OfflineAccessManager offlineAccessManager) {
        this.store = new DiscordBotStore(configManager);
        this.settings = store.load();
        McApiClient mcApiClient = new McApiClient(plugin);
        plugin.getServer().getPluginManager().registerEvents(mcApiClient, plugin);
        this.runtime = new DiscordBotRuntime(plugin, whitelistModule, offlineAccessManager, mcApiClient, this::getSettings);
        this.settingsMenu = new DiscordBotSettingsMenu(plugin, this);
        this.whitelistMessageService = new DiscordWhitelistMessageService(plugin, runtime);
        this.whitelistSettingsMenu = new DiscordWhitelistSettingsMenu(this, whitelistMessageService);
        if (settings.isConfigured()) {
            runtime.start(settings);
        }
    }

    public synchronized DiscordBotSettings getSettings() {
        return settings;
    }

    public synchronized boolean saveSettings(DiscordBotSettings newSettings) {
        boolean saved = store.save(newSettings);
        if (saved) {
            this.settings = newSettings;
            whitelistMessageService.shutdown();
            runtime.stop();
            runtime.start(newSettings);
        }
        return saved;
    }

    public synchronized void shutdown() {
        whitelistMessageService.shutdown();
        runtime.stop();
    }

    public void sendWhitelistEmbed(String channelId) {
        whitelistMessageService.sendWhitelistEmbed(channelId);
    }

    public void triggerWhitelistReorderDelay(String channelId) {
        whitelistMessageService.triggerWhitelistReorderDelay(channelId);
    }

    public void openMenu(Player player) {
        settingsMenu.openMenu(player);
    }

    public void openWhitelistChannelMenu(Player player) {
        whitelistSettingsMenu.openMenu(player);
    }

    public boolean isBotOnline() {
        return runtime.isOnline();
    }

    public boolean isBotTokenConfigured() {
        return getSettings().isConfigured();
    }

    public JDA getJda() {
        return runtime.getJda();
    }

    public void registerRuntimeListener(Object listener) {
        runtime.registerListener(listener);
    }

    public void unregisterRuntimeListener(Object listener) {
        runtime.unregisterListener(listener);
    }

    public DiscordBotSettings createUpdatedSettings(String token, String whitelistChannelId) {
        DiscordBotSettings updated = new DiscordBotSettings();
        updated.setToken(token);
        updated.setGuildId(settings.getGuildId());
        updated.setWhitelistChannelId(whitelistChannelId);
        updated.setWhitelistApprovalMode(settings.getWhitelistApprovalMode());
        updated.setWhitelistApproverUserIds(settings.getWhitelistApproverUserIds());
        return updated;
    }
}
