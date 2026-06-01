package org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Util.ServerInfoUtil;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.DialogUI;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Whitelist.PendingWhitelistModule;

import java.time.Instant;
import java.util.logging.Level;

/**
 * Discord Bot を管理するモジュール。
 * JDA の起動・停止、設定 UI、ホワイトリスト申請 Embed 送信を担当する。
 */
public class DiscordBotModule {
    private final Loader plugin;
    private final DiscordBotStore store;
    private final McApiClient mcApiClient;
    private final PendingWhitelistModule whitelistModule;

    private JDA jda;
    private DiscordBotSettings settings;

    public DiscordBotModule(Loader plugin, ConfigManager configManager, PendingWhitelistModule whitelistModule) {
        this.plugin = plugin;
        this.store = new DiscordBotStore(configManager);
        this.mcApiClient = new McApiClient(plugin);
        this.whitelistModule = whitelistModule;
        this.settings = store.load();
        if (settings.isConfigured()) {
            startBot();
        }
    }

    private void startBot() {
        if (jda != null) return;
        String token = settings.getToken();
        if (token.isEmpty()) return;
        try {
            jda = JDABuilder.createLight(token)
                    .addEventListeners(new DiscordWhitelistListener(plugin, whitelistModule, mcApiClient))
                    .build();
            plugin.getLogger().info("[DiscordBot] Bot を起動しました");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[DiscordBot] Bot の起動に失敗しました: " + e.getMessage());
            jda = null;
        }
    }

    private org.bukkit.scheduler.BukkitTask reorderTask;

    private void stopBot() {
        if (reorderTask != null) {
            try {
                reorderTask.cancel();
            } catch (Exception e) {}
            reorderTask = null;
        }
        if (jda == null) return;
        try {
            jda.shutdownNow();
            if (!jda.awaitShutdown(10, java.util.concurrent.TimeUnit.SECONDS)) {
                plugin.getLogger().warning("[DiscordBot] Bot のシャットダウンがタイムアウトしました。");
            } else {
                plugin.getLogger().info("[DiscordBot] Bot を正常に停止しました");
            }
        } catch (InterruptedException e) {
            plugin.getLogger().log(Level.WARNING, "[DiscordBot] Bot 停止待機中に割り込みが発生しました: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[DiscordBot] Bot 停止中にエラー: " + e.getMessage());
        } finally {
            jda = null;
        }
    }

    public synchronized DiscordBotSettings getSettings() {
        return settings;
    }

    public synchronized boolean saveSettings(DiscordBotSettings newSettings) {
        boolean saved = store.save(newSettings);
        if (saved) {
            this.settings = newSettings;
            stopBot();
            if (newSettings.isConfigured()) {
                startBot();
            }
        }
        return saved;
    }

    public synchronized void shutdown() {
        stopBot();
    }

    /**
     * 設定済みチャンネルにホワイトリスト申請用 Embed を送信する。
     *
     * @param channelId 送信先のテキストチャンネル ID
     */
    public void sendWhitelistEmbed(String channelId) {
        if (jda == null) {
            plugin.getLogger().warning("[DiscordBot] Bot が起動していません");
            return;
        }
        if (channelId.isBlank()) {
            plugin.getLogger().warning("[DiscordBot] チャンネル ID が未設定です");
            return;
        }
        var channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            plugin.getLogger().warning("[DiscordBot] チャンネルが見つかりません: " + channelId);
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⚔️ " + ServerInfoUtil.getServerName() + " ホワイトリスト申請")
                .setDescription("サーバーへの参加を希望する場合は、下のボタンを押して申請してください。\n" +
                        "Java 版・Bedrock 版どちらにも対応しています。")
                .setColor(0x5865F2)
                .setTimestamp(Instant.now())
                .setFooter(ServerInfoUtil.getServerName());

        Button applyButton = Button.primary(DiscordWhitelistListener.BUTTON_ID, "📝 ホワイトリスト申請");

        channel.sendMessageEmbeds(embed.build())
                .addComponents(ActionRow.of(applyButton))
                .queue(
                        msg -> plugin.getLogger().info("[DiscordBot] ホワイトリスト申請 Embed を送信しました"),
                        err -> plugin.getLogger().warning("[DiscordBot] Embed 送信失敗: " + err.getMessage())
                );
    }

