package org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.Module.Bot;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.DiscordWebhookSettings;

import javax.annotation.Nonnull;
import java.util.ArrayList;
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
        if (settings == null || !settings.isEnabled() || !settings.isBotModeEnabled() || !settings.isBotChatRelayEnabled()) {
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

        Member member = event.getMember();
        String authorName = member != null ? member.getEffectiveName() : event.getAuthor().getName();
        String content = event.getMessage().getContentDisplay().trim();
        if (content.isBlank()) {
            List<String> attachmentUrls = new ArrayList<>();
            event.getMessage().getAttachments().forEach(attachment -> attachmentUrls.add(attachment.getUrl()));
            content = String.join(" ", attachmentUrls).trim();
        }
        if (content.isBlank()) {
            return;
        }

        messageConsumer.accept(new DiscordIncomingMessage(authorName, content));
    }

    public record DiscordIncomingMessage(String authorName, String content) {
    }
}
