package org.pexserver.koukunn.bettersurvival.Modules.Feature.YouTube;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** YouTube Live Streaming API のコメントをゲーム内チャットへ中継する。 */
public final class YouTubeLiveChatModule {
    public record OAuthPrompt(String verificationUrl, String userCode, int expiresInSeconds) {}
    private static final Pattern VIDEO_ID = Pattern.compile("(?:youtu\\.be/|youtube(?:-nocookie)?\\.com/(?:watch\\?(?:[^#]*&)?v=|live/|embed/|shorts/))([A-Za-z0-9_-]{11})");
    private final Loader plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final File settingsFile;
    private final YamlConfiguration settings;
    private final Properties bundledOAuth = new Properties();
    private final Set<String> seenIds = ConcurrentHashMap.newKeySet();
    private final Set<String> recentSubscriberIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean polling = new AtomicBoolean();
    private volatile BukkitTask task;
    private volatile BukkitTask loginTask;
    private volatile String videoId;
    private volatile String liveChatId;
    private volatile String pageToken;
    private volatile String channelId;
    private volatile boolean firstPage;
    private volatile Instant startedAt;
    private volatile long nextPollAtMillis;
    private volatile long nextSubscriberPollAtMillis;
    private volatile long subscriberCount = -1L;
    private volatile boolean subscriberBaselineLoaded;
    private final List<BukkitTask> bossBarTasks = new ArrayList<>();

    public YouTubeLiveChatModule(Loader plugin) {
        this.plugin = plugin;
        settingsFile = new File(plugin.getDataFolder(), "youtube.yml");
        loadBundledOAuth();
        settings = YamlConfiguration.loadConfiguration(settingsFile);
        settings.addDefault("api-key", "");
        settings.addDefault("oauth.client-id", "");
        settings.addDefault("oauth.client-secret", "");
        settings.addDefault("oauth.access-token", "");
        settings.addDefault("oauth.refresh-token", "");
        settings.addDefault("oauth.expires-at", 0L);
        settings.addDefault("oauth.channel-id", "");
        settings.addDefault("oauth.channel-title", "");
        settings.addDefault("relay.normal-chat", true);
        settings.addDefault("notifications.super-chat", true);
        settings.addDefault("notifications.super-sticker", true);
        settings.addDefault("notifications.membership", true);
        settings.addDefault("notifications.subscriber-change", true);
        settings.addDefault("bossbar.enabled", true);
        settings.addDefault("bossbar.duration-seconds", 10);
        settings.options().copyDefaults(true);
        saveSettings();
    }