    /**
     * ホワイトリスト申請による募集Embed自動再送（デバウンス）をトリガーする。
     * 3分間新規リクエストがない場合に実行される。
     */
    public synchronized void triggerWhitelistReorderDelay(String channelId) {
        if (reorderTask != null) {
            try {
                reorderTask.cancel();
            } catch (Exception e) {}
        }
        reorderTask = org.bukkit.Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            executeWhitelistReorder(channelId);
        }, 180 * 20L); // 3分間 = 180秒
    }

    private void executeWhitelistReorder(String channelId) {
        if (jda == null) return;
        if (channelId.isBlank()) return;
        var channel = jda.getTextChannelById(channelId);
        if (channel == null) return;

        channel.getHistory().retrievePast(5).queue(messages -> {
            if (messages.isEmpty()) return;

            // 最新メッセージが募集メッセージ（ボタンIDが一致）かどうか確認
            var latestMsg = messages.get(0);
            boolean latestIsApplyMsg = false;
            if (latestMsg.getAuthor().getId().equals(jda.getSelfUser().getId())) {
                for (var union : latestMsg.getComponents()) {
                    if (union instanceof net.dv8tion.jda.api.components.actionrow.ActionRow) {
                        var row = (net.dv8tion.jda.api.components.actionrow.ActionRow) union;
                        for (var component : row.getComponents()) {
                            if (component instanceof net.dv8tion.jda.api.components.buttons.Button) {
                                var btn = (net.dv8tion.jda.api.components.buttons.Button) component;
                                if (DiscordWhitelistListener.BUTTON_ID.equals(btn.getCustomId())) {
                                    latestIsApplyMsg = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            }

            // すでに最下部にある場合は何もしない
            if (latestIsApplyMsg) return;

            // 最新5件の中から古い募集メッセージを探して削除
            boolean hasOldButton = false;
            for (var msg : messages) {
                if (msg.getAuthor().getId().equals(jda.getSelfUser().getId())) {
                    boolean isButtonMsg = false;
                    for (var union : msg.getComponents()) {
                        if (union instanceof net.dv8tion.jda.api.components.actionrow.ActionRow) {
                            var row = (net.dv8tion.jda.api.components.actionrow.ActionRow) union;
                            for (var component : row.getComponents()) {
                                if (component instanceof net.dv8tion.jda.api.components.buttons.Button) {
                                    var btn = (net.dv8tion.jda.api.components.buttons.Button) component;
                                    if (DiscordWhitelistListener.BUTTON_ID.equals(btn.getCustomId())) {
                                        isButtonMsg = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (isButtonMsg) {
                        hasOldButton = true;
                        msg.delete().queue(
                                success -> {},
                                err -> plugin.getLogger().warning("[DiscordBot] 古い募集メッセージの削除に失敗しました: " + err.getMessage())
                        );
                    }
                }
            }

            // 古い募集メッセージがあった（埋もれていた）場合は、最下部に新規送信
            if (hasOldButton) {
                sendWhitelistEmbed(channelId);
            }
        }, err -> plugin.getLogger().warning("[DiscordBot] 履歴メッセージの取得に失敗しました: " + err.getMessage()));
    }

    /**
     * `/discord` コマンドで開く ChestGUI メインメニュー。
     * Webhook 設定と Discord Bot 設定を選べる。
     */
    public void openMenu(Player player) {
        String botStatus = (jda != null) ? "§aオンライン" : "§cオフライン";

        ChestUI.builder()
                .title("Discord 設定")
                .size(27)
                .addButtonAt(11, "§6Webhook 設定", Material.PAPER, "Join/Leave/Status 通知の設定")
                .addButtonAt(15, "§bDiscord Bot 設定", Material.NETHER_STAR, "Bot 状態: " + botStatus)
                .then((result, p) -> {
                    if (result.cancelled || result.slot == null) return;
                    if (result.slot == 11) {
                        plugin.getDiscordWebhookModule().openMenu(p);
                    } else if (result.slot == 15) {
                        openBotSettings(p);
                    }
                })
                .show(player);
    }

    /**
     * Bot 設定 (Token) を DialogUI で開く。
     */
    private void openBotSettings(Player player) {
        DiscordBotSettings current = getSettings();
        DialogUI.builder()
                .title("Discord Bot 設定")
                .body("Bot Token を設定します（Discord Developer Portal で取得）")
                .addTextInput("token", "Bot Token", current.getToken(), 200, false)
                .confirmation("保存", "キャンセル")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) {
                        openMenu(p);
                        return;
                    }
                    DiscordBotSettings updated = new DiscordBotSettings();
                    updated.setToken(result.getText("token"));
                    updated.setGuildId(current.getGuildId());
                    updated.setWhitelistChannelId(current.getWhitelistChannelId());
                    if (saveSettings(updated)) {
                        p.sendMessage("§aDiscord Bot 設定を保存しました");
                    } else {
                        p.sendMessage("§cDiscord Bot 設定の保存に失敗しました");
                    }
                    openMenu(p);
                })
                .show(player);
    }

    /**
     * `/w discord` コマンドで開く ChestGUI。
     * チャンネル ID 設定と申請 Embed 送信を行う。
     */
    public void openWhitelistChannelMenu(Player player) {
        DiscordBotSettings current = getSettings();
        String channelId = current.getWhitelistChannelId();
        String channelLore = channelId.isEmpty() ? "未設定" : channelId;

        ChestUI.builder()
                .title("Discord ホワイトリスト設定")
                .size(27)
                .addButtonAt(11, "§eチャンネル ID 設定", Material.NAME_TAG, "現在: " + channelLore)
                .addButtonAt(15, "§a申請 Embed 送信", Material.PAPER, "設定済みチャンネルに Embed を送信")
                .then((result, p) -> {
                    if (result.cancelled || result.slot == null) return;
                    if (result.slot == 11) {
                        openChannelIdInput(p);
                    } else if (result.slot == 15) {
                        String cId = getSettings().getWhitelistChannelId();
                        if (cId.isEmpty()) {
                            p.sendMessage("§cチャンネル ID が未設定です。先に設定してください");
                            return;
                        }
                        if (jda == null) {
                            p.sendMessage("§cDiscord Bot が起動していません。Token を設定してください");
                            return;
                        }
                        sendWhitelistEmbed(cId);
                        p.sendMessage("§aDiscord にホワイトリスト申請 Embed を送信しました");
                    }
                })
                .show(player);
    }

    private void openChannelIdInput(Player player) {
        DiscordBotSettings current = getSettings();
        DialogUI.builder()
                .title("チャンネル ID 設定")
                .body("ホワイトリスト申請を受け付けるチャンネルの ID を入力してください")
                .addTextInput("channelId", "チャンネル ID", current.getWhitelistChannelId(), 30, false)
                .confirmation("保存", "キャンセル")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) {
                        openWhitelistChannelMenu(p);
                        return;
                    }
                    String newChannelId = result.getText("channelId").trim();
                    if (!newChannelId.isEmpty() && !newChannelId.matches("\\d+")) {
                        p.sendMessage("§cチャンネル ID は数字のみで指定してください");
                        openWhitelistChannelMenu(p);
                        return;
                    }
                    DiscordBotSettings updated = new DiscordBotSettings();
                    updated.setToken(current.getToken());
                    updated.setGuildId(current.getGuildId());
                    updated.setWhitelistChannelId(newChannelId);
                    if (saveSettings(updated)) {
                        p.sendMessage("§aチャンネル ID を保存しました: " + newChannelId);
                    } else {
                        p.sendMessage("§c保存に失敗しました");
                    }
                    openWhitelistChannelMenu(p);
                })
                .show(player);
    }
}
