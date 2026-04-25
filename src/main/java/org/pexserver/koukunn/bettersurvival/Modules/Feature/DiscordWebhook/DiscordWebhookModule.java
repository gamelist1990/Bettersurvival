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
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.DialogUI;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DiscordWebhookModule implements Listener {
    private static final String STATUS_IMAGE_FILE_NAME = "status.png";
    private static final int JOIN_COLOR = 0x57F287;
    private static final int LEAVE_COLOR = 0xED4245;
    private static final int STATUS_COLOR = 0x5865F2;
    private static final int PLAYER_LIST_LIMIT = 15;

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
                .addTextInput("eventWebhookUrl", "Join/Leave Webhook URL", current.getEventWebhookUrl(), 2048, true)
                .addBoolInput("joinEnabled", "Join通知", current.isJoinEnabled())
                .addBoolInput("leaveEnabled", "Leave通知", current.isLeaveEnabled())
                .addTextInput("statusWebhookUrl", "Status/List Webhook URL", current.getStatusWebhookUrl(), 2048, true)
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
        byte[] imageBytes = createStatusImageBytes();
        if (imageBytes.length == 0) {
            sender.sendMessage("§cStatus画像の生成に失敗しました");
            return;
        }
        String messageId = current.getStatusMessageId();
        if (!messageId.isBlank()) {
            client.editMessage(current.getStatusWebhookUrl(), messageId, payload, STATUS_IMAGE_FILE_NAME, imageBytes).thenAccept(updated -> {
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
        byte[] imageBytes = createStatusImageBytes();
        if (imageBytes.length == 0) {
            sender.sendMessage("§cStatus画像の生成に失敗しました");
            return;
        }
        client.sendAndReturnMessageId(webhookUrl, payload, STATUS_IMAGE_FILE_NAME, imageBytes).thenAccept(messageId -> {
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
        embed.addProperty("description", compactPlayerEventText(title, player.getName(), online));
        JsonObject thumbnail = new JsonObject();
        thumbnail.addProperty("url", getPlayerIconUrl(player));
        embed.add("thumbnail", thumbnail);

        JsonObject payload = new JsonObject();
        payload.addProperty("username", getWebhookDisplayName());
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);
        client.send(url, payload);
    }

    private JsonObject createStatusPayload() {
        JsonObject embed = baseEmbed("Status/List", STATUS_COLOR);
        embed.addProperty("description", "Current server status");
        JsonObject image = new JsonObject();
        image.addProperty("url", "attachment://" + STATUS_IMAGE_FILE_NAME);
        embed.add("image", image);

        JsonObject payload = new JsonObject();
        payload.addProperty("username", getWebhookDisplayName());
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

    private String getPlayerIconUrl(Player player) {
        return "https://mc-heads.net/avatar/" + player.getUniqueId() + "/64";
    }

    private String compactPlayerEventText(String title, String playerName, int online) {
        String action = "Join".equalsIgnoreCase(title) ? "joined" : "left";
        return playerName + " " + action + "\nOnline: " + online + "/" + Bukkit.getMaxPlayers();
    }

    private byte[] createStatusImageBytes() {
        try {
            StatusImageRenderer.StatusImageData data = new StatusImageRenderer.StatusImageData(
                    getWebhookDisplayName(),
                    Bukkit.getOnlinePlayers().size(),
                    Bukkit.getMaxPlayers(),
                    sortedOnlinePlayerNames(),
                    minecraftTimeText(),
                    tpsText(),
                    cpuLoadText(),
                    heapText(),
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

    private String cpuLoadText() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean operatingSystem) {
            double load = operatingSystem.getProcessCpuLoad();
            if (load >= 0) {
                return String.format(Locale.ROOT, "%.1f%%", load * 100.0);
            }
        }
        return "unknown";
    }

    private String getWebhookDisplayName() {
        String motd = PlainTextComponentSerializer.plainText().serialize(Bukkit.motd());
        if (motd != null) {
            String singleLine = motd.replace('\n', ' ').trim();
            if (!singleLine.isEmpty() && isAscii(singleLine)) {
                return singleLine;
            }
        }
        String fallback = plugin.getServer().getName();
        if (fallback != null) {
            String trimmed = fallback.trim();
            if (!trimmed.isEmpty() && isAscii(trimmed)) {
                return trimmed;
            }
        }
        return "Minecraft Server";
    }

    private boolean isAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }
}
