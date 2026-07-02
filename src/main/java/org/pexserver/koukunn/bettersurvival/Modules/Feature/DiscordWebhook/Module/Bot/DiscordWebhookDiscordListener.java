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

        // 本文と添付は分けて渡す（表示側で「画像: 名前」形式のリンクに整形される）
        Member member = event.getMember();
        String authorName = member != null ? member.getEffectiveName() : event.getAuthor().getGlobalName() != null ? event.getAuthor().getGlobalName() : event.getAuthor().getName();
        String avatarUrl = event.getAuthor().getEffectiveAvatarUrl();
        String content = event.getMessage().getContentRaw().trim();
        List<Message.Attachment> attachments = event.getMessage().getAttachments();
        if (content.isBlank() && attachments.isEmpty()) {
            return;
        }
        messageConsumer.accept(new DiscordIncomingMessage(
                authorName,
                event.getAuthor().getId(),
                avatarUrl,
                event.getChannel().getId(),
                event.getMessageId(),
                event.getMessage().getJumpUrl(),
                content,
                attachments));
    }

    public record DiscordIncomingMessage(String authorName, String authorId, String avatarUrl, String channelId, String messageId, String messageUrl, String content, List<Message.Attachment> attachments) {
    }
}
