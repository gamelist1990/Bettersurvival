package org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.Module.Webhook;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;
import org.pexserver.koukunn.bettersurvival.Core.Util.ServerInfoUtil;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Api.McApiClient;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.DiscordWebhookClient;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.DiscordWebhookSettings;

import java.time.Instant;

public class DiscordWebhookEventService {
    private static final int JOIN_COLOR = 0x57F287;
    private static final int LEAVE_COLOR = 0xED4245;

    private final DiscordWebhookClient client;

    public DiscordWebhookEventService(DiscordWebhookClient client) {
        this.client = client;
    }

    public void sendJoin(DiscordWebhookSettings settings, Player player) {
        sendPlayerEvent(settings.getEventWebhookUrl(), "Join", player, Bukkit.getOnlinePlayers().size(), JOIN_COLOR);
    }

    public void sendLeave(DiscordWebhookSettings settings, Player player) {
        int online = Math.max(0, Bukkit.getOnlinePlayers().size() - 1);
        sendPlayerEvent(settings.getEventWebhookUrl(), "Leave", player, online, LEAVE_COLOR);
    }

    private void sendPlayerEvent(String url, String title, Player player, int online, int color) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("color", color);
        embed.addProperty("timestamp", Instant.now().toString());
        embed.addProperty("description", compactPlayerEventText(title, player.getName(), online));

        JsonObject thumbnail = new JsonObject();
        thumbnail.addProperty("url", getPlayerIconUrl(player));
        embed.add("thumbnail", thumbnail);

        JsonObject payload = new JsonObject();
        payload.addProperty("username", ServerInfoUtil.getServerName());
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);
        client.send(url, payload);
    }

    private String getPlayerIconUrl(Player player) {
        return McApiClient.getFaceUrl(player.getUniqueId(), player.getName(), FloodgateUtil.isBedrock(player));
    }

    private String compactPlayerEventText(String title, String playerName, int online) {
        String action = "Join".equalsIgnoreCase(title) ? "joined" : "left";
        return playerName + " " + action + "\nOnline: " + online + "/" + Bukkit.getMaxPlayers();
    }
}
