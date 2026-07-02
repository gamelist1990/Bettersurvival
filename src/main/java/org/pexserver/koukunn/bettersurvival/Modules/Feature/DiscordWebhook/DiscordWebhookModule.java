package org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.DialogUI;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.Module.Bot.DiscordWebhookBotModeService;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.Module.Webhook.DiscordWebhookEventService;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.Module.Webhook.DiscordWebhookStatusService;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class DiscordWebhookModule implements Listener {
    private final DiscordWebhookStore store;
    private final DiscordWebhookClient client;
    private final DiscordWebhookEventService webhookEventService;
    private final DiscordWebhookStatusService statusService;
    private final DiscordWebhookBotModeService botModeService;
    private DiscordWebhookSettings settings;

    public DiscordWebhookModule(Loader plugin, ConfigManager configManager) {
        this.store = new DiscordWebhookStore(configManager);
        this.client = new DiscordWebhookClient(plugin);
        this.settings = store.load();
        this.webhookEventService = new DiscordWebhookEventService(client);
        this.statusService = new DiscordWebhookStatusService(plugin, client, this::getSettings, this::saveStatusMessageId);
        this.botModeService = new DiscordWebhookBotModeService(plugin, client, this::getSettings);
        statusService.updateStatusAutoUpdateTask();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        DiscordWebhookSettings current = getSettings();
        if (isBotModeActive(current)) {
            if (!current.isEnabled() || !current.isBotJoinEnabled()) return;
            botModeService.sendJoin(event.getPlayer(), Bukkit.getOnlinePlayers().size());
            return;
        }
        if (!current.isEnabled() || !current.isJoinEnabled()) return;
        if (!client.isValidUrl(current.getEventWebhookUrl())) return;
        webhookEventService.sendJoin(current, event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        DiscordWebhookSettings current = getSettings();
        if (isBotModeActive(current)) {
            if (!current.isEnabled() || !current.isBotLeaveEnabled()) return;
            int online = Math.max(0, Bukkit.getOnlinePlayers().size() - 1);
            botModeService.sendLeave(event.getPlayer(), online);
            return;
        }
        if (!current.isEnabled() || !current.isLeaveEnabled()) return;
        if (!client.isValidUrl(current.getEventWebhookUrl())) return;
        webhookEventService.sendLeave(current, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        DiscordWebhookSettings current = getSettings();
        if (!isBotModeActive(current) || !current.isBotChatRelayEnabled()) {
            return;
        }
        String plainMessage = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (plainMessage.isBlank()) {
            return;
        }
        botModeService.sendMinecraftChat(event.getPlayer(), plainMessage);
    }

    public synchronized DiscordWebhookSettings getSettings() {
        return settings;
    }

    public synchronized boolean saveSettings(DiscordWebhookSettings settings) {
        boolean saved = store.save(settings);
        if (saved) {
            this.settings = settings;
            statusService.updateStatusAutoUpdateTask();
        }
        return saved;
    }

    public synchronized void shutdown() {
        statusService.shutdown();
        botModeService.shutdown();
    }

    public boolean sendStatusNow(Player sender) {
        return statusService.sendStatusNow(sender);
    }

    public void openMenu(Player player) {
        DiscordWebhookSettings current = getSettings();
        DialogUI.builder()
                .title("DiscordWebhook")
                .body("状態: " + (current.isEnabled() ? "有効" : "無効"))
                .body("イベント送信方式: " + (current.isBotModeEnabled() ? "Bot" : "Webhook"))
                .body("Webhook Join/Leave: " + enabledText(current.isJoinEnabled()) + "/" + enabledText(current.isLeaveEnabled()))
                .body("Bot Join/Leave: " + enabledText(current.isBotJoinEnabled()) + "/" + enabledText(current.isBotLeaveEnabled()))
                .body("Botチャット中継: " + enabledText(current.isBotChatRelayEnabled()))
                .body("Bot⇔WebService連携: " + enabledText(current.isBotWebServiceIntegrationEnabled()))
                .body("Status/List: " + enabledText(current.isStatusEnabled()))
                .body("Status送信形式: " + (current.isStatusEmbedEnabled() ? "日本語Embed" : "画像"))
                .body("Status自動更新: " + enabledText(current.isStatusAutoUpdateEnabled()))
                .addAction("設定", 0x57F287)
                .addAction("Status送信", 0x5865F2)
                .onResponse((result, p) -> {
                    int actionIndex = result.getActionIndex();
                    if (actionIndex == 0) {
                        openSettings(p);
                    } else if (actionIndex == 1) {
                        sendStatusNow(p);
                    }
                })
                .show(player);
    }

    private void openSettings(Player player) {
        DiscordWebhookSettings current = getSettings();
        boolean botModeAvailable = botModeService.isAvailable();
        DialogUI.builder()
                .title("DiscordWebhook設定")
                .body("Join/Leave/Chat は Webhook または Bot モードを選べます")
                .body("Botモード利用可否: " + (botModeAvailable ? "利用可能" : "Discord Bot Token 未設定"))
                .addBoolInput("enabled", "DiscordWebhookを有効にする", current.isEnabled())
                .addBoolInput("botModeEnabled", "Join/Leave/Chat に Bot モードを使う", current.isBotModeEnabled())
                .addTextInput("botChannelId", "Botモード用チャンネル ID", current.getBotChannelId(), 30, false)
                .addBoolInput("botJoinEnabled", "Botモード Join通知", current.isBotJoinEnabled())
                .addBoolInput("botLeaveEnabled", "Botモード Leave通知", current.isBotLeaveEnabled())
                .addBoolInput("botChatRelayEnabled", "Discord と Minecraft のチャット中継", current.isBotChatRelayEnabled())
                .addBoolInput("botWebServiceIntegrationEnabled", "Botモードで WebService と連携する", current.isBotWebServiceIntegrationEnabled())
                .addTextInput("eventWebhookUrl", "Join/Leave Webhook URL", current.getEventWebhookUrl(), 2048, true)
                .addBoolInput("joinEnabled", "Webhook Join通知", current.isJoinEnabled())
                .addBoolInput("leaveEnabled", "Webhook Leave通知", current.isLeaveEnabled())
                .addTextInput("statusWebhookUrl", "Status/List Webhook URL", current.getStatusWebhookUrl(), 2048, true)
                .addBoolInput("statusEnabled", "Status/List送信", current.isStatusEnabled())
                .addBoolInput("statusEmbedEnabled", "Status/Listを日本語Embedで送信する", current.isStatusEmbedEnabled())
                .addBoolInput("statusAutoUpdateEnabled", "Status/Listを5分ごとに自動更新", current.isStatusAutoUpdateEnabled())
                .confirmation("保存", "キャンセル")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) {
                        openMenu(p);
                        return;
                    }
                    DiscordWebhookSettings updated = new DiscordWebhookSettings();
                    updated.setEnabled(result.getBool("enabled"));
                    updated.setBotModeEnabled(result.getBool("botModeEnabled"));
                    updated.setBotChannelId(result.getText("botChannelId"));
                    updated.setBotJoinEnabled(result.getBool("botJoinEnabled"));
                    updated.setBotLeaveEnabled(result.getBool("botLeaveEnabled"));
                    updated.setBotChatRelayEnabled(result.getBool("botChatRelayEnabled"));
                    updated.setBotWebServiceIntegrationEnabled(result.getBool("botWebServiceIntegrationEnabled"));
                    updated.setEventWebhookUrl(result.getText("eventWebhookUrl"));
                    updated.setJoinEnabled(result.getBool("joinEnabled"));
                    updated.setLeaveEnabled(result.getBool("leaveEnabled"));
                    updated.setStatusWebhookUrl(result.getText("statusWebhookUrl"));
                    updated.setStatusEnabled(result.getBool("statusEnabled"));
                    updated.setStatusEmbedEnabled(result.getBool("statusEmbedEnabled"));
                    updated.setStatusAutoUpdateEnabled(result.getBool("statusAutoUpdateEnabled"));
                    if (updated.isBotModeEnabled() && !botModeAvailable) {
                        p.sendMessage("§cBotモードは Discord Bot Token 設定後に利用できます");
                        openMenu(p);
                        return;
                    }
                    if (updated.isBotModeEnabled() && !updated.getBotChannelId().isBlank() && !updated.getBotChannelId().matches("\\d+")) {
                        p.sendMessage("§cBotモード用チャンネル ID は数字のみで指定してください");
                        openMenu(p);
                        return;
                    }
                    if (updated.getStatusWebhookUrl().equals(current.getStatusWebhookUrl())) {
                        updated.setStatusMessageId(current.getStatusMessageId());
                    }
                    if (saveSettings(updated)) {
                        p.sendMessage("§aDiscordWebhook設定を保存しました");
                    } else {
                        p.sendMessage("§cDiscordWebhook設定の保存に失敗しました");
                    }
                    openMenu(p);
                })
                .show(player);
    }

    private String enabledText(boolean enabled) {
        return enabled ? "有効" : "無効";
    }

    private synchronized void saveStatusMessageId(String messageId) {
        settings.setStatusMessageId(messageId);
        store.save(settings);
    }

    private boolean isBotModeActive(DiscordWebhookSettings settings) {
        return settings.isBotModeEnabled() && botModeService.isActive();
    }
}
