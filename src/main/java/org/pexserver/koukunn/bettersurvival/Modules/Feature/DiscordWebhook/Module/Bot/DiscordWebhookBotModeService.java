package org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.Module.Bot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Api.McApiClient;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Bot.DiscordBotModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.DiscordWebhookClient;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.DiscordWebhookSettings;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService.FeedTextUtil;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService.WebPost;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService.WebServiceModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.time.Instant;

public class DiscordWebhookBotModeService {
    private static final int JOIN_COLOR = 0x57F287;
    private static final int LEAVE_COLOR = 0xED4245;

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

    /**
     * Web 投稿を Discord へ中継する。
     * 本文は素の URL を [サイト名](URL) に変換し、
     * Web でアップロードされた画像 (data URL) はファイルとして添付する。
     */
    public void sendWebServicePost(WebPost post) {
        if (!isActive() || post == null || (post.getText().isBlank() && post.getAttachments().isEmpty())) {
            return;
        }
        DiscordWebhookSettings settings = settingsSupplier.get();
        if (settings == null || !settings.isBotWebServiceIntegrationEnabled()) {
            return;
        }
        StringBuilder content = new StringBuilder(FeedTextUtil.toDiscordContent(post.getText()));
        List<DiscordWebhookClient.FileUpload> files = new ArrayList<>();
        int imageIndex = 1;
        for (WebPost.Attachment attachment : post.getAttachments()) {
            if (attachment == null || attachment.getUrl().isBlank()) {
                continue;
            }
            DiscordWebhookClient.FileUpload upload = files.size() < 4 ? loadAttachmentFile(attachment, imageIndex) : null;
            if (upload != null) {
                files.add(upload);
                imageIndex++;
                continue;
            }
            // ファイル化できない添付はリンクとして載せる
            if (attachment.getUrl().startsWith("http://") || attachment.getUrl().startsWith("https://")) {
                if (!content.isEmpty()) {
                    content.append('\n');
                }
                content.append(attachment.getUrl());
            }
        }
        sendWebhookMessage(
                settings.getBotChannelId(),
                displayWebAuthor(post),
                post.getFaceUrl(),
                content.toString(),
                files);
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
        sendWebhookMessage(channelId, username, avatarUrl, content, List.of());
    }

    private void sendWebhookMessage(String channelId, String username, String avatarUrl, String content,
                                    List<DiscordWebhookClient.FileUpload> files) {
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
            client.send(webhookUrl, payload, files);
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
        DiscordWebhookSettings settings = settingsSupplier.get();
        mirrorDiscordMessageToWebService(message);

        if (settings == null || !settings.isBotChatRelayEnabled()) {
            return;
        }

        Component header = Component.text("[Discord] ", NamedTextColor.AQUA)
                .append(Component.text(message.authorName(), NamedTextColor.WHITE))
                .append(Component.text(": ", NamedTextColor.DARK_GRAY));

        // 本文: URL はサイト名のみのクリック可能テキストに / Markdown 装飾も反映
        Component body = FeedTextUtil.toMinecraftComponent(message.content(), NamedTextColor.GRAY);
        // 添付: 画像はタイトルだけ表示し、クリックで Web (Discord CDN) から閲覧できる
        for (net.dv8tion.jda.api.entities.Message.Attachment attachment : message.attachments()) {
            String label = (attachment.isImage() ? "画像: " : "ファイル: ") + attachment.getFileName();
            body = body.append(Component.text(" "))
                    .append(FeedTextUtil.attachmentComponent(label, attachment.getUrl()));
        }
        Component fullMessage = header.append(body);

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(fullMessage);
            }
            Bukkit.getConsoleSender().sendMessage(fullMessage);
        });
    }

    private void mirrorDiscordMessageToWebService(DiscordWebhookDiscordListener.DiscordIncomingMessage message) {
        DiscordWebhookSettings settings = settingsSupplier.get();
        WebServiceModule webService = plugin.getWebServiceModule();
        if (settings == null || !settings.isBotWebServiceIntegrationEnabled() || webService == null || !webService.isDiscordIntegrationEnabled()) {
            return;
        }
        List<WebPost.Attachment> webAttachments = new ArrayList<>();
        for (net.dv8tion.jda.api.entities.Message.Attachment attachment : message.attachments()) {
            WebPost.Attachment webAttachment = new WebPost.Attachment();
            webAttachment.setType(attachment.isImage() ? "image" : "file");
            webAttachment.setUrl(attachment.getUrl());
            webAttachment.setName(attachment.getFileName());
            webAttachment.setWidth(attachment.getWidth());
            webAttachment.setHeight(attachment.getHeight());
            webAttachments.add(webAttachment);
        }
        webService.createDiscordPost(
                message.authorId(),
                message.authorName(),
                message.avatarUrl(),
                message.channelId(),
                message.messageId(),
                message.messageUrl(),
                message.content(),
                webAttachments);
    }

    private String normalizedPlayerName(Player player) {
        String rawName = player.getName();
        if (!FloodgateUtil.isBedrock(player)) {
            return rawName;
        }
        return FloodgateUtil.stripPrefix(rawName).replace("_", " ");
    }

    /**
     * Web 投稿の添付をファイルアップロードに変換する。
     * data URL または自サーバーの /uploads/feed/ に保存された画像に対応。
     * 変換できない場合は null。
     */
    private DiscordWebhookClient.FileUpload loadAttachmentFile(WebPost.Attachment attachment, int imageIndex) {
        String url = attachment.getUrl();
        byte[] bytes = FeedTextUtil.decodeImageDataUrl(url);
        String mime = FeedTextUtil.imageDataUrlMime(url);
        if (bytes == null || mime == null) {
            // 自サーバーに保存されたアップロード画像を読み込む
            int marker = url.indexOf("/uploads/feed/");
            if (marker < 0) {
                return null;
            }
            String fileName = url.substring(marker + "/uploads/feed/".length());
            if (fileName.isBlank() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
                return null;
            }
            java.io.File file = new java.io.File(
                    plugin.getConfigManager().getBaseDir(), "WebService/uploads/feed/" + fileName);
            if (!file.isFile() || file.length() > 8_000_000L) {
                return null;
            }
            try {
                bytes = java.nio.file.Files.readAllBytes(file.toPath());
            } catch (java.io.IOException error) {
                return null;
            }
            String lower = fileName.toLowerCase(java.util.Locale.ROOT);
            mime = lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? "image/jpeg"
                    : lower.endsWith(".gif") ? "image/gif"
                    : lower.endsWith(".webp") ? "image/webp"
                    : "image/png";
        }
        String extension = FeedTextUtil.extensionForMime(mime);
        String fallback = "image" + imageIndex + "." + extension;
        String fileName = FeedTextUtil.sanitizeFileName(attachment.getName(), fallback);
        if (!fileName.toLowerCase(java.util.Locale.ROOT).endsWith("." + extension)) {
            fileName = fileName + "." + extension;
        }
        return new DiscordWebhookClient.FileUpload(fileName, mime, bytes);
    }

    private String displayWebAuthor(WebPost post) {
        if (post.getNickname() != null && !post.getNickname().isBlank()) {
            return post.getNickname();
        }
        if (post.getDisplayName() != null && !post.getDisplayName().isBlank()) {
            return post.getDisplayName();
        }
        return post.getUsername() == null || post.getUsername().isBlank() ? "Web" : post.getUsername();
    }

    private void registerDiscordListener() {
        DiscordBotModule botModule = plugin.getDiscordBotModule();
        if (botModule != null) {
            botModule.registerRuntimeListener(discordListener);
        }
    }
}
