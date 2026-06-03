package org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.Module.Bot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Api.McApiClient;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Bot.DiscordBotModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.DiscordWebhookClient;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.DiscordWebhookSettings;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiscordWebhookBotModeService {
    private static final int JOIN_COLOR = 0x57F287;
    private static final int LEAVE_COLOR = 0xED4245;
    private static final String URL_REGEX = "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+";
    private static final Pattern LINK_OR_URL_PATTERN = Pattern.compile(
            "\\[([^\\]\\r\\n]+)]\\((" + URL_REGEX + ")\\)|(" + URL_REGEX + ")");

    private final Loader plugin;
    private final DiscordWebhookClient client;
    private final Supplier<DiscordWebhookSettings> settingsSupplier;
    private final DiscordWebhookDiscordListener discordListener;
    private final Map<String, CompletableFuture<String>> webhookUrlCache = new ConcurrentHashMap<>();

    public DiscordWebhookBotModeService(
            Loader plugin,
            DiscordWebhookClient client,
            Supplier<DiscordWebhookSettings> settingsSupplier) {
        this.plugin = plugin;
        this.client = client;
        this.settingsSupplier = settingsSupplier;
        this.discordListener = new DiscordWebhookDiscordListener(settingsSupplier,
                this::relayDiscordMessageToMinecraft);
        registerDiscordListener();
    }

    public void shutdown() {
        DiscordBotModule botModule = plugin.getDiscordBotModule();
        if (botModule != null) {
            botModule.unregisterRuntimeListener(discordListener);
        }
        webhookUrlCache.clear();
    }

    public boolean isAvailable() {
        DiscordBotModule botModule = plugin.getDiscordBotModule();
        return botModule != null && botModule.isBotTokenConfigured();
    }

    public boolean isActive() {
        DiscordWebhookSettings settings = settingsSupplier.get();
        return settings != null
                && settings.isEnabled()
                && settings.isBotModeEnabled()
                && !settings.getBotChannelId().isBlank()
                && isAvailable();
    }

    public void sendJoin(Player player, int onlineCount) {
        if (!isActive())
            return;
        sendPlayerSystemEmbed(player, "サーバーに参加しました", onlineCount, JOIN_COLOR);
    }

    public void sendLeave(Player player, int onlineCount) {
        if (!isActive())
            return;
        sendPlayerSystemEmbed(player, "サーバーから退出しました", onlineCount, LEAVE_COLOR);
    }

    public void sendMinecraftChat(Player player, String message) {
        if (!isActive())
            return;
        DiscordWebhookSettings settings = settingsSupplier.get();
        if (settings == null || !settings.isBotChatRelayEnabled() || message == null || message.isBlank()) {
            return;
        }
        sendWebhookMessage(
                settings.getBotChannelId(),
                normalizedPlayerName(player),
                McApiClient.getFaceUrl(player.getUniqueId(), player.getName(), FloodgateUtil.isBedrock(player)),
                message.trim());
    }

    private void sendPlayerSystemEmbed(Player player, String message, int onlineCount, int color) {
        DiscordWebhookSettings settings = settingsSupplier.get();
        if (settings == null) {
            return;
        }
        sendWebhookEmbed(
                settings.getBotChannelId(),
                normalizedPlayerName(player),
                McApiClient.getFaceUrl(player.getUniqueId(), player.getName(), FloodgateUtil.isBedrock(player)),
                message,
                "オンライン: " + onlineCount + "/" + Bukkit.getMaxPlayers(),
                color);
    }

    private void sendWebhookMessage(String channelId, String username, String avatarUrl, String content) {
        getOrCreateWebhookUrl(channelId).thenAccept(webhookUrl -> {
            if (webhookUrl.isBlank()) {
                return;
            }
            JsonObject payload = new JsonObject();
            payload.addProperty("username", username);
            payload.addProperty("avatar_url", avatarUrl);
            payload.addProperty("content", content);
            JsonObject allowedMentions = new JsonObject();
            allowedMentions.add("parse", new JsonArray());
            payload.add("allowed_mentions", allowedMentions);
            client.send(webhookUrl, payload);
        });
    }

    private void sendWebhookEmbed(String channelId, String username, String avatarUrl, String description, String footerText, int color) {
        getOrCreateWebhookUrl(channelId).thenAccept(webhookUrl -> {
            if (webhookUrl.isBlank()) {
                return;
            }
            JsonObject embed = new JsonObject();
            embed.addProperty("description", description);
            embed.addProperty("color", color);
            embed.addProperty("timestamp", Instant.now().toString());

            JsonObject footer = new JsonObject();
            footer.addProperty("text", footerText);
            embed.add("footer", footer);

            JsonObject payload = new JsonObject();
            payload.addProperty("username", username);
            payload.addProperty("avatar_url", avatarUrl);
            JsonObject allowedMentions = new JsonObject();
            allowedMentions.add("parse", new JsonArray());
            payload.add("allowed_mentions", allowedMentions);
            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            payload.add("embeds", embeds);
            client.send(webhookUrl, payload);
        });
    }

    private CompletableFuture<String> getOrCreateWebhookUrl(String channelId) {
        if (channelId == null || channelId.isBlank()) {
            return CompletableFuture.completedFuture("");
        }
        return webhookUrlCache.computeIfAbsent(channelId, this::createWebhookUrlFuture);
    }

    private CompletableFuture<String> createWebhookUrlFuture(String channelId) {
        CompletableFuture<String> future = new CompletableFuture<>();
        TextChannel channel = getTextChannel(channelId);
        if (channel == null) {
            future.complete("");
            return future;
        }
        if (!channel.getGuild().getSelfMember().hasPermission(channel, Permission.MANAGE_WEBHOOKS)) {
            plugin.getLogger().warning("[DiscordWebhook-Bot] Webhook作成に MANAGE_WEBHOOKS 権限が必要です");
            future.complete("");
            return future;
        }

        channel.retrieveWebhooks().queue(webhooks -> {
            for (Webhook webhook : webhooks) {
                User owner = webhook.getOwnerAsUser();
                if (owner != null
                        && owner.getIdLong() == channel.getJDA().getSelfUser().getIdLong()
                        && webhook.getToken() != null
                        && !webhook.getUrl().isBlank()) {
                    future.complete(webhook.getUrl());
                    return;
                }
            }
            channel.createWebhook("BetterSurvival Relay").queue(
                    created -> future.complete(created.getUrl()),
                    error -> {
                        plugin.getLogger().warning("[DiscordWebhook-Bot] Webhook作成失敗: " + error.getMessage());
                        future.complete("");
                    });
        }, error -> {
            plugin.getLogger().warning("[DiscordWebhook-Bot] Webhook一覧取得失敗: " + error.getMessage());
            future.complete("");
        });

        future.whenComplete((url, error) -> {
            if (error != null || url == null || url.isBlank()) {
                webhookUrlCache.remove(channelId);
            }
        });
        return future;
    }

    private TextChannel getTextChannel(String channelId) {
        if (channelId == null) {
            return null;
        }
        DiscordBotModule botModule = plugin.getDiscordBotModule();
        if (botModule == null) {
            return null;
        }
        JDA jda = botModule.getJda();
        if (jda == null) {
            return null;
        }
        return jda.getTextChannelById(channelId);
    }

    private void relayDiscordMessageToMinecraft(DiscordWebhookDiscordListener.DiscordIncomingMessage message) {
        Component header = Component.text("[Discord] ", NamedTextColor.AQUA)
                .append(Component.text(message.authorName(), NamedTextColor.WHITE))
                .append(Component.text(": ", NamedTextColor.DARK_GRAY));

        Component body = buildMessageComponent(message.content());
        Component fullMessage = header.append(body);

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(fullMessage);
            }
            Bukkit.getConsoleSender().sendMessage(fullMessage);
        });
    }

    private Component buildMessageComponent(String text) {
        Component result = Component.empty();
        Matcher matcher = LINK_OR_URL_PATTERN.matcher(text);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                result = result.append(Component.text(text.substring(last, matcher.start()), NamedTextColor.GRAY));
            }

            String label = matcher.group(1);
            String markdownUrl = matcher.group(2);
            String plainUrl = matcher.group(3);
            String displayText = label != null ? label : plainUrl;
            String openUrl = markdownUrl != null ? markdownUrl : plainUrl;

            result = result.append(
                    Component.text(displayText)
                            .color(NamedTextColor.BLUE)
                            .decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.openUrl(openUrl)));
            last = matcher.end();
        }
        if (last < text.length()) {
            result = result.append(Component.text(text.substring(last), NamedTextColor.GRAY));
        }
        return result;
    }

    private String normalizedPlayerName(Player player) {
        String rawName = player.getName();
        if (!FloodgateUtil.isBedrock(player)) {
            return rawName;
        }
        return FloodgateUtil.stripPrefix(rawName).replace("_", " ");
    }

    private void registerDiscordListener() {
        DiscordBotModule botModule = plugin.getDiscordBotModule();
        if (botModule != null) {
            botModule.registerRuntimeListener(discordListener);
        }
    }
}
