package org.pexserver.koukunn.bettersurvival.Modules.Feature.BedrockSkin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * HTTP リクエスト用ユーティリティ
 */
public final class HttpUtils {

    private static final int TIMEOUT = 30000;
    private static final String USER_AGENT = "BetterSurvival/1.0";

    private HttpUtils() {}

    /**
     * 画像を Mineskin API にアップロードする POST リクエスト
     */
    public static HttpResponse post(String urlString, BufferedImage image) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);
            
            String boundary = "----" + UUID.randomUUID().toString().replace("-", "");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            connection.setRequestProperty("User-Agent", USER_AGENT);

            try (OutputStream os = connection.getOutputStream()) {
                // Write multipart form data
                writeMultipartImage(os, boundary, image);
            }

            int responseCode = connection.getResponseCode();
            String responseBody;

            try (InputStream is = responseCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                responseBody = sb.toString();
            }

            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            return new HttpResponse(responseCode, json);

        } catch (Exception e) {
            throw new RuntimeException("HTTP POST failed: " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * マルチパートフォームデータとして画像を書き込む
     */
    private static void writeMultipartImage(OutputStream os, String boundary, BufferedImage image) throws IOException {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true);
        String CRLF = "\r\n";

        // ファイルパート
        writer.append("--").append(boundary).append(CRLF);
        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"").append(CRLF);
        writer.append("Content-Type: image/png").append(CRLF);
        writer.append(CRLF).flush();

        // 画像データを書き込む
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        os.write(baos.toByteArray());
        os.flush();

        writer.append(CRLF).flush();
        writer.append("--").append(boundary).append("--").append(CRLF).flush();
    }

    /**
     * HTTP レスポンス
     */
    public static class HttpResponse {
        private final int httpCode;
        private final JsonObject response;

        public HttpResponse(int httpCode, JsonObject response) {
            this.httpCode = httpCode;
            this.response = response;
        }

        public int getHttpCode() {
            return httpCode;
        }

        public JsonObject getResponse() {
            return response;
        }
    }
}
