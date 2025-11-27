package org.pexserver.koukunn.bettersurvival.Modules.Feature.BedrockSkin;

import com.google.gson.JsonObject;

import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Mineskin API を使用してスキンをアップロードし、Java 用の value/signature を取得
 */
public final class SkinUploader {
    private static final String UPLOAD_URL = "https://api.mineskin.org/generate/upload";
    private static final int MAX_TRIES = 3;

    private final Executor requestExecutor = Executors.newSingleThreadExecutor();
    private long nextRequest;

    public CompletableFuture<UploadResult> uploadSkin(RawSkin rawSkin) {
        return CompletableFuture.supplyAsync(() -> uploadSkinInner(rawSkin, 0), requestExecutor);
    }

    private UploadResult uploadSkinInner(RawSkin rawSkin, int times) {
        // レート制限に従って待機
        if (System.currentTimeMillis() < nextRequest) {
            try {
                Thread.sleep(nextRequest - System.currentTimeMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        BufferedImage image = SkinUtils.toBufferedImage(rawSkin);
        SkinModel model = rawSkin.alex ? SkinModel.ALEX : SkinModel.STEVE;
        String url = UPLOAD_URL + getUploadUrlParameters(model);

        try {
            UploadResult result = parseAndHandleResponse(HttpUtils.post(url, image));
            if (result.getHttpCode() == 429) {
                if (times + 1 >= MAX_TRIES) {
                    return result;
                }
                return uploadSkinInner(rawSkin, times + 1);
            }
            return result;
        } catch (RuntimeException exception) {
            return UploadResult.exception(exception);
        }
    }

    private String getUploadUrlParameters(SkinModel model) {
        return "?visibility=1&model=" + model.getName();
    }

    private UploadResult parseAndHandleResponse(HttpUtils.HttpResponse response) {
        int httpCode = response.getHttpCode();
        JsonObject jsonResponse = response.getResponse();

        if (jsonResponse == null) {
            throw new IllegalStateException("Response cannot be null!");
        }

        if (jsonResponse.has("nextRequest")) {
            nextRequest = jsonResponse.get("nextRequest").getAsLong();
        }

        if (httpCode >= 200 && httpCode < 300) {
            return UploadResult.success(httpCode, jsonResponse);
        } else {
            String error = jsonResponse.has("error") ? jsonResponse.get("error").getAsString() : "Unknown error";
            return UploadResult.failed(httpCode, error);
        }
    }

    public boolean checkResult(Consumer<String> warningLoggingFunction, String playerName, UploadResult uploadResult, Throwable throwable) {
        if (throwable != null) {
            warningLoggingFunction.accept("Failed to upload player skin for " + playerName);
            throwable.printStackTrace();
            return false;
        }

        if (uploadResult.getError() != null) {
            warningLoggingFunction.accept("Error while uploading player skin for " + playerName + " " + uploadResult.getError());
            return false;
        }
        return true;
    }

    public enum SkinModel {
        STEVE, ALEX;

        public static final SkinModel[] VALUES = values();

        public String getName() {
            return name;
        }

        private final String name = name().toLowerCase(Locale.ROOT);

        public static SkinModel getByOrdinal(int ordinal) {
            return VALUES.length > ordinal ? VALUES[ordinal] : STEVE;
        }

        public static SkinModel getByName(String name) {
            return "alex".equalsIgnoreCase(name) ? ALEX : STEVE;
        }
    }

    public static final class UploadResult {
        private final int httpCode;
        private final String error;
        private final boolean exception;

        private final SkinModel model;
        private final String skinUrl;
        private final String capeUrl;
        private final JsonObject response;

        private UploadResult(int httpCode, String error, boolean exception, SkinModel model, String skinUrl, String capeUrl, JsonObject response) {
            this.httpCode = httpCode;
            this.error = error;
            this.exception = exception;
            this.model = model;
            this.skinUrl = skinUrl;
            this.capeUrl = capeUrl;
            this.response = response;
        }

        public int getHttpCode() {
            return httpCode;
        }

        public String getError() {
            return error;
        }

        public boolean isException() {
            return exception;
        }

        public SkinModel getModel() {
            return model;
        }

        public String getSkinUrl() {
            return skinUrl;
        }

        public String getCapeUrl() {
            return capeUrl;
        }

        public JsonObject getResponse() {
            return response;
        }

        public static UploadResult exception(Throwable throwable) {
            return new UploadResult(-1, throwable.getMessage(), true, null, null, null, null);
        }

        public static UploadResult failed(int httpCode, String error) {
            return new UploadResult(httpCode, error, false, SkinModel.STEVE, null, null, null);
        }

        public static UploadResult success(int httpCode, JsonObject body) {
            SkinModel model = SkinModel.getByName(body.has("variant") ? body.get("variant").getAsString() : "classic");

            JsonObject data = body.getAsJsonObject("data");
            JsonObject textureData = data.getAsJsonObject("texture");

            String skinUrl = null;
            String capeUrl = null;
            if (textureData.has("url")) {
                skinUrl = textureData.get("url").getAsString();
            }

            JsonObject response = new JsonObject();
            response.addProperty("value", textureData.get("value").getAsString());
            response.addProperty("signature", textureData.get("signature").getAsString());

            return new UploadResult(httpCode, null, false, model, skinUrl, capeUrl, response);
        }

        public static UploadResult success(JsonObject response) {
            return new UploadResult(200, null, false, null, null, null, response);
        }
    }
}
