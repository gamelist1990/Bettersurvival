package org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * mc-api.io を使用して Minecraft プレイヤー名の実在確認を行うクライアント。
 * Java 版・Bedrock 版どちらにも対応する。
 */
public class McApiClient implements Listener {
    private static final String MC_API_BASE = "https://mc-api.io/uuid/";
    private static volatile McApiClient instance;

    private final Loader plugin;
    private final HttpClient client;
    private final McApiCacheStore cacheStore;
    private final Set<UUID> refreshInFlight;

    public McApiClient(Loader plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.cacheStore = new McApiCacheStore(plugin.getConfigManager());
        this.refreshInFlight = ConcurrentHashMap.newKeySet();
        instance = this;
    }

    /**
     * Java 版プレイヤーの実在確認を行う。
     *
     * @param username Minecraft Java ユーザー名
     * @return 実在すれば true の CompletableFuture
     */
    public CompletableFuture<Boolean> validateJavaPlayer(String username) {
        String url = MC_API_BASE + encodePathSegment(username) + "/java";
        return sendValidationRequest(url, "Java プレイヤー確認失敗");
    }

    /**
     * Bedrock 版プレイヤー(Xbox ゲーマータグ)の実在確認を行う。
     *
     * @param gamertag Xbox ゲーマータグ
     * @return 実在すれば true の CompletableFuture
     */
    public CompletableFuture<Boolean> validateBedrockPlayer(String gamertag) {
        String url = MC_API_BASE + encodePathSegment(gamertag) + "/bedrock";
        return sendValidationRequest(url, "Bedrock プレイヤー確認失敗");
    }

