package org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.Plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class DiscordWebhookClient {
    private final Plugin plugin;
    private final HttpClient client;

    public DiscordWebhookClient(Plugin plugin) {
        this.plugin = plugin;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public boolean send(String url, JsonObject payload) {
        if (!isValidUrl(url)) return false;

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        plugin.getLogger().warning("DiscordWebhook送信失敗: HTTP " + response.statusCode());
                    }
                })
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING, "DiscordWebhook送信失敗: " + error.getMessage());
                    return null;
                });
        return true;
    }

    public CompletableFuture<String> sendAndReturnMessageId(String url, JsonObject payload) {
        if (!isValidUrl(url)) return CompletableFuture.completedFuture("");

        HttpRequest request = HttpRequest.newBuilder(URI.create(withWait(url)))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        plugin.getLogger().warning("DiscordWebhook送信失敗: HTTP " + response.statusCode());
                        return "";
                    }
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    return json.has("id") ? json.get("id").getAsString() : "";
                })
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING, "DiscordWebhook送信失敗: " + error.getMessage());
                    return "";
                });
    }

    public CompletableFuture<String> sendAndReturnMessageId(String url, JsonObject payload, String fileName, byte[] fileBytes) {
        if (!isValidUrl(url)) return CompletableFuture.completedFuture("");

        String boundary = "----BetterSurvival" + UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder(URI.create(withWait(url)))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(buildMultipartBody(boundary, payload, fileName, fileBytes)))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        plugin.getLogger().warning("DiscordWebhook送信失敗: HTTP " + response.statusCode());
                        return "";
                    }
                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    return json.has("id") ? json.get("id").getAsString() : "";
                })
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING, "DiscordWebhook送信失敗: " + error.getMessage());
                    return "";
                });
    }

    public CompletableFuture<Boolean> editMessage(String url, String messageId, JsonObject payload) {
        if (!isValidUrl(url) || messageId == null || messageId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(messageUrl(url, messageId)))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return true;
                    }
                    if (response.statusCode() != 404) {
                        plugin.getLogger().warning("DiscordWebhookメッセージ更新失敗: HTTP " + response.statusCode());
                    }
                    return false;
                })
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING, "DiscordWebhookメッセージ更新失敗: " + error.getMessage());
                    return false;
                });
    }

    public CompletableFuture<Boolean> editMessage(String url, String messageId, JsonObject payload, String fileName, byte[] fileBytes) {
        if (!isValidUrl(url) || messageId == null || messageId.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }

        String boundary = "----BetterSurvival" + UUID.randomUUID();
        HttpRequest request = HttpRequest.newBuilder(URI.create(messageUrl(url, messageId)))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .method("PATCH", HttpRequest.BodyPublishers.ofByteArray(buildMultipartBody(boundary, payload, fileName, fileBytes)))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApply(response -> {
                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        return true;
                    }
                    if (response.statusCode() != 404) {
                        plugin.getLogger().warning("DiscordWebhookメッセージ更新失敗: HTTP " + response.statusCode());
                    }
                    return false;
                })
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.WARNING, "DiscordWebhookメッセージ更新失敗: " + error.getMessage());
                    return false;
                });
    }

    public boolean isValidUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String value = url.trim();
        return value.startsWith("https://") || value.startsWith("http://");
    }

    private String withWait(String url) {
        String value = url.trim();
        return value + (value.contains("?") ? "&" : "?") + "wait=true";
    }

    private String messageUrl(String url, String messageId) {
        String value = url.trim();
        int queryIndex = value.indexOf('?');
        if (queryIndex < 0) {
            return value + "/messages/" + messageId;
        }
        return value.substring(0, queryIndex) + "/messages/" + messageId + value.substring(queryIndex);
    }

    private byte[] buildMultipartBody(String boundary, JsonObject payload, String fileName, byte[] fileBytes) {
        ByteArrayBuilder body = new ByteArrayBuilder();
        JsonObject multipartPayload = payload;
        if (fileBytes != null && fileBytes.length > 0 && fileName != null && !fileName.isBlank()) {
            multipartPayload = payload.deepCopy();
            JsonArray attachments = new JsonArray();
            JsonObject attachment = new JsonObject();
            attachment.addProperty("id", 0);
            attachment.addProperty("filename", fileName);
            attachments.add(attachment);
            multipartPayload.add("attachments", attachments);
        }
        body.append(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        body.append("Content-Disposition: form-data; name=\"payload_json\"\r\n".getBytes(StandardCharsets.UTF_8));
        body.append("Content-Type: application/json\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        body.append(multipartPayload.toString().getBytes(StandardCharsets.UTF_8));
        body.append("\r\n".getBytes(StandardCharsets.UTF_8));
        if (fileBytes != null && fileBytes.length > 0 && fileName != null && !fileName.isBlank()) {
            body.append(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
            body.append(("Content-Disposition: form-data; name=\"files[0]\"; filename=\"" + fileName + "\"\r\n")
                    .getBytes(StandardCharsets.UTF_8));
            body.append("Content-Type: image/png\r\n\r\n".getBytes(StandardCharsets.UTF_8));
            body.append(fileBytes);
            body.append("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        body.append(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return body.toByteArray();
    }

    private static final class ByteArrayBuilder {
        private final java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();

        private void append(byte[] value) {
            output.write(value, 0, value.length);
        }

        private byte[] toByteArray() {
            return output.toByteArray();
        }
    }
}
