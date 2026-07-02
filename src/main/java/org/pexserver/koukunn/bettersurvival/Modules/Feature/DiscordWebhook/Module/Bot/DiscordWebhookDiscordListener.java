package org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.Module.Bot;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.DiscordWebhookSettings;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DiscordWebhookDiscordListener extends ListenerAdapter {
    private final Supplier<DiscordWebhookSettings> settingsSupplier;
    private final Consumer<DiscordIncomingMessage> messageConsumer;

    public DiscordWebhookDiscordListener(
            Supplier<DiscordWebhookSettings> settingsSupplier,
            Consumer<DiscordIncomingMessage> messageConsumer
    ) {
        this.settingsSupplier = settingsSupplier;
        this.messageConsumer = messageConsumer;
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        DiscordWebhookSettings settings = settingsSupplier.get();
        if (settings == null || !settings.isEnabled() || !settings.isBotModeEnabled() || (!settings.isBotChatRelayEnabled() && !settings.isBotWebServiceIntegrationEnabled())) {
            return;
        }
        if (settings.getBotChannelId().isBlank() || !settings.getBotChannelId().equals(event.getChannel().getId())) {
            return;
        }
        if (!event.isFromGuild() || event.isWebhookMessage()) {
            return;
        }
        if (event.getAuthor().isBot() || event.getAuthor().getIdLong() == event.getJDA().getSelfUser().getIdLong()) {
            return;
        }

        // 更新: メッセージ内容と添付ファイルを両方処理するように変更
        Member member = event.getMember();
        String authorName = member != null ? member.getEffectiveName() : event.getAuthor().getGlobalName() != null ? event.getAuthor().getGlobalName() : event.getAuthor().getName();
        String avatarUrl = event.getAuthor().getEffectiveAvatarUrl();
        String content = event.getMessage().getContentRaw().trim();
        List<Message.Attachment> attachments = event.getMessage().getAttachments();
        if (content.isBlank() && attachments.isEmpty()) {
            return;
        }
        String replyToMessageId = event.getMessage().getReferencedMessage() == null ? "" : event.getMessage().getReferencedMessage().getId();
        if (attachments.isEmpty()) {
            messageConsumer.accept(new DiscordIncomingMessage(
                    authorName,
                    event.getAuthor().getId(),
                    avatarUrl,
                    event.getChannel().getId(),
                    event.getMessageId(),
                    event.getMessage().getJumpUrl(),
                    replyToMessageId,
                    content,
                    attachments));
            return;
        }
        StringBuilder attachmentText = new StringBuilder();
        if (!content.isBlank()) {
            attachmentText.append(content).append(' ');
        }
        for (Message.Attachment attachment : attachments) {
            attachmentText.append('[')
                    .append(attachment.getFileName())
                    .append("](")
                    .append(attachment.getUrl())
                    .append(") ");
        }
        messageConsumer.accept(new DiscordIncomingMessage(
                authorName,
                event.getAuthor().getId(),
                avatarUrl,
                event.getChannel().getId(),
                event.getMessageId(),
                event.getMessage().getJumpUrl(),
                replyToMessageId,
                attachmentText.toString().trim(),
                attachments));
    }

    public record DiscordIncomingMessage(String authorName, String authorId, String avatarUrl, String channelId, String messageId, String messageUrl, String replyToMessageId, String content, List<Message.Attachment> attachments) {
    }
}