    public synchronized String start(String url) {
        String key = apiKey();
        if (key.isBlank() && !isOAuthLoggedIn()) {
            return "APIキーまたはOAuthログインが必要です。/youtube settings または /youtube login を使用してください";
        }
        Matcher matcher = VIDEO_ID.matcher(url == null ? "" : url);
        if (!matcher.find()) {
            return "YouTube配信URLを認識できません";
        }
        stop();
        videoId = matcher.group(1);
        firstPage = true;
        startedAt = Instant.now();
        seenIds.clear();
        nextPollAtMillis = 0L;
        nextSubscriberPollAtMillis = 0L;
        subscriberCount = -1L;
        subscriberBaselineLoaded = false;
        recentSubscriberIds.clear();
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::pollSafely, 0L, 20L);
        return null;
    }

    public synchronized boolean stop() {
        boolean active = task != null;
        if (task != null) {
            task.cancel();
            task = null;
        }
        videoId = null;
        liveChatId = null;
        pageToken = null;
        channelId = null;
        subscriberCount = -1L;
        seenIds.clear();
        synchronized (bossBarTasks) {
            bossBarTasks.forEach(BukkitTask::cancel);
            bossBarTasks.clear();
        }
        return active;
    }

    public synchronized void shutdown() {
        stop();
        if (loginTask != null) {
            loginTask.cancel();
            loginTask = null;
        }
    }

    public synchronized void setApiKey(String key) {
        settings.set("api-key", key == null ? "" : key.trim());
        saveSettings();
    }

    public boolean isOAuthConfigured() {
        return !oauthClientId().isBlank();
    }

    public boolean hasApiKey() {
        return !apiKey().isBlank();
    }

    public void onMain(Runnable action) {
        Bukkit.getScheduler().runTask(plugin, action);
    }

    public boolean isOAuthLoggedIn() {
        return !settings.getString("oauth.refresh-token", "").isBlank();
    }

    public String oauthChannelDisplay() {
        String title = settings.getString("oauth.channel-title", "");
        String id = settings.getString("oauth.channel-id", "");
        if (!title.isBlank()) return title + (id.isBlank() ? "" : " (" + id + ")");
        return id.isBlank() ? "未確認" : id;
    }

    public synchronized void setOAuthClient(String clientId, String clientSecret) {
        if (clientId != null && !clientId.isBlank()) settings.set("oauth.client-id", clientId.trim());
        if (clientSecret != null && !clientSecret.isBlank()) settings.set("oauth.client-secret", clientSecret.trim());
        saveSettings();
    }

    public CompletableFuture<OAuthPrompt> beginOAuthLogin(UUID requester) {
        String clientId = oauthClientId();
        if (clientId.isBlank()) return CompletableFuture.failedFuture(new IllegalStateException(
                "このビルドにOAuth Client IDが組み込まれていません。docs/YOUTUBE_OAUTH_SETUP.mdを参照してください"));
        if (loginTask != null) return CompletableFuture.failedFuture(new IllegalStateException("OAuthログイン処理が既に進行中です"));
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject response = postForm("https://oauth2.googleapis.com/device/code",
                        "client_id=" + encode(clientId) + "&scope=" + encode("https://www.googleapis.com/auth/youtube.readonly"));
                String deviceCode = response.get("device_code").getAsString();
                String userCode = response.get("user_code").getAsString();
                String verificationUrl = response.has("verification_url") ? response.get("verification_url").getAsString()
                        : response.get("verification_uri").getAsString();
                int expires = response.get("expires_in").getAsInt();
                int interval = response.has("interval") ? response.get("interval").getAsInt() : 5;
                startLoginPolling(requester, deviceCode, expires, interval);
                return new OAuthPrompt(verificationUrl, userCode, expires);
            } catch (Exception exception) {
                throw new java.util.concurrent.CompletionException(exception);
            }
        });
    }

    public synchronized void logoutOAuth() {
        if (loginTask != null) {
            loginTask.cancel();
            loginTask = null;
        }
        settings.set("oauth.access-token", "");
        settings.set("oauth.refresh-token", "");
        settings.set("oauth.expires-at", 0L);
        settings.set("oauth.channel-id", "");
        settings.set("oauth.channel-title", "");
        saveSettings();
    }

    private void startLoginPolling(UUID requester, String deviceCode, int expiresInSeconds, int intervalSeconds) {
        long expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000L;
        long[] intervalMillis = {Math.max(5, intervalSeconds) * 1000L};
        long[] nextAttempt = {System.currentTimeMillis() + intervalMillis[0]};
        loginTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (System.currentTimeMillis() < nextAttempt[0]) return;
            nextAttempt[0] = System.currentTimeMillis() + intervalMillis[0];
            if (System.currentTimeMillis() >= expiresAt) {
                finishLogin(requester, "§cYouTube OAuth認証の有効時間が切れました");
                return;
            }
            try {
                String body = "client_id=" + encode(oauthClientId())
                        + optionalClientSecret()
                        + "&device_code=" + encode(deviceCode)
                        + "&grant_type=" + encode("urn:ietf:params:oauth:grant-type:device_code");
                JsonObject token = postFormAllowError("https://oauth2.googleapis.com/token", body);
                if (token.has("error")) {
                    String error = token.get("error").getAsString();
                    if (error.equals("authorization_pending")) return;
                    if (error.equals("slow_down")) {
                        intervalMillis[0] += 5000L;
                        return;
                    }
                    finishLogin(requester, "§cYouTube OAuth認証に失敗しました: " + error);
                    return;
                }
                storeTokens(token);
                loadAuthenticatedChannel();
                finishLogin(requester, "§aYouTube OAuthログイン完了: §f" + oauthChannelDisplay());
            } catch (Exception exception) {
                plugin.getLogger().warning("YouTube OAuth確認失敗: " + exception.getMessage());
            }
        }, 20L, 20L);
    }

    private synchronized void finishLogin(UUID requester, String message) {
        if (loginTask != null) {
            loginTask.cancel();
            loginTask = null;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(requester);
            if (player != null) player.sendMessage(message);
            plugin.getLogger().info(message.replace('§', ' '));
        });
    }

    private synchronized void storeTokens(JsonObject token) {
        settings.set("oauth.access-token", token.get("access_token").getAsString());
        if (token.has("refresh_token")) settings.set("oauth.refresh-token", token.get("refresh_token").getAsString());
        settings.set("oauth.expires-at", System.currentTimeMillis() + Math.max(60, token.get("expires_in").getAsLong() - 60) * 1000L);
        saveSettings();
    }

    private void loadAuthenticatedChannel() throws IOException, InterruptedException {
        JsonObject root = getJson("https://www.googleapis.com/youtube/v3/channels?part=snippet&mine=true");
        JsonArray items = root.getAsJsonArray("items");
        if (items == null || items.isEmpty()) return;
        JsonObject item = items.get(0).getAsJsonObject();
        settings.set("oauth.channel-id", item.get("id").getAsString());
        settings.set("oauth.channel-title", item.getAsJsonObject("snippet").get("title").getAsString());
        saveSettings();
    }

    public boolean setting(String path, boolean fallback) {
        return settings.getBoolean(path, fallback);
    }

    public int setting(String path, int fallback) {
        return settings.getInt(path, fallback);
    }

    public synchronized void saveUiSettings(String apiKey, String clientId, String clientSecret, boolean normalChat, boolean superChat,
            boolean superSticker, boolean membership, boolean subscriberChange, boolean bossBar, int durationSeconds) {
        if (apiKey != null && !apiKey.isBlank()) settings.set("api-key", apiKey.trim());
        setOAuthClient(clientId, clientSecret);
        settings.set("relay.normal-chat", normalChat);
        settings.set("notifications.super-chat", superChat);
        settings.set("notifications.super-sticker", superSticker);
        settings.set("notifications.membership", membership);
        settings.set("notifications.subscriber-change", subscriberChange);
        settings.set("bossbar.enabled", bossBar);
        settings.set("bossbar.duration-seconds", Math.max(3, Math.min(30, durationSeconds)));
        saveSettings();
    }

    public boolean isActive() {
        return task != null;
    }

    public String getVideoId() {
        return videoId;
    }

    private void pollSafely() {
        if (System.currentTimeMillis() < nextPollAtMillis) {
            return;
        }
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        try {
            if (liveChatId == null) {
                resolveLiveChat();
            } else {
                pollMessages();
                pollSubscribersIfDue();
            }
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
            if (message.contains("liveChatEnded") || message.contains("liveChatDisabled")) {
                fail(message.contains("liveChatEnded") ? "配信が終了したためコメント連携を停止しました" : "この配信ではライブチャットが無効です");
            } else {
                plugin.getLogger().warning("YouTubeコメント取得失敗: " + message);
                nextPollAtMillis = System.currentTimeMillis() + 10_000L;
            }
        } finally {
            polling.set(false);
        }
    }

    private void resolveLiveChat() throws IOException, InterruptedException {
        JsonObject root = getJson("https://www.googleapis.com/youtube/v3/videos?part=snippet,liveStreamingDetails&id="
                + encode(videoId) + apiKeyQuery());
        JsonArray items = root.getAsJsonArray("items");
        if (items == null || items.isEmpty()) {
            fail("動画が見つかりませんでした");
            return;
        }
        JsonObject video = items.get(0).getAsJsonObject();
        JsonObject live = video.getAsJsonObject("liveStreamingDetails");
        if (live == null || !live.has("activeLiveChatId")) {
            fail("この動画はコメント取得可能なライブ配信ではありません");
            return;
        }
        liveChatId = live.get("activeLiveChatId").getAsString();
        JsonObject snippet = video.getAsJsonObject("snippet");
        channelId = snippet.get("channelId").getAsString();
        String title = snippet.get("title").getAsString();
        broadcast(Component.text("[YouTube] ", NamedTextColor.RED)
                .append(Component.text("コメント連携を開始: " + title, NamedTextColor.YELLOW)));
        if (isOAuthLoggedIn() && !oauthMatchesStream()) {
            broadcast(Component.text("[YouTube] OAuthでログインしたチャンネルと配信チャンネルが異なるため、名前付き登録通知は無効です", NamedTextColor.RED));
        }
    }

    private void pollMessages() throws IOException, InterruptedException {
        String endpoint = "https://www.googleapis.com/youtube/v3/liveChat/messages?part=id,snippet,authorDetails&maxResults=200&liveChatId="
                + encode(liveChatId) + apiKeyQuery()
                + (pageToken == null ? "" : "&pageToken=" + encode(pageToken));
        JsonObject root = getJson(endpoint);
        JsonArray items = root.getAsJsonArray("items");
        if (items != null) {
            for (var element : items) {
                JsonObject item = element.getAsJsonObject();
                String id = item.get("id").getAsString();
                if (!seenIds.add(id)) {
                    continue;
                }
                JsonObject snippet = item.getAsJsonObject("snippet");
                if (firstPage && snippet.has("publishedAt")
                        && Instant.parse(snippet.get("publishedAt").getAsString()).isBefore(startedAt)) {
                    continue;
                }
                String author = item.getAsJsonObject("authorDetails").get("displayName").getAsString();
                handleMessage(snippet, author);
            }
        }
        firstPage = false;
        long interval = root.has("pollingIntervalMillis") ? root.get("pollingIntervalMillis").getAsLong() : 5000L;
        nextPollAtMillis = System.currentTimeMillis() + Math.max(1000L, interval);
        if (root.has("nextPageToken")) {
            pageToken = root.get("nextPageToken").getAsString();
        }
        if (root.has("offlineAt")) {
            fail("配信が終了したためコメント連携を停止しました");
        }
        if (seenIds.size() > 5000) {
            seenIds.clear();
        }
    }

    private void handleMessage(JsonObject snippet, String author) {
        String type = snippet.has("type") ? snippet.get("type").getAsString() : "textMessageEvent";
        String display = snippet.has("displayMessage") ? snippet.get("displayMessage").getAsString() : "";
        switch (type) {
            case "superChatEvent" -> {
                if (!setting("notifications.super-chat", true)) return;
                JsonObject details = snippet.getAsJsonObject("superChatDetails");
                String amount = details != null && details.has("amountDisplayString") ? details.get("amountDisplayString").getAsString() : "Super Chat";
                announcePremium("スーパーチャット", author, amount, display, BarColor.YELLOW);
            }
            case "superStickerEvent" -> {
                if (!setting("notifications.super-sticker", true)) return;
                JsonObject details = snippet.getAsJsonObject("superStickerDetails");
                String amount = details != null && details.has("amountDisplayString") ? details.get("amountDisplayString").getAsString() : "Super Sticker";
                String sticker = details != null && details.has("superStickerMetadata")
                        && details.getAsJsonObject("superStickerMetadata").has("altText")
                        ? details.getAsJsonObject("superStickerMetadata").get("altText").getAsString() : display;
                announcePremium("スーパーステッカー", author, amount, sticker, BarColor.PINK);
            }
            case "newSponsorEvent", "memberMilestoneChatEvent", "membershipGiftingEvent", "giftMembershipReceivedEvent" -> {
                if (!setting("notifications.membership", true)) return;
                announcePremium("メンバーシップ", author, "", display, BarColor.GREEN);
            }
            case "textMessageEvent" -> {
                if (!setting("relay.normal-chat", true)) return;
                broadcast(Component.text("[YouTube] ", NamedTextColor.RED)
                        .append(Component.text(author, NamedTextColor.GOLD))
                        .append(Component.text(": ", NamedTextColor.GRAY))
                        .append(Component.text(display, NamedTextColor.WHITE)));
            }
            default -> {
            }
        }
    }

    private void announcePremium(String kind, String author, String amount, String message, BarColor color) {
        String title = "[YouTube] " + kind + " - " + author + (amount.isBlank() ? "" : " " + amount);
        broadcast(Component.text(title, NamedTextColor.GOLD)
                .append(message.isBlank() ? Component.empty() : Component.text(": " + message, NamedTextColor.WHITE)));
        if (setting("bossbar.enabled", true)) showTimedBossBar(title, color);
    }

    private void showTimedBossBar(String title, BarColor color) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            BossBar bar = Bukkit.createBossBar(title, color, BarStyle.SOLID);
            Bukkit.getOnlinePlayers().forEach(bar::addPlayer);
            int totalTicks = Math.max(60, Math.min(600, setting("bossbar.duration-seconds", 10) * 20));
            final int[] elapsed = {0};
            BukkitTask timer = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                elapsed[0]++;
                double progress = Math.max(0.0D, 1.0D - (double) elapsed[0] / totalTicks);
                bar.setProgress(progress);
                if (elapsed[0] >= totalTicks) {
                    bar.removeAll();
                    synchronized (bossBarTasks) {
                        bossBarTasks.removeIf(BukkitTask::isCancelled);
                    }
                }
            }, 1L, 1L);
            synchronized (bossBarTasks) {
                bossBarTasks.add(timer);
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                timer.cancel();
                bar.removeAll();
                synchronized (bossBarTasks) {
                    bossBarTasks.remove(timer);
                }
            }, totalTicks + 1L);
        });
    }

    private void pollSubscribersIfDue() throws IOException, InterruptedException {
        if (!setting("notifications.subscriber-change", true) || channelId == null
                || System.currentTimeMillis() < nextSubscriberPollAtMillis) return;
        nextSubscriberPollAtMillis = System.currentTimeMillis() + 60_000L;
        int namedSubscribers = isOAuthLoggedIn() && oauthMatchesStream() ? pollNamedSubscribers() : 0;
        JsonObject root = getJson("https://www.googleapis.com/youtube/v3/channels?part=statistics&id="
                + encode(channelId) + apiKeyQuery());
        JsonArray items = root.getAsJsonArray("items");
        if (items == null || items.isEmpty()) return;
        JsonObject statistics = items.get(0).getAsJsonObject().getAsJsonObject("statistics");
        if (statistics == null || !statistics.has("subscriberCount")
                || (statistics.has("hiddenSubscriberCount") && statistics.get("hiddenSubscriberCount").getAsBoolean())) return;
        long current = statistics.get("subscriberCount").getAsLong();
        long previous = subscriberCount;
        subscriberCount = current;
        if (previous < 0 || previous == current) return;
        long delta = current - previous;
        if (delta > 0 && namedSubscribers > 0) return;
        String title = delta > 0 ? "チャンネル登録者が " + delta + " 人増えました" : "チャンネル登録者が " + -delta + " 人減りました";
        broadcast(Component.text("[YouTube] " + title + "（現在 " + current + " 人）",
                delta > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        if (setting("bossbar.enabled", true)) showTimedBossBar(title, delta > 0 ? BarColor.GREEN : BarColor.WHITE);
    }

    private int pollNamedSubscribers() throws IOException, InterruptedException {
        JsonObject root = getJson("https://www.googleapis.com/youtube/v3/subscriptions?part=snippet,subscriberSnippet&myRecentSubscribers=true&maxResults=50");
        JsonArray items = root.getAsJsonArray("items");
        if (items == null) return 0;
        List<JsonObject> newlyVisible = new ArrayList<>();
        for (var element : items) {
            JsonObject subscription = element.getAsJsonObject();
            String id = subscription.get("id").getAsString();
            if (!recentSubscriberIds.contains(id)) newlyVisible.add(subscription);
        }
        if (subscriberBaselineLoaded) {
            for (int index = newlyVisible.size() - 1; index >= 0; index--) {
                JsonObject subscription = newlyVisible.get(index);
                JsonObject subscriber = subscription.getAsJsonObject("subscriberSnippet");
                if (subscriber == null || !subscriber.has("title")) continue;
                String name = subscriber.get("title").getAsString();
                String title = name + " さんがチャンネル登録しました！";
                broadcast(Component.text("[YouTube] " + title, NamedTextColor.GREEN));
                if (setting("bossbar.enabled", true)) showTimedBossBar(title, BarColor.GREEN);
            }
        }
        recentSubscriberIds.clear();
        for (var element : items) recentSubscriberIds.add(element.getAsJsonObject().get("id").getAsString());
        subscriberBaselineLoaded = true;
        return newlyVisible.size();
    }

    private JsonObject getJson(String url) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET();
        if (isOAuthLoggedIn()) builder.header("Authorization", "Bearer " + validAccessToken());
        HttpRequest request = builder.build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body = response.body();
            throw new IOException("YouTube API HTTP " + response.statusCode() + ": "
                    + body.substring(0, Math.min(body.length(), 500)));
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private synchronized String validAccessToken() throws IOException, InterruptedException {
        String accessToken = settings.getString("oauth.access-token", "");
        if (!accessToken.isBlank() && settings.getLong("oauth.expires-at", 0L) > System.currentTimeMillis()) return accessToken;
        String refreshToken = settings.getString("oauth.refresh-token", "");
        if (refreshToken.isBlank()) throw new IOException("YouTube OAuth refresh tokenがありません");
        String body = "client_id=" + encode(oauthClientId())
                + optionalClientSecret()
                + "&refresh_token=" + encode(refreshToken)
                + "&grant_type=refresh_token";
        JsonObject token = postForm("https://oauth2.googleapis.com/token", body);
        storeTokens(token);
        return settings.getString("oauth.access-token", "");
    }

    private JsonObject postForm(String url, String body) throws IOException, InterruptedException {
        JsonObject response = postFormAllowError(url, body);
        if (response.has("error")) throw new IOException(response.get("error").getAsString());
        return response;
    }

    private JsonObject postFormAllowError(String url, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private String optionalClientSecret() {
        String secret = settings.getString("oauth.client-secret", "").trim();
        if (secret.isBlank()) secret = bundledOAuth.getProperty("client-secret", "").trim();
        return secret.isBlank() ? "" : "&client_secret=" + encode(secret);
    }

    private String oauthClientId() {
        String configured = settings.getString("oauth.client-id", "").trim();
        return configured.isBlank() ? bundledOAuth.getProperty("client-id", "").trim() : configured;
    }

    private void loadBundledOAuth() {
        try (InputStream input = plugin.getResource("youtube-oauth.properties")) {
            if (input != null) bundledOAuth.load(input);
        } catch (IOException exception) {
            plugin.getLogger().warning("組み込みYouTube OAuth設定を読み込めません: " + exception.getMessage());
        }
    }

    private String apiKeyQuery() {
        return isOAuthLoggedIn() ? "" : "&key=" + encode(apiKey());
    }

    private boolean oauthMatchesStream() {
        String authenticatedChannel = settings.getString("oauth.channel-id", "");
        return !authenticatedChannel.isBlank() && authenticatedChannel.equals(channelId);
    }

    private void fail(String message) {
        stop();
        broadcast(Component.text("[YouTube] " + message, NamedTextColor.RED));
    }

    private void broadcast(Component message) {
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.broadcast(message));
    }

    private String apiKey() {
        String environment = System.getenv("YOUTUBE_API_KEY");
        return environment == null || environment.isBlank() ? settings.getString("api-key", "").trim() : environment.trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private void saveSettings() {
        try {
            settings.save(settingsFile);
        } catch (IOException exception) {
            plugin.getLogger().warning("youtube.yml の保存に失敗: " + exception.getMessage());
        }
    }
}
