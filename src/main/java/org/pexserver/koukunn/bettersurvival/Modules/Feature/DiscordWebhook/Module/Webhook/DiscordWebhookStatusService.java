package org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.Module.Webhook;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.dv8tion.jda.api.JDA;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Core.Util.ServerInfoUtil;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Bot.DiscordBotModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.DiscordWebhookClient;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.DiscordWebhookSettings;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.StatusImageRenderer;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DiscordWebhookStatusService {
    private static final String STATUS_IMAGE_FILE_NAME = "status.png";
    private static final int STATUS_COLOR = 0x5865F2;
    private static final int PLAYER_LIST_LIMIT = 15;
    private static final long STATUS_AUTO_UPDATE_INTERVAL_TICKS = 5L * 60L * 20L;

    private final Loader plugin;
    private final DiscordWebhookClient client;
    private final Supplier<DiscordWebhookSettings> settingsSupplier;
    private final Consumer<String> statusMessageIdSaver;

    private BukkitTask statusAutoUpdateTask;

    public DiscordWebhookStatusService(
            Loader plugin,
            DiscordWebhookClient client,
            Supplier<DiscordWebhookSettings> settingsSupplier,
            Consumer<String> statusMessageIdSaver
    ) {
        this.plugin = plugin;
        this.client = client;
        this.settingsSupplier = settingsSupplier;
        this.statusMessageIdSaver = statusMessageIdSaver;
    }

    public boolean sendStatusNow(Player sender) {
        DiscordWebhookSettings current = settingsSupplier.get();
        if (!current.isEnabled()) {
            sender.sendMessage("§cDiscordWebhookが無効です");
            return false;
        }
        if (!current.isStatusEnabled()) {
            sender.sendMessage("§cStatus/List送信が無効です");
            return false;
        }
        if (!client.isValidUrl(current.getStatusWebhookUrl())) {
            sender.sendMessage("§cStatus/List用Webhook URLが未設定です");
            return false;
        }
        recreateStatusMessage(current, sender);
        sender.sendMessage("§aDiscordのStatus/Listを再投稿します");
        return true;
    }

    public void updateStatusAutoUpdateTask() {
        cancelStatusAutoUpdateTask();
        DiscordWebhookSettings current = settingsSupplier.get();
        if (!current.isEnabled() || !current.isStatusEnabled() || !current.isStatusAutoUpdateEnabled()) return;
        if (!client.isValidUrl(current.getStatusWebhookUrl())) return;
        statusAutoUpdateTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                this::sendScheduledStatus,
                STATUS_AUTO_UPDATE_INTERVAL_TICKS,
                STATUS_AUTO_UPDATE_INTERVAL_TICKS
        );
    }

    public void shutdown() {
        cancelStatusAutoUpdateTask();
    }

    private void cancelStatusAutoUpdateTask() {
        if (statusAutoUpdateTask != null) {
            statusAutoUpdateTask.cancel();
            statusAutoUpdateTask = null;
        }
    }

    private void sendScheduledStatus() {
        DiscordWebhookSettings current = settingsSupplier.get();
        if (!current.isEnabled() || !current.isStatusEnabled() || !current.isStatusAutoUpdateEnabled()) return;
        if (!client.isValidUrl(current.getStatusWebhookUrl())) return;
        recreateStatusMessage(current, null);
    }

    private void recreateStatusMessage(DiscordWebhookSettings current, Player sender) {
        JsonObject payload = createStatusPayload(current);
        String previousMessageId = current.getStatusMessageId();
        if (!previousMessageId.isBlank()) {
            client.deleteMessage(current.getStatusWebhookUrl(), previousMessageId)
                    .whenComplete((deleted, deleteError) -> sendStatusMessage(current, payload, sender));
            return;
        }
        sendStatusMessage(current, payload, sender);
    }

    private void sendStatusMessage(DiscordWebhookSettings current, JsonObject payload, Player sender) {
        if (current.isStatusEmbedEnabled()) {
            sendStatusMessage(current.getStatusWebhookUrl(), payload, sender);
            return;
        }

        byte[] imageBytes = createStatusImageBytes();
        if (imageBytes.length == 0) {
            sendStatusMessage(sender, "§cStatus画像の生成に失敗しました");
            return;
        }
        sendStatusMessage(current.getStatusWebhookUrl(), payload, imageBytes, sender);
    }

    private void sendStatusMessage(String webhookUrl, JsonObject payload, Player sender) {
        client.sendAndReturnMessageId(webhookUrl, payload).thenAccept(messageId -> {
            if (messageId.isBlank()) {
                sendStatusMessageSync(sender, "§cStatus/Listメッセージの再投稿に失敗しました");
                return;
            }
            runSync(() -> {
                statusMessageIdSaver.accept(messageId);
                sendStatusMessage(sender, "§aStatus/Listメッセージを再投稿しました");
            });
        });
    }

    private void sendStatusMessage(String webhookUrl, JsonObject payload, byte[] imageBytes, Player sender) {
        client.sendAndReturnMessageId(webhookUrl, payload, STATUS_IMAGE_FILE_NAME, imageBytes).thenAccept(messageId -> {
            if (messageId.isBlank()) {
                sendStatusMessageSync(sender, "§cStatus/Listメッセージの再投稿に失敗しました");
                return;
            }
            runSync(() -> {
                statusMessageIdSaver.accept(messageId);
                sendStatusMessage(sender, "§aStatus/Listメッセージを再投稿しました");
            });
        });
    }

    private JsonObject createStatusPayload(DiscordWebhookSettings current) {
        JsonObject embed = new JsonObject();
        embed.addProperty("color", STATUS_COLOR);
        embed.addProperty("timestamp", Instant.now().toString());
        if (current.isStatusEmbedEnabled()) {
            embed.addProperty("title", "サーバー状態");
            embed.addProperty("description", "現在のサーバー情報です。");
            addField(embed, "サーバー名", ServerInfoUtil.getServerName(), false);
            addField(embed, "総プレイヤー", Bukkit.getOnlinePlayers().size() + " / " + Bukkit.getMaxPlayers(), true);
            addField(embed, "ワールド時間", minecraftTimeText(), true);
            addField(embed, "TPS", tpsText(), true);
            addField(embed, "CPU", cpuLoadText(), true);
            addField(embed, "メモリ", heapText(), true);
            addField(embed, "Discord Bot", discordBotStatusJapaneseText() + " / " + discordBotPingText(), false);
            addField(embed, "Botモード", botModeJapaneseText(), true);
        } else {
            embed.addProperty("title", "Status/List");
            embed.addProperty("description", "Current server status");
            JsonObject image = new JsonObject();
            image.addProperty("url", "attachment://" + STATUS_IMAGE_FILE_NAME);
            embed.add("image", image);
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("username", ServerInfoUtil.getServerName());
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);
        return payload;
    }

    private byte[] createStatusImageBytes() {
        try {
            StatusImageRenderer.StatusImageData data = new StatusImageRenderer.StatusImageData(
                    ServerInfoUtil.getServerName(),
                    Bukkit.getOnlinePlayers().size(),
                    Bukkit.getMaxPlayers(),
                    sortedOnlinePlayerNames(),
                    minecraftTimeText(),
                    tpsText(),
                    cpuLoadText(),
                    heapText(),
                    discordBotStatusText(),
                    discordBotPingText(),
                    botModeText(),
                    Instant.now());
            return StatusImageRenderer.render(data);
        } catch (Exception e) {
            plugin.getLogger().warning("Status画像の生成に失敗しました: " + e.getMessage());
            return new byte[0];
        }
    }

    private List<String> sortedOnlinePlayerNames() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        if (names.size() > PLAYER_LIST_LIMIT) {
            List<String> clipped = new ArrayList<>(names.subList(0, PLAYER_LIST_LIMIT - 1));
            clipped.add("+" + (names.size() - clipped.size()) + " more");
            return clipped;
        }
        return names;
    }

    private String minecraftTimeText() {
        List<World> worlds = plugin.getServer().getWorlds();
        if (worlds.isEmpty()) return "unknown";
        World world = worlds.get(0);
        long time = world.getTime();
        return time + "T / " + dayPart(time);
    }

    private String dayPart(long time) {
        if (time < 6000) return "Morning";
        if (time < 12000) return "Day";
        return "Night";
    }

    private String tpsText() {
        double[] tps = plugin.getServer().getTPS();
        return tps.length > 0 ? String.format(Locale.ROOT, "%.2f/20.00", Math.min(20.0, tps[0])) : "unknown";
    }

    private String heapText() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        long usedMb = heap.getUsed() / 1024 / 1024;
        long maxMb = heap.getMax() / 1024 / 1024;
        long heapPercent = maxMb > 0 ? Math.round(usedMb * 100.0 / maxMb) : 0;
        return usedMb + "/" + maxMb + "MB (" + heapPercent + "%)";
    }

    private String discordBotStatusText() {
        DiscordBotModule botModule = plugin.getDiscordBotModule();
        if (botModule == null || !botModule.isBotTokenConfigured()) {
            return "NOT CONFIGURED";
        }

        JDA jda = botModule.getJda();
        if (jda == null) {
            return "OFFLINE";
        }

        return switch (jda.getStatus()) {
            case CONNECTED -> "ONLINE";
            case INITIALIZING, LOGGING_IN, AWAITING_LOGIN_CONFIRMATION, LOADING_SUBSYSTEMS -> "STARTING";
            case DISCONNECTED, RECONNECT_QUEUED, WAITING_TO_RECONNECT -> "RECONNECTING";
            case SHUTTING_DOWN, SHUTDOWN -> "OFFLINE";
            case FAILED_TO_LOGIN -> "LOGIN FAILED";
            default -> jda.getStatus().name().replace('_', ' ');
        };
    }

    private String discordBotPingText() {
        DiscordBotModule botModule = plugin.getDiscordBotModule();
        if (botModule == null || !botModule.isBotTokenConfigured()) {
            return "N/A";
        }

        JDA jda = botModule.getJda();
        if (jda == null || jda.getStatus() != JDA.Status.CONNECTED) {
            return "N/A";
        }

        long ping = jda.getGatewayPing();
        return ping >= 0 ? ping + "ms" : "UNKNOWN";
    }

    private String botModeText() {
        DiscordWebhookSettings current = settingsSupplier.get();
        if (current == null) {
            return "DISABLED";
        }
        return current.isBotModeEnabled() ? "ENABLED" : "DISABLED";
    }

    private String botModeJapaneseText() {
        DiscordWebhookSettings current = settingsSupplier.get();
        if (current == null) {
            return "無効";
        }
        return current.isBotModeEnabled() ? "有効" : "無効";
    }

    private String discordBotStatusJapaneseText() {
        DiscordBotModule botModule = plugin.getDiscordBotModule();
        if (botModule == null || !botModule.isBotTokenConfigured()) {
            return "未設定";
        }

        JDA jda = botModule.getJda();
        if (jda == null) {
            return "オフライン";
        }

        return switch (jda.getStatus()) {
            case CONNECTED -> "オンライン";
            case INITIALIZING, LOGGING_IN, AWAITING_LOGIN_CONFIRMATION, LOADING_SUBSYSTEMS -> "起動中";
            case DISCONNECTED, RECONNECT_QUEUED, WAITING_TO_RECONNECT -> "再接続中";
            case SHUTTING_DOWN, SHUTDOWN -> "オフライン";
            case FAILED_TO_LOGIN -> "ログイン失敗";
            default -> jda.getStatus().name().replace('_', ' ');
        };
    }

    private void addField(JsonObject embed, String name, String value, boolean inline) {
        JsonArray fields = embed.has("fields") ? embed.getAsJsonArray("fields") : new JsonArray();
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value == null || value.isBlank() ? "-" : value);
        field.addProperty("inline", inline);
        fields.add(field);
        embed.add("fields", fields);
    }

    private String cpuLoadText() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean operatingSystem) {
            double processLoad = operatingSystem.getProcessCpuLoad();
            if (processLoad >= 0) {
                return String.format(Locale.ROOT, "%.1f%%", processLoad * 100.0);
            }

            double systemLoad = operatingSystem.getCpuLoad();
            if (systemLoad >= 0) {
                return String.format(Locale.ROOT, "%.1f%%", systemLoad * 100.0);
            }
        }

        double averageTickTime = plugin.getServer().getAverageTickTime();
        if (averageTickTime > 0) {
            return String.format(Locale.ROOT, "N/A (%.1fmspt)", averageTickTime);
        }
        return "N/A";
    }

    private void sendStatusMessage(Player sender, String message) {
        if (sender != null && sender.isOnline()) {
            sender.sendMessage(message);
        }
    }

    private void sendStatusMessageSync(Player sender, String message) {
        if (sender != null) {
            runSync(() -> sendStatusMessage(sender, message));
        }
    }

    private void runSync(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }
}
