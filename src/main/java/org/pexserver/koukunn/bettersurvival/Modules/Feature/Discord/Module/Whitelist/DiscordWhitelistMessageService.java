package org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Whitelist;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Message;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Core.Util.ServerInfoUtil;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Bot.DiscordBotRuntime;

import java.time.Instant;

public class DiscordWhitelistMessageService {
    private static final int WHITELIST_MESSAGE_HISTORY_LOOKUP_LIMIT = 100;

    private final Loader plugin;
    private final DiscordBotRuntime runtime;

    private BukkitTask reorderTask;

    public DiscordWhitelistMessageService(Loader plugin, DiscordBotRuntime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
    }

    public void sendWhitelistEmbed(String channelId) {
        JDA jda = runtime.getJda();
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

    public synchronized void triggerWhitelistReorderDelay(String channelId) {
        if (reorderTask != null) {
            reorderTask.cancel();
        }
        reorderTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> executeWhitelistReorder(channelId), 180 * 20L);
    }

    public synchronized void triggerWhitelistReorderNow(String channelId) {
        if (reorderTask != null) {
            reorderTask.cancel();
            reorderTask = null;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> executeWhitelistReorder(channelId));
    }

    public synchronized void shutdown() {
        if (reorderTask == null) return;
        reorderTask.cancel();
        reorderTask = null;
    }

    private void executeWhitelistReorder(String channelId) {
        JDA jda = runtime.getJda();
        if (jda == null || channelId.isBlank()) return;
        var channel = jda.getTextChannelById(channelId);
        if (channel == null) return;

        channel.getHistory().retrievePast(WHITELIST_MESSAGE_HISTORY_LOOKUP_LIMIT).queue(messages -> {
            if (messages.isEmpty()) return;
            boolean latestIsWhitelistMessage = isLatestWhitelistMessage(messages.get(0), jda);

            for (int i = 0; i < messages.size(); i++) {
                var msg = messages.get(i);
                if (!hasWhitelistButton(msg, jda)) continue;
                if (latestIsWhitelistMessage && i == 0) continue;
                msg.delete().queue(
                        success -> { },
                        err -> plugin.getLogger().warning("[DiscordBot] 古い募集メッセージの削除に失敗しました: " + err.getMessage())
                );
            }

            if (!latestIsWhitelistMessage) {
                sendWhitelistEmbed(channelId);
            }
        }, err -> plugin.getLogger().warning("[DiscordBot] 履歴メッセージの取得に失敗しました: " + err.getMessage()));
    }

    private boolean isLatestWhitelistMessage(Message message, JDA jda) {
        return hasWhitelistButton(message, jda);
    }

    private boolean hasWhitelistButton(Message message, JDA jda) {
        if (!message.getAuthor().getId().equals(jda.getSelfUser().getId())) {
            return false;
        }
        for (var componentTree : message.getComponents()) {
            if (!(componentTree instanceof ActionRow row)) {
                continue;
            }
            for (var component : row.getComponents()) {
                if (component instanceof Button button && DiscordWhitelistListener.BUTTON_ID.equals(button.getCustomId())) {
                    return true;
                }
            }
        }
        return false;
    }
}