    private CompletableFuture<Boolean> sendValidationRequest(String url, String errorLabel) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                        // Java版は "id"、Bedrock版は "uuid" や "xuid" を返すため、いずれかが存在すれば有効とする
                        boolean hasId = json.has("id") && !json.get("id").isJsonNull();
                        boolean hasUuid = json.has("uuid") && !json.get("uuid").isJsonNull();
                        boolean hasXuid = json.has("xuid") && !json.get("xuid").isJsonNull();
                        return hasId || hasUuid || hasXuid;
                    }
                    return false;
                })
                .exceptionally(err -> {
                    plugin.getLogger().log(Level.WARNING, errorLabel + ": " + err.getMessage());
                    return false;
                });
    }

    private String encodePathSegment(String segment) {
        return segment.replace(" ", "%20").replace("#", "%23");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        refreshPlayerCache(event.getPlayer());
    }

    public void refreshPlayerCache(Player player) {
        refreshPlayerCache(player.getUniqueId(), player.getName(), FloodgateUtil.isBedrock(player));
    }

    /**
     * mc-api.io の顔レンダリング URL を生成する。
     * <p>
     * Bedrock 版は Floodgate 形式（{@code .PEX_kurann}）をそのまま渡せる。
     * 内部でドットプレフィックスを除去し、{@code _} をスペースに戻してから URL エンコードする。
     * </p>
     *
     * @param username  ユーザー名（Bedrock の場合は Floodgate 形式でも生の Xbox ゲーマータグでも可）
     * @param isBedrock Bedrock 版の場合 true
     * @return 顔画像 URL（256×256）
     */
    public static String getFaceUrl(String username, boolean isBedrock) {
        return buildFaceUrl(username, isBedrock);
    }

    public static String getFaceUrl(UUID uuid, String username, boolean isBedrock) {
        McApiClient current = instance;
        if (current != null) {
            String cachedUrl = current.cacheStore.getFaceUrl(uuid).orElse("");
            if (!cachedUrl.isBlank()) {
                return cachedUrl;
            }
            current.ensureCacheWarm(uuid, username, isBedrock);
        }
        return buildFaceUrl(username, isBedrock);
    }

    public static String getFaceUrl(OfflinePlayer player) {
        String name = player.getName();
        if (name == null || name.isBlank()) {
            return "";
        }
        return getFaceUrl(player.getUniqueId(), name, FloodgateUtil.isBedrock(player.getUniqueId()));
    }

    public static String getSkinUrl(UUID uuid, String username, boolean isBedrock) {
        McApiClient current = instance;
        if (current != null) {
            String cachedUrl = current.cacheStore.getSkinUrl(uuid).orElse("");
            if (!cachedUrl.isBlank()) {
                return cachedUrl;
            }
            current.ensureCacheWarm(uuid, username, isBedrock);
        }
        return buildSkinUrl(username, isBedrock);
    }

    public static String getBodyUrl(UUID uuid, String username, boolean isBedrock) {
        McApiClient current = instance;
        if (current != null) {
            String cachedUrl = current.cacheStore.getBodyUrl(uuid).orElse("");
            if (!cachedUrl.isBlank()) {
                return cachedUrl;
            }
            current.ensureCacheWarm(uuid, username, isBedrock);
        }
        return buildBodyUrl(username, isBedrock);
    }

    public static String getFaceBase64(UUID uuid, String username, boolean isBedrock) {
        McApiClient current = instance;
        if (current == null) {
            return "";
        }
        String cachedBase64 = current.cacheStore.getFaceBase64(uuid).orElse("");
        if (!cachedBase64.isBlank()) {
            return cachedBase64;
        }
        current.ensureCacheWarm(uuid, username, isBedrock);
        return "";
    }

    public static String getSkinBase64(UUID uuid, String username, boolean isBedrock) {
        McApiClient current = instance;
        if (current == null) {
            return "";
        }
        String cachedBase64 = current.cacheStore.getSkinBase64(uuid).orElse("");
        if (!cachedBase64.isBlank()) {
            return cachedBase64;
        }
        current.ensureCacheWarm(uuid, username, isBedrock);
        return "";
    }

    public static String getBodyBase64(UUID uuid, String username, boolean isBedrock) {
        McApiClient current = instance;
        if (current == null) {
            return "";
        }
        String cachedBase64 = current.cacheStore.getBodyBase64(uuid).orElse("");
        if (!cachedBase64.isBlank()) {
            return cachedBase64;
        }
        current.ensureCacheWarm(uuid, username, isBedrock);
        return "";
    }

    public static String buildFaceUrl(String username, boolean isBedrock) {
        String encoded = encodedRenderName(username, isBedrock);
        String edition = isBedrock ? "BEDROCK" : "JAVA";
        return "https://mc-api.io/render/FACE/" + encoded + "/" + edition + "?size=256";
    }

    public static String buildSkinUrl(String username, boolean isBedrock) {
        String encoded = encodedRenderName(username, isBedrock);
        String edition = isBedrock ? "BEDROCK" : "JAVA";
        return "https://mc-api.io/render/BUST/" + encoded + "/" + edition + "?size=256";
    }

    public static String buildBodyUrl(String username, boolean isBedrock) {
        String encoded = encodedRenderName(username, isBedrock);
        String edition = isBedrock ? "BEDROCK" : "JAVA";
        return "https://mc-api.io/render/FULL/" + encoded + "/" + edition + "?size=256";
    }

    private static String encodedRenderName(String username, boolean isBedrock) {
        String name = username;
        if (isBedrock) {
            name = FloodgateUtil.stripPrefix(name);
            name = name.replace("_", " ");
        }
        return name.replace(" ", "%20").replace("#", "%23");
    }

    private CompletableFuture<String> fetchBase64(String url, String label, String playerName) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        plugin.getLogger().warning("[McAPI] " + label + " 画像取得失敗: " + playerName + " HTTP " + response.statusCode());
                        return "";
                    }
                    byte[] body = response.body();
                    if (body == null || body.length == 0) {
                        return "";
                    }
                    return Base64.getEncoder().encodeToString(body);
                })
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING, "[McAPI] " + label + " 画像取得失敗: " + playerName + " - " + error.getMessage());
                    return "";
                });
    }

    private void ensureCacheWarm(UUID uuid, String rawName, boolean isBedrock) {
        refreshPlayerCache(uuid, rawName, isBedrock);
    }

    private CompletableFuture<Void> refreshPlayerCache(UUID uuid, String rawName, boolean isBedrock) {
        if (uuid == null || rawName == null || rawName.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        if (!refreshInFlight.add(uuid)) {
            return CompletableFuture.completedFuture(null);
        }

        String normalizedName = normalizedName(rawName, isBedrock);
        String faceUrl = buildFaceUrl(rawName, isBedrock);
        String skinUrl = buildSkinUrl(rawName, isBedrock);
        String bodyUrl = buildBodyUrl(rawName, isBedrock);

        CompletableFuture<String> faceBase64Future = fetchBase64(faceUrl, "face", rawName);
        CompletableFuture<String> skinBase64Future = fetchBase64(skinUrl, "skin", rawName);
        CompletableFuture<String> bodyBase64Future = fetchBase64(bodyUrl, "body", rawName);

        return CompletableFuture.allOf(faceBase64Future, skinBase64Future, bodyBase64Future)
                .thenRun(() -> cacheStore.update(
                        uuid,
                        rawName,
                        normalizedName,
                        isBedrock,
                        faceUrl,
                        skinUrl,
                        bodyUrl,
                        faceBase64Future.join(),
                        skinBase64Future.join(),
                        bodyBase64Future.join()
                ))
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING, "[McAPI] キャッシュ更新失敗: " + rawName + " - " + error.getMessage());
                    return null;
                })
                .whenComplete((ignored, error) -> refreshInFlight.remove(uuid));
    }

    private String normalizedName(String rawName, boolean isBedrock) {
        if (!isBedrock) {
            return rawName;
        }
        return FloodgateUtil.stripPrefix(rawName).replace("_", " ");
    }
}
