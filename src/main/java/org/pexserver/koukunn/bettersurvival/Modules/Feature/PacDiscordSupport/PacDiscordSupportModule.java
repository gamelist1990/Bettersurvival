package org.pexserver.koukunn.bettersurvival.Modules.Feature.PacDiscordSupport;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.DialogUI;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Bot.DiscordBotModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Bot.DiscordBotSettings;
import org.pexserver.pac.api.PacApi;
import org.pexserver.pac.api.event.PacBanEvent;

import java.util.logging.Level;

public final class PacDiscordSupportModule implements Listener {
    private final Loader plugin;
    private final DiscordBotModule discordBotModule;
    private final PacDiscordSupportListener discordListener;
    private final PacDiscordSupportCaseService caseService;
    private final PacSupportCaseStore caseStore;
    private final BukkitTask banListRefreshTask;
    private volatile PacApi pacApi;

    private PacDiscordSupportModule(Loader plugin, DiscordBotModule discordBotModule, PacApi pacApi) {
        this.plugin = plugin;
        this.discordBotModule = discordBotModule;
        this.pacApi = pacApi;
        this.caseStore = new PacSupportCaseStore(plugin.getConfigManager());
        this.caseService = new PacDiscordSupportCaseService(plugin, caseStore, this::getPacApi,
                discordBotModule::getSettings, discordBotModule::getJda,
                discordBotModule::savePacSupportDashboardMessageId);
        this.discordListener = new PacDiscordSupportListener(plugin, this::getPacApi,
                discordBotModule::getSettings, discordBotModule::getJda,
                caseService,
                discordBotModule::savePacBanListMessageIds);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        discordBotModule.registerRuntimeListener(discordListener);
        discordBotModule.registerRuntimeListener(caseService);
        discordBotModule.setPacDiscordSupportModule(this);
        banListRefreshTask = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> {
                    discordListener.refreshPublishedBanList();
                    caseService.ensureStaffDashboard();
                    caseService.refreshBanNoticeStatuses();
                }, 40L, 1200L);
        plugin.getLogger().info("PAC Discord support integration is enabled.");
    }

    public static PacDiscordSupportModule create(Loader plugin, DiscordBotModule discordBotModule) {
        PacApi api = Bukkit.getServicesManager().load(PacApi.class);
        if (api == null || api.apiVersion() != PacApi.API_VERSION) {
            plugin.getLogger().info("PAC is not available; PAC integration is disabled.");
            return null;
        }
        return new PacDiscordSupportModule(plugin, discordBotModule, api);
    }

    public void openMenu(Player player) {
        DiscordBotSettings settings = discordBotModule.getSettings();
        String pacStatus = getPacApi() == null ? "§c停止中" : "§a有効";
        ChestUI.builder()
                .title("PAC Discord サポート")
                .size(27)
                .addButtonAt(11, "§eチャンネル設定", Material.WRITABLE_BOOK,
                        "PAC: " + pacStatus + "\n申立て受付: " + channelSummary(settings.getPacAppealChannelId())
                                + "\nスタッフ用: " + channelSummary(settings.getPacAppealInboxChannelId())
                                + "\nBAN一覧: " + channelSummary(settings.getPacBanListChannelId())
                                + "\nスタッフロール: " + settings.getPacSupportStaffRoleIds().size() + " 件")
                .addButtonAt(13, "§c現在の BAN 一覧を投稿/更新", Material.BOOK,
                        "設定したチャンネルの PAC BAN 一覧 Embed を更新")
                .addButtonAt(15, "§b申立てフォームを投稿", Material.PAPER,
                        "公開チャンネルに申立てボタンを設置")
                .then((result, p) -> {
                    if (result.cancelled || result.slot == null) return;
                    if (result.slot == 11) {
                        openChannelSettings(p);
                    } else if (result.slot == 13) {
                        publishBanList(p);
                    } else if (result.slot == 15) {
                        publishAppealPanel(p);
                    }
                })
                .show(player);
    }

    public void shutdown() {
        banListRefreshTask.cancel();
        discordListener.shutdown();
        caseService.shutdown();
        pacApi = null;
        discordBotModule.unregisterRuntimeListener(discordListener);
        discordBotModule.unregisterRuntimeListener(caseService);
    }

    @EventHandler
    public void onPacBan(PacBanEvent event) {
        discordListener.sendBanNotice(event.ban());
        discordListener.refreshPublishedBanList();
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (!"PAC".equalsIgnoreCase(event.getPlugin().getName())) return;
        pacApi = null;
        plugin.getLogger().info("PAC is not available; PAC integration is disabled.");
    }

    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        if (!"PAC".equalsIgnoreCase(event.getPlugin().getName())) return;
        try {
            PacApi api = Bukkit.getServicesManager().load(PacApi.class);
            if (api == null || api.apiVersion() != PacApi.API_VERSION) {
                pacApi = null;
                plugin.getLogger().info("PAC is not available; PAC integration is disabled.");
                return;
            }
            pacApi = api;
            plugin.getLogger().info("PAC Discord support integration is enabled.");
            discordListener.refreshPublishedBanList();
            caseService.refreshBanNoticeStatuses();
        } catch (LinkageError error) {
            pacApi = null;
            plugin.getLogger().log(Level.WARNING,
                    "PAC is not available; PAC integration is disabled because its API could not be loaded: "
                            + error.getMessage());
        }
    }

    private PacApi getPacApi() {
        return pacApi;
    }

    private void openChannelSettings(Player player) {
        DiscordBotSettings current = discordBotModule.getSettings();
        DialogUI.builder()
                .title("PAC Discord チャンネル設定")
                .body("公開受付・スタッフ管理パネル・BAN一覧の各チャンネル ID と、PACスタッフロール ID を設定します。\n"
                        + "ロール ID は Discord の開発者モードでコピーし、複数ある場合はカンマ区切りで入力してください。\n"
                        + "専用チャンネルの作成には Bot のチャンネル管理・ロール管理権限が必要です。")
                .addTextInput("appealChannel", "公開受付チャンネル ID", current.getPacAppealChannelId(), 30, false)
                .addTextInput("inboxChannel", "スタッフ用チャンネル ID", current.getPacAppealInboxChannelId(), 30, false)
                .addTextInput("banListChannel", "BAN一覧掲載チャンネル ID", current.getPacBanListChannelId(), 30, false)
                .addTextInput("staffRoles", "PACスタッフ ロールID (カンマ区切り)",
                        current.getPacSupportStaffRoleIdsText(), 500, false)
                .confirmation("保存", "キャンセル")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) {
                        openMenu(p);
                        return;
                    }
                    String appealChannel = valueOrEmpty(result.getText("appealChannel")).trim();
                    String inboxChannel = valueOrEmpty(result.getText("inboxChannel")).trim();
                    String banListChannel = valueOrEmpty(result.getText("banListChannel")).trim();
                    String staffRoles = valueOrEmpty(result.getText("staffRoles")).trim();
                    if (!isValidChannelId(appealChannel) || !isValidChannelId(inboxChannel)
                            || !isValidChannelId(banListChannel) || !isValidRoleIds(staffRoles)) {
                        p.sendMessage("§cチャンネル ID は空欄または数字のみ、スタッフロール ID は数字をカンマ区切りで指定してください");
                        openChannelSettings(p);
                        return;
                    }
                    DiscordBotSettings updated = discordBotModule.createUpdatedSettings(
                            current.getToken(), current.getWhitelistChannelId());
                    updated.setPacAppealChannelId(appealChannel);
                    updated.setPacAppealInboxChannelId(inboxChannel);
                    updated.setPacBanListChannelId(banListChannel);
                    updated.setPacSupportStaffRoleIdsFromText(staffRoles);
                    if (!current.getPacAppealInboxChannelId().equals(inboxChannel)) {
                        updated.setPacSupportDashboardMessageId("");
                    }
                    if (!current.getPacBanListChannelId().equals(banListChannel)) {
                        updated.setPacBanListMessageIds(java.util.List.of());
                    }
                    if (discordBotModule.saveSettings(updated)) {
                        if (!current.getPacSupportStaffRoleIds().equals(updated.getPacSupportStaffRoleIds())) {
                            caseService.refreshOpenCaseStaffRoles(current.getPacSupportStaffRoleIds());
                        }
                        p.sendMessage("§aPAC 異議申し立てチャンネルを保存しました");
                    } else {
                        p.sendMessage("§c保存に失敗しました");
                    }
                    openMenu(p);
                })
                .show(player);
    }

    private void publishAppealPanel(Player player) {
        if (getPacApi() == null) {
            player.sendMessage("§cPAC is not available; PAC連携機能は停止しています");
            return;
        }
        if (discordBotModule.getSettings().getPacAppealChannelId().isBlank()) {
            player.sendMessage("§c先に公開受付チャンネル ID を設定してください");
            return;
        }
        if (!discordBotModule.isBotOnline()) {
            player.sendMessage("§cDiscord Bot が起動していません。Token を設定してください");
            return;
        }
        if (discordListener.publishAppealPanel()) {
            player.sendMessage("§aDiscord に PAC 異議申し立てフォームの投稿を依頼しました");
        } else {
            player.sendMessage("§cDiscord にフォームを投稿できませんでした。Bot の接続状態とチャンネル ID を確認してください");
        }
    }

    private void publishBanList(Player player) {
        if (getPacApi() == null) {
            player.sendMessage("§cPAC is not available; PAC連携機能は停止しています");
            return;
        }
        if (discordBotModule.getSettings().getPacBanListChannelId().isBlank()) {
            player.sendMessage("§c先に BAN 一覧掲載チャンネル ID を設定してください");
            return;
        }
        if (!discordBotModule.isBotOnline()) {
            player.sendMessage("§cDiscord Bot が起動していません。Token を設定してください");
            return;
        }
        if (discordListener.publishBanList()) {
            player.sendMessage("§a現在の PAC BAN 一覧を Discord に投稿/更新しています");
        } else {
            player.sendMessage("§cBAN 一覧を投稿できませんでした。Bot の接続状態とチャンネル ID を確認してください");
        }
    }

    private String channelSummary(String channelId) {
        return channelId.isBlank() ? "未設定" : channelId;
    }

    private boolean isValidChannelId(String channelId) {
        return channelId.isEmpty() || channelId.matches("\\d+");
    }

    private boolean isValidRoleIds(String roleIds) {
        if (roleIds.isEmpty()) return true;
        for (String roleId : roleIds.split("[,\\s]+")) {
            if (!roleId.matches("\\d+")) return false;
        }
        return true;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
