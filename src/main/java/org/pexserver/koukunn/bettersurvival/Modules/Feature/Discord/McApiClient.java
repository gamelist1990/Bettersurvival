package org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * mc-api.io を使用して Minecraft プレイヤー名の実在確認を行うクライアント。
 * Java 版・Bedrock 版どちらにも対応する。
 */
public class McApiClient {
    private static final String MC_API_BASE = "https://mc-api.io/uuid/";

    private final Plugin plugin;
    private final HttpClient client;

    public McApiClient(Plugin plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
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
        String name = username;
        if (isBedrock) {
            name = FloodgateUtil.stripPrefix(name);
            name = name.replace("_", " ");
        }
        String edition = isBedrock ? "bedrock" : "java";
        String encoded = name.replace(" ", "%20").replace("#", "%23");
        return "https://mc-api.io/render/face/" + encoded + "/" + edition + "?size=256";
    }
}
