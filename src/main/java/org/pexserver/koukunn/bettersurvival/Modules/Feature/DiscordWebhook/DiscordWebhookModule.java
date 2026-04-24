package org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.DialogUI;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DiscordWebhookModule implements Listener {
    private static final int JOIN_COLOR = 0x57F287;
    private static final int LEAVE_COLOR = 0xED4245;
    private static final int STATUS_COLOR = 0x5865F2;
    private static final int PLAYER_LIST_LIMIT = 10;

    private final Loader plugin;
    private final DiscordWebhookStore store;
    private final DiscordWebhookClient client;
    private DiscordWebhookSettings settings;

    public DiscordWebhookModule(Loader plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.store = new DiscordWebhookStore(configManager);
        this.client = new DiscordWebhookClient(plugin);
        this.settings = store.load();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        DiscordWebhookSettings current = getSettings();
        if (!current.isEnabled() || !current.isJoinEnabled()) return;
        if (!client.isValidUrl(current.getEventWebhookUrl())) return;
        sendPlayerEvent(current.getEventWebhookUrl(), "Join", event.getPlayer(), Bukkit.getOnlinePlayers().size(), JOIN_COLOR);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        DiscordWebhookSettings current = getSettings();
        if (!current.isEnabled() || !current.isLeaveEnabled()) return;
        if (!client.isValidUrl(current.getEventWebhookUrl())) return;
        int online = Math.max(0, Bukkit.getOnlinePlayers().size() - 1);
        sendPlayerEvent(current.getEventWebhookUrl(), "Leave", event.getPlayer(), online, LEAVE_COLOR);
    }

    public synchronized DiscordWebhookSettings getSettings() {
        return settings;
    }

    public synchronized boolean saveSettings(DiscordWebhookSettings settings) {
        boolean saved = store.save(settings);
        if (saved) {
            this.settings = settings;
        }
        return saved;
    }

    public boolean sendStatusNow(Player sender) {
        DiscordWebhookSettings current = getSettings();
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
        sendOrUpdateStatus(current, sender);
        sender.sendMessage("§aDiscordのStatus/Listを更新します");
        return true;
    }

    public void openMenu(Player player) {
        DiscordWebhookSettings current = getSettings();
        DialogUI.builder()
                .title("DiscordWebhook")
                .body("状態: " + (current.isEnabled() ? "有効" : "無効"))
                .body("Join: " + enabledText(current.isJoinEnabled()))
                .body("Leave: " + enabledText(current.isLeaveEnabled()))
                .body("Status/List: " + enabledText(current.isStatusEnabled()))
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
        DialogUI.builder()
                .title("DiscordWebhook設定")
                .body("Join/Leave用URLとStatus/List用URLは別々に設定できます")
                .addBoolInput("enabled", "DiscordWebhookを有効にする", current.isEnabled())
                .addTextInput("eventWebhookUrl", "Join/Leave Webhook URL", current.getEventWebhookUrl(), 2048, false)
                .addBoolInput("joinEnabled", "Join通知", current.isJoinEnabled())
                .addBoolInput("leaveEnabled", "Leave通知", current.isLeaveEnabled())
                .addTextInput("statusWebhookUrl", "Status/List Webhook URL", current.getStatusWebhookUrl(), 2048, false)
                .addBoolInput("statusEnabled", "Status/List送信", current.isStatusEnabled())
                .confirmation("保存", "キャンセル")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) {
                        openMenu(p);
                        return;
                    }
                    DiscordWebhookSettings updated = new DiscordWebhookSettings();
                    updated.setEnabled(result.getBool("enabled"));
                    updated.setEventWebhookUrl(result.getText("eventWebhookUrl"));
                    updated.setJoinEnabled(result.getBool("joinEnabled"));
                    updated.setLeaveEnabled(result.getBool("leaveEnabled"));
                    updated.setStatusWebhookUrl(result.getText("statusWebhookUrl"));
                    updated.setStatusEnabled(result.getBool("statusEnabled"));
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

    private void sendOrUpdateStatus(DiscordWebhookSettings current, Player sender) {
        JsonObject payload = createStatusPayload();
        String messageId = current.getStatusMessageId();
        if (!messageId.isBlank()) {
            client.editMessage(current.getStatusWebhookUrl(), messageId, payload).thenAccept(updated -> {
                if (updated) {
                    runSync(() -> sender.sendMessage("§a既存のStatus/Listメッセージを更新しました"));
                    return;
                }
                createStatusMessage(current.getStatusWebhookUrl(), payload, sender);
            });
            return;
        }
        createStatusMessage(current.getStatusWebhookUrl(), payload, sender);
    }

    private void createStatusMessage(String webhookUrl, JsonObject payload, Player sender) {
        client.sendAndReturnMessageId(webhookUrl, payload).thenAccept(messageId -> {
            if (messageId.isBlank()) {
                runSync(() -> sender.sendMessage("§cStatus/Listメッセージの作成に失敗しました"));
                return;
            }
            runSync(() -> {
                saveStatusMessageId(messageId);
                sender.sendMessage("§aStatus/Listメッセージを作成し、次回以降は更新するようにしました");
            });
        });
    }

    private synchronized void saveStatusMessageId(String messageId) {
        settings.setStatusMessageId(messageId);
        store.save(settings);
    }

    private void runSync(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    private void sendPlayerEvent(String url, String title, Player player, int online, int color) {
        JsonObject embed = baseEmbed(title, color);
        embed.addProperty("description", player.getName());
        JsonObject thumbnail = new JsonObject();
        thumbnail.addProperty("url", getPlayerIconUrl(player));
        embed.add("thumbnail", thumbnail);
        embed.add("fields", fields(
                field("Player", player.getName(), true),
                field("Players", online + "/" + Bukkit.getMaxPlayers(), true)
        ));

        JsonObject payload = new JsonObject();
        payload.addProperty("username", "PEXServer");
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);
        client.send(url, payload);
    }

    private JsonObject createStatusPayload() {
        JsonObject embed = baseEmbed("Status/List", STATUS_COLOR);
        embed.add("fields", fields(
                field("Players", Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers(), true),
                field("Online Players", onlinePlayerList(), false),
                field("Minecraft Time", minecraftTimeText(), true),
                field("Server Load", serverLoadText(), false)
        ));

        JsonObject payload = new JsonObject();
        payload.addProperty("username", "PEXServer");
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);
        return payload;
    }

    private JsonObject baseEmbed(String title, int color) {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("color", color);
        embed.addProperty("timestamp", Instant.now().toString());
        return embed;
    }

    private JsonArray fields(JsonObject... values) {
        JsonArray fields = new JsonArray();
        for (JsonObject value : values) {
            fields.add(value);
        }
        return fields;
    }

    private JsonObject field(String name, String value, boolean inline) {
        JsonObject field = new JsonObject();
        field.addProperty("name", name);
        field.addProperty("value", value == null || value.isBlank() ? "-" : value);
        field.addProperty("inline", inline);
        return field;
    }

    private String getPlayerIconUrl(Player player) {
        return "https://mc-heads.net/avatar/" + player.getUniqueId() + "/64";
    }

    private String onlinePlayerList() {
        List<String> names = new ArrayList<>();
        int index = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (index >= PLAYER_LIST_LIMIT) break;
            names.add(player.getName());
            index++;
        }
        if (names.isEmpty()) return "なし";
        if (Bukkit.getOnlinePlayers().size() > PLAYER_LIST_LIMIT) {
            names.add("...");
        }
        return String.join(", ", names);
    }

    private String minecraftTimeText() {
        List<World> worlds = plugin.getServer().getWorlds();
        if (worlds.isEmpty()) return "unknown";
        World world = worlds.get(0);
        long time = world.getTime();
        return world.getName() + " " + time + "tick / " + dayPart(time);
    }

    private String dayPart(long time) {
        if (time < 6000) return "朝";
        if (time < 12000) return "昼";
        return "夜";
    }

    private String serverLoadText() {
        double[] tps = plugin.getServer().getTPS();
        String tpsText = tps.length > 0 ? String.format("%.2f/20.00", Math.min(20.0, tps[0])) : "unknown";
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        long usedMb = heap.getUsed() / 1024 / 1024;
        long maxMb = heap.getMax() / 1024 / 1024;
        long heapPercent = maxMb > 0 ? Math.round(usedMb * 100.0 / maxMb) : 0;
        String cpuText = cpuLoadText();
        return "TPS: " + tpsText + "\nCPU: " + cpuText + "\nHeap: " + usedMb + "/" + maxMb + "MB (" + heapPercent + "%)";
    }

    private String cpuLoadText() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean operatingSystem) {
            double load = operatingSystem.getProcessCpuLoad();
            if (load >= 0) {
                return String.format("%.1f%%", load * 100.0);
            }
        }
        return "unknown";
    }
}
