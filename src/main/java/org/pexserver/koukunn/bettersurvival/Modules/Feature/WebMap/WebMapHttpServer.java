package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.World;
import org.pexserver.koukunn.bettersurvival.Core.Util.ServerInfoUtil;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService.WebProfile;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService.WebPost;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService.WebServiceModule;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;

public class WebMapHttpServer {
    private static final Gson GSON = new Gson();
    private static final int TILE_SIZE = 512;
    private static final int CHUNK_PIXEL_SIZE = 16;
    private static final String PLACEHOLDER_TITLE = "__WEBMAP_TITLE__";
    private static final String PLACEHOLDER_DESCRIPTION = "__WEBMAP_DESCRIPTION__";
    private static final String PLACEHOLDER_SITE_NAME = "__WEBMAP_SITE_NAME__";
    private static final String PLACEHOLDER_URL = "__WEBMAP_URL__";
    private static final String PLACEHOLDER_IMAGE = "__WEBMAP_IMAGE__";
    private static final String PLACEHOLDER_IMAGE_ALT = "__WEBMAP_IMAGE_ALT__";

    private final WebMapModule module;
    private final WebMapRateLimiter apiLimiter = new WebMapRateLimiter(120, 60D);
    private final WebMapRateLimiter tileLimiter = new WebMapRateLimiter(512, 256D);
    private final WebMapTileCache tileCache = new WebMapTileCache(512);
    private HttpServer server;
    private HaproxyProxyServer haproxyServer;

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    public WebMapHttpServer(WebMapModule module) {
        this.module = module;
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public synchronized void start(WebMapSettings settings) throws IOException {
        stop();
        String host = settings.isPublicAccess() ? "0.0.0.0" : "127.0.0.1";
        WebServiceModule webService = module.getPlugin().getWebServiceModule();
        boolean haproxyEnabled = webService != null && webService.isHaproxyProtocolEnabled();
        if (haproxyEnabled) {
            // PROXY protocol v2 モード: HTTP はループバックの一時ポートで待ち受け、
            // 公開ポートには実IPを解決するリレーを立てる
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } else {
            server = HttpServer.create(new InetSocketAddress(host, settings.getPort()), 0);
        }
        server.createContext("/api/v1/bootstrap", safe(this::handleBootstrap));
        server.createContext("/api/v1/status", safe(this::handleStatus));
        server.createContext("/api/v1/auth/register", safe(this::handleAuthRegister));
        server.createContext("/api/v1/auth/login", safe(this::handleAuthLogin));
        server.createContext("/api/v1/auth/logout", safe(this::handleAuthLogout));
        server.createContext("/api/v1/auth/me", safe(this::handleAuthMe));
        server.createContext("/api/v1/profile/update", safe(this::handleProfileUpdate));
        server.createContext("/api/v1/feed", safe(this::handleFeed));
        server.createContext("/api/v1/feed/post", safe(this::handleFeedPost));
        server.createContext("/api/v1/feed/like", safe(this::handleFeedLike));
        server.createContext("/api/v1/feed/repost", safe(this::handleFeedRepost));
        server.createContext("/api/v1/feed/delete", safe(this::handleFeedDelete));
        server.createContext("/api/v1/feed/updates", safe(this::handleFeedUpdates));
        server.createContext("/api/v1/feed/image", safe(this::handleFeedImage));
        server.createContext("/api/v1/privacy/request", safe(this::handlePrivacyRequest));
        server.createContext("/api/v1/privacy/requests", safe(this::handlePrivacyRequests));
        server.createContext("/api/v1/privacy/disclosure", safe(this::handlePrivacyDisclosure));
        server.createContext("/api/v1/admin/me", safe(this::handleAdminMe));
        server.createContext("/api/v1/admin/privacy/requests", safe(this::handleAdminPrivacyRequests));
        server.createContext("/api/v1/admin/privacy/requests/resolve", safe(this::handleAdminPrivacyResolve));
        server.createContext("/api/v1/worlds", safe(this::handleApiWorlds));
        server.createContext("/api/v1/players", safe(this::handleApiPlayers));
        server.createContext("/api/status", safe(this::handleStatus));
        server.createContext("/tiles", safe(this::handleTiles));
        server.createContext("/uploads", safe(this::handleUploads));
        server.createContext("/", safe(this::handleStatic));
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        if (haproxyEnabled) {
            haproxyServer = new HaproxyProxyServer(module.getPlugin().getLogger());
            try {
                haproxyServer.start(host, settings.getPort(), server.getAddress().getPort());
            } catch (IOException error) {
                stop();
                throw error;
            }
        }
    }

    private HttpHandler safe(ExchangeHandler delegate) {
        return exchange -> {
            try {
                delegate.handle(exchange);
            } catch (Throwable throwable) {
                if (isClientDisconnect(throwable)) {
                    exchange.close();
                    return;
                }
                module.getPlugin().getLogger().log(java.util.logging.Level.WARNING,
                        "WebMap HTTP handler failed: " + exchange.getRequestURI(), throwable);
                try {
                    if (!exchange.getResponseHeaders().containsKey("Content-Type")) {
                        writePlain(exchange, 500, "Internal Server Error");
                    }
                } catch (IOException ignored) {
                    exchange.close();
                }
            }
        };
    }

    private boolean isClientDisconnect(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof SocketException) {
                return true;
            }
            if (current instanceof IOException ioException) {
                String message = ioException.getMessage();
                if (message == null) {
                    continue;
                }
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("stream is closed")
                        || normalized.contains("broken pipe")
                        || normalized.contains("connection reset")
                        || normalized.contains("forcibly closed")
                        || normalized.contains("an existing connection was forcibly closed")) {
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized void stop() {
        if (haproxyServer != null) {
            haproxyServer.stop();
            haproxyServer = null;
        }
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    /**
     * 実クライアント IP を解決する。
     * HAProxy PROXY protocol v2 が有効な場合はリレーの対応表から取得する。
     */
    private String clientIp(HttpExchange exchange) {
        if (exchange.getRemoteAddress() == null || exchange.getRemoteAddress().getAddress() == null) {
            return "unknown";
        }
        HaproxyProxyServer relay = haproxyServer;
        if (relay != null) {
            String real = relay.lookupClientIp(exchange.getRemoteAddress().getPort());
            if (real != null && !real.isBlank()) {
                return real;
            }
        }
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private void handleBootstrap(HttpExchange exchange) throws IOException {
        if (!guardApi(exchange)) {
            return;
        }
        String serverName = ServerInfoUtil.getServerName();
        writeJson(exchange, Map.of(
                "static", false,
                "server", Map.of(
                        "title", serverName + " WebMap - {world}",
                        "statusUrl", "/api/v1/status",
                        "worldsUrl", "/api/v1/worlds",
                        "playersUrl", "/api/v1/players"
                )
        ), 3);
    }

    private void handleStatus(HttpExchange exchange) throws IOException {
        if (!guardApi(exchange)) {
            return;
        }
        writeJson(exchange, Map.of(
                "running", module.isServerRunning(),
                "globallyEnabled", module.isGloballyEnabled(),
                "paused", module.getSettings().isPaused(),
                "publicAccess", module.getSettings().isPublicAccess(),
                "port", module.getSettings().getPort(),
                "url", module.getPublicUrl(),
                "playerTracking", module.getSettings().isAutoTrackPlayers(),
                "chunkGenActive", module.getActiveChunkGenCount()
        ), 2);
    }

    private void handleApiWorlds(HttpExchange exchange) throws IOException {
        if (!guardWebMap(exchange)) {
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (!"/api/v1/worlds".equals(path)) {
            handleApiWorld(exchange);
            return;
        }
        if (!guardApi(exchange)) {
            return;
        }
        List<Map<String, Object>> worlds = new ArrayList<>();
        int order = 0;
        for (World world : module.getPlugin().getServer().getWorlds()) {
            WebMapDimensionSettings dimension = module.getDimensionSettings(world);
            if (!dimension.isVisible()) {
                continue;
            }
            String worldKey = world.getKey().toString();
            String worldName = world.getName();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", worldKey);
            row.put("name", worldName);
            row.put("displayName", dimension.getDisplayName());
            row.put("order", order++);
            row.put("type", switch (world.getEnvironment()) {
                case NETHER -> "nether";
                case THE_END -> "the_end";
                default -> "normal";
            });
            row.put("environment", world.getEnvironment().name());
            row.put("icon", null);
            row.put("chunks", module.getChunkCount(worldKey));
            row.put("tileUrl", "/api/v1/worlds/" + worldName + "/tile/{z}/{x}_{y}.png");
            row.put("worldUrl", "/api/v1/worlds/" + worldName);
            row.put("spawn", Map.of(
                    "x", world.getSpawnLocation().getBlockX(),
                    "z", world.getSpawnLocation().getBlockZ()
            ));
            worlds.add(row);
        }
        writeJson(exchange, worlds, 10);
    }

    private void handleApiPlayers(HttpExchange exchange) throws IOException {
        if (!guardWebMap(exchange)) {
            return;
        }
        if (!guardApi(exchange)) {
            return;
        }
        writeJson(exchange, Map.of(
                "players", module.getOnlinePlayersSnapshot(),
                "max", module.getPlugin().getServer().getMaxPlayers(),
                "updatedAt", System.currentTimeMillis()
        ), 1);
    }

    private void handleTiles(HttpExchange exchange) throws IOException {
        if (!guardWebMap(exchange)) {
            return;
        }
        if (!guardTile(exchange)) {
            return;
        }
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/tiles/settings.json")) {
            handleTilesSettings(exchange);
            return;
        }
        if (path.equals("/tiles/players.json")) {
            handleTilesPlayers(exchange);
            return;
        }
        String[] segments = path.split("/");
        if (segments.length >= 4 && segments[3].equals("settings.json")) {
            handleWorldSettings(exchange, segments[2]);
            return;
        }
        if (segments.length >= 4 && segments[3].equals("markers.json")) {
            handleTilesMarkers(exchange, segments[2]);
            return;
        }
        if (segments.length >= 5 && segments[4].endsWith(".png")) {
            handleTileImage(exchange, segments[2], segments[3], segments[4]);
            return;
        }
        writePlain(exchange, 404, "Not Found");
    }

    private void handleApiWorld(HttpExchange exchange) throws IOException {
        if (!guardWebMap(exchange)) {
            return;
        }
        if (!guardApi(exchange)) {
            return;
        }
        String[] segments = exchange.getRequestURI().getPath().split("/");
        if (segments.length < 5) {
            writePlain(exchange, 404, "Not Found");
            return;
        }
        String worldName = segments[4];
        if (segments.length == 5) {
            handleWorldV1Settings(exchange, worldName);
            return;
        }
        if (segments.length == 6 && "changes".equals(segments[5])) {
            handleWorldChanges(exchange, worldName);
            return;
        }
        if (segments.length == 6 && "markers".equals(segments[5])) {
            handleWorldMarkers(exchange, worldName);
            return;
        }
        if (segments.length == 8 && "tile".equals(segments[5]) && segments[7].endsWith(".png")) {
            if (!guardTile(exchange)) {
                return;
            }
            handleTileImage(exchange, worldName, segments[6], segments[7]);
            return;
        }
        writePlain(exchange, 404, "Not Found");
    }

    private void handleTilesSettings(HttpExchange exchange) throws IOException {
        List<Map<String, Object>> worlds = new ArrayList<>();
        int order = 0;
        for (World world : module.getPlugin().getServer().getWorlds()) {
            WebMapDimensionSettings dimension = module.getDimensionSettings(world);
            if (!dimension.isVisible()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", world.getName());
            row.put("display_name", dimension.getDisplayName());
            row.put("icon", null);
            row.put("order", order++);
            row.put("type", switch (world.getEnvironment()) {
                case NETHER -> "nether";
                case THE_END -> "the_end";
                default -> "normal";
            });
            worlds.add(row);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("static", false);
        payload.put("worlds", worlds);
        payload.put("ui", Map.of(
                "title", "BetterSurvival WebMap - {world}",
                "sidebar", Map.of(
                        "pinned", "pinned",
                        "world_list_label", "Worlds",
                        "player_list_label", "Players ({cur}/{max})"
                ),
                "coordinates", Map.of(
                        "enabled", true,
                        "html", "X {x} / Z {z}"
                ),
                "link", Map.of(
                        "enabled", true
                )
        ));
        writeJson(exchange, payload, 15);
    }

    private void handleTilesPlayers(HttpExchange exchange) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("players", module.getOnlinePlayersSnapshot());
        payload.put("max", module.getPlugin().getServer().getMaxPlayers());
        writeJson(exchange, payload, 1);
    }

    private void handleWorldSettings(HttpExchange exchange, String worldName) throws IOException {
        World world = resolveWorldByName(worldName);
        if (world == null) {
            writePlain(exchange, 404, "World Not Found");
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("player_tracker", Map.of(
                "enabled", true,
                "update_interval", 2,
                "label", "Players",
                "show_controls", true,
                "default_hidden", false,
                "priority", 100,
                "z_index", 1000,
                "nameplates", Map.of(
                        "enabled", true,
                        "show_heads", true,
                        "show_health", false,
                        "show_armor", false,
                        "heads_url", "__player_face__"
                )
        ));
        payload.put("marker_update_interval", 30);
        payload.put("tiles_update_interval", 10);
        payload.put("zoom", Map.of(
                "def", 0,
                "max", 0,
                "extra", 6
        ));
        payload.put("spawn", Map.of(
                "x", world.getSpawnLocation().getBlockX(),
                "z", world.getSpawnLocation().getBlockZ()
        ));
        payload.put("markersUrl", "/api/v1/worlds/" + world.getName() + "/markers");
        writeJson(exchange, payload, 15);
    }

    private void handleWorldMarkers(HttpExchange exchange, String worldName) throws IOException {
        World world = resolveWorldByName(worldName);
        if (world == null) {
            writePlain(exchange, 404, "World Not Found");
            return;
        }
        writeJson(exchange, Map.of(
                "markers", module.snapshotMarkers(world.getKey().toString()),
                "updatedAt", System.currentTimeMillis()
        ), 10);
    }

    private void handleTilesMarkers(HttpExchange exchange, String worldName) throws IOException {
        handleWorldMarkers(exchange, worldName);
    }

    private void handleWorldV1Settings(HttpExchange exchange, String worldName) throws IOException {
        World world = resolveWorldByName(worldName);
        if (world == null) {
            writePlain(exchange, 404, "World Not Found");
            return;
        }
        WebMapDimensionSettings dimension = module.getDimensionSettings(world);
        String type = switch (world.getEnvironment()) {
            case NETHER -> "nether";
            case THE_END -> "the_end";
            default -> "normal";
        };
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("key", world.getKey().toString());
        payload.put("name", world.getName());
        payload.put("displayName", dimension.getDisplayName());
        payload.put("environment", world.getEnvironment().name());
        payload.put("type", type);
        payload.put("chunks", module.getChunkCount(world.getKey().toString()));
        payload.put("playerTracker", Map.of(
                "enabled", true,
                "nameplates", Map.of(
                        "enabled", true,
                        "showHeads", true,
                        "showHealth", false,
                        "showArmor", false
                )
        ));
        payload.put("zoom", Map.of(
                "default", 0,
                "maxNative", 0,
                "extra", 3,
                "min", -3,
                "max", 6
        ));
        payload.put("spawn", Map.of(
                "x", world.getSpawnLocation().getBlockX(),
                "z", world.getSpawnLocation().getBlockZ()
        ));
        payload.put("backgroundImage", switch (type) {
            case "nether" -> "/images/nether_sky.png";
            case "the_end" -> "/images/end_sky.png";
            default -> "/images/overworld_sky.png";
        });
        payload.put("tileTemplate", "/api/v1/worlds/" + world.getName() + "/tile/{z}/{x}_{y}.png");
        payload.put("changesUrl", "/api/v1/worlds/" + world.getName() + "/changes");
        payload.put("markersUrl", "/api/v1/worlds/" + world.getName() + "/markers");
        writeJson(exchange, payload, 15);
    }

    private void handleWorldChanges(HttpExchange exchange, String worldName) throws IOException {
        World world = resolveWorldByName(worldName);
        if (world == null) {
            writePlain(exchange, 404, "World Not Found");
            return;
        }
        long since = parseLong(queryValue(exchange, "since"), 0L);
        List<Map<String, Object>> tiles = module.getChangedTilesSince(world.getKey().toString(), since);
        long latest = since;
        for (Map<String, Object> tile : tiles) {
            Object updatedAt = tile.get("updatedAt");
            if (updatedAt instanceof Number number) {
                latest = Math.max(latest, number.longValue());
            }
        }
        writeJson(exchange, Map.of(
                "tiles", tiles,
                "latest", latest
        ), 0);
    }

    private void handleTileImage(HttpExchange exchange, String worldName, String tileZoom, String tileSegment) throws IOException {
        World world = resolveWorldByName(worldName);
        if (world == null) {
            writeEmptyTile(exchange);
            return;
        }
        String worldKey = world.getKey().toString();
        int underscore = tileSegment.indexOf('_');
        int dot = tileSegment.lastIndexOf('.');
        if (underscore <= 0 || dot <= underscore) {
            writeEmptyTile(exchange);
            return;
        }
        int tileX = parseInt(tileSegment.substring(0, underscore), 0);
        int tileZ = parseInt(tileSegment.substring(underscore + 1, dot), 0);
        String revision = queryValue(exchange, "v");
        String cacheKey = worldKey + ":" + tileZoom + ":" + tileX + ":" + tileZ + ":" + revision;
        
        List<WebMapChunkRecord> tileChunks = new ArrayList<>();
        boolean hasPixels = false;
        long newestUpdatedAt = 0L;
        int baseBlockX = tileX * TILE_SIZE;
        int baseBlockZ = tileZ * TILE_SIZE;
        int chunkStartX = Math.floorDiv(baseBlockX, CHUNK_PIXEL_SIZE);
        int chunkStartZ = Math.floorDiv(baseBlockZ, CHUNK_PIXEL_SIZE);
        int chunkEndX = Math.floorDiv(baseBlockX + TILE_SIZE - 1, CHUNK_PIXEL_SIZE);
        int chunkEndZ = Math.floorDiv(baseBlockZ + TILE_SIZE - 1, CHUNK_PIXEL_SIZE);
        for (int chunkX = chunkStartX; chunkX <= chunkEndX; chunkX++) {
            for (int chunkZ = chunkStartZ; chunkZ <= chunkEndZ; chunkZ++) {
                WebMapChunkRecord chunk = module.getChunk(worldKey, chunkX, chunkZ);
                if (chunk == null || chunk.getPixels() == null || chunk.getPixels().length < 256) {
                    continue;
                }
                hasPixels = true;
                newestUpdatedAt = Math.max(newestUpdatedAt, chunk.getUpdatedAt());
                tileChunks.add(chunk);
            }
        }
        if (!hasPixels) {
            writeEmptyTile(exchange);
            return;
        }

        WebMapTileCache.TileEntry cached = tileCache.get(cacheKey);
        String requestEtag = exchange.getRequestHeaders().getFirst("If-None-Match");
        if (cached != null && cached.updatedAt() == newestUpdatedAt && cached.etag().equals(requestEtag)) {
            exchange.getResponseHeaders().set("ETag", cached.etag());
            exchange.sendResponseHeaders(304, -1);
            exchange.close();
            return;
        }

        String etag = "\"" + worldKey.hashCode() + "-" + tileX + "-" + tileZ + "-" + newestUpdatedAt + "\"";
        if (cached != null && cached.updatedAt() == newestUpdatedAt) {
            writePng(exchange, cached.payload(), etag, 30);
            return;
        }
        BufferedImage image = new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (WebMapChunkRecord chunk : tileChunks) {
            int startPixelX = (chunk.getX() * CHUNK_PIXEL_SIZE) - baseBlockX;
            int startPixelZ = (chunk.getZ() * CHUNK_PIXEL_SIZE) - baseBlockZ;
            for (int localZ = 0; localZ < CHUNK_PIXEL_SIZE; localZ++) {
                int pixelZ = startPixelZ + localZ;
                if (pixelZ < 0 || pixelZ >= TILE_SIZE) {
                    continue;
                }
                for (int localX = 0; localX < CHUNK_PIXEL_SIZE; localX++) {
                    int pixelX = startPixelX + localX;
                    if (pixelX < 0 || pixelX >= TILE_SIZE) {
                        continue;
                    }
                    image.setRGB(pixelX, pixelZ, parseColor(chunk.getPixels()[(localZ * CHUNK_PIXEL_SIZE) + localX]));
                }
            }
        }
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", byteStream);
        byte[] body = byteStream.toByteArray();
        tileCache.put(cacheKey, body, etag, newestUpdatedAt);
        writePng(exchange, body, etag, 30);
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writePlain(exchange, 405, "Method Not Allowed");
            return;
        }
        String path = decodeRequestPath(exchange.getRequestURI().getRawPath());
        if (path == null) {
            writePlain(exchange, 404, "Not Found");
            return;
        }
        if (path == null || path.equals("/") || path.isBlank() || path.equals("/webmap") || path.startsWith("/webmap/")) {
            path = "/index.html";
        }
        if (path.startsWith("/api/") || path.startsWith("/tiles")) {
            writePlain(exchange, 404, "Not Found");
            return;
        }
        byte[] body = staticResource(path);
        if (body == null) {
            if (isStaticAssetPath(path)) {
                writePlain(exchange, 404, "Not Found");
                return;
            }
            body = staticResource("/index.html");
            path = "/index.html";
        }
        if (body == null) {
            writePlain(exchange, 404, "WebMap frontend not built");
            return;
        }
        if (path.equals("/index.html")) {
            body = renderIndexHtml(exchange, body);
        }
        securityHeaders(exchange);
        if (path.equals("/index.html")) {
            exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; img-src 'self' data: blob: http: https:; style-src 'self' 'unsafe-inline'; script-src 'self'; connect-src 'self'; font-src 'self' data:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'");
        }
        exchange.getResponseHeaders().set("Content-Type", mimeType(path));
        if (path.startsWith("/assets/") || path.startsWith("/images/") || path.endsWith(".ico") || path.endsWith(".svg")) {
            exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
        } else {
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        }
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private void writeJson(HttpExchange exchange, Object payload, int maxAgeSeconds) throws IOException {
        securityHeaders(exchange);
        byte[] body = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=" + Math.max(0, maxAgeSeconds));
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private void writePlain(HttpExchange exchange, int status, String text) throws IOException {
        securityHeaders(exchange);
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private void writePng(HttpExchange exchange, byte[] body, String etag, int maxAgeSeconds) throws IOException {
        securityHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "image/png");
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=" + Math.max(0, maxAgeSeconds));
        exchange.getResponseHeaders().set("ETag", etag);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private void handleUploads(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writePlain(exchange, 405, "Method Not Allowed");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String prefix = "/uploads/feed/";
        if (path == null || !path.startsWith(prefix)) {
            writePlain(exchange, 404, "Not Found");
            return;
        }
        String fileName = path.substring(prefix.length());
        if (!fileName.matches("[a-f0-9\\-]{36}\\.(png|jpg|jpeg|gif|webp)")) {
            writePlain(exchange, 404, "Not Found");
            return;
        }
        File file = new File(module.getPlugin().getConfigManager().getBaseDir(), "WebService/uploads/feed/" + fileName);
        if (!file.isFile()) {
            writePlain(exchange, 404, "Not Found");
            return;
        }
        byte[] body = java.nio.file.Files.readAllBytes(file.toPath());
        securityHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", uploadMimeType(fileName));
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=31536000, immutable");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private String uploadMimeType(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "image/png";
    }

    private void writeEmptyTile(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB), "png", byteStream);
        writePng(exchange, byteStream.toByteArray(), "\"empty\"", 30);
    }

    private byte[] renderIndexHtml(HttpExchange exchange, byte[] templateBytes) {
        String serverName = ServerInfoUtil.getServerName();
        PageMeta meta = buildPageMeta(exchange, serverName);
        String html = new String(templateBytes, StandardCharsets.UTF_8)
                .replace(PLACEHOLDER_TITLE, escapeHtml(meta.title()))
                .replace(PLACEHOLDER_DESCRIPTION, escapeHtml(meta.description()))
                .replace(PLACEHOLDER_SITE_NAME, escapeHtml(serverName))
                .replace(PLACEHOLDER_URL, escapeHtml(meta.url()))
                .replace(PLACEHOLDER_IMAGE, escapeHtml(meta.image()))
                .replace(PLACEHOLDER_IMAGE_ALT, escapeHtml(meta.title()));
        return html.getBytes(StandardCharsets.UTF_8);
    }

    private PageMeta buildPageMeta(HttpExchange exchange, String serverName) {
        String base = (serverName == null || serverName.isBlank()) ? "PEX Survival Server" : serverName.trim();
        String path = exchange.getRequestURI().getPath();
        String url = absoluteRequestUrl(exchange);
        String fallbackImage = absoluteRequestBase(exchange) + "/images/wiki/pexserver-hero.png";
        if (path == null || path.isBlank() || path.equals("/")) {
            return new PageMeta(
                    trimToLength(base + " - Home", 80),
                    trimToLength(base + " の公式Webサイトです。サーバー情報、フィード、プロフィール、WebMapを確認できます。", 220),
                    url,
                    fallbackImage
            );
        }
        if (path.equals("/features") || path.startsWith("/features/")) {
            return new PageMeta(
                    trimToLength(base + " - Features", 80),
                    trimToLength(base + " の便利機能、サバイバル拡張、各種システムを確認できます。", 220),
                    url,
                    fallbackImage
            );
        }
        if (path.equals("/profile") || path.startsWith("/profile/")) {
            return new PageMeta(
                    trimToLength(base + " - Profile", 80),
                    trimToLength("Minecraftアカウントと連携したプロフィール、投稿、自己紹介を確認できます。", 220),
                    url,
                    fallbackImage
            );
        }
        if (path.equals("/feed") || path.startsWith("/feed/")) {
            PageMeta postMeta = feedPostMeta(exchange, base, path, url, fallbackImage);
            if (postMeta != null) {
                return postMeta;
            }
            return new PageMeta(
                    trimToLength(base + " - Feed", 80),
                    trimToLength(base + " のプレイヤー投稿、Minecraftチャット連携、サーバー内の出来事を確認できます。", 220),
                    url,
                    fallbackImage
            );
        }
        if (path.equals("/webmap") || path.startsWith("/webmap/")) {
            String worldDisplayName = resolveWorldDisplayName(decodeQueryValue(queryValue(exchange, "world")));
            return new PageMeta(
                    trimToLength(buildPageTitle(base, worldDisplayName), 80),
                    trimToLength(buildPageDescription(base, worldDisplayName), 220),
                    url,
                    fallbackImage
            );
        }
        return new PageMeta(
                trimToLength(base, 80),
                trimToLength(base + " のWebサイトです。", 220),
                url,
                fallbackImage
        );
    }

    private PageMeta feedPostMeta(HttpExchange exchange, String serverName, String path, String url, String fallbackImage) {
        String prefix = "/feed/post/";
        if (!path.startsWith(prefix) || path.length() <= prefix.length()) {
            return null;
        }
        WebServiceModule service = module.getPlugin().getWebServiceModule();
        if (service == null || !service.isGloballyEnabled()) {
            return null;
        }
        String postId = decodeQueryValue(path.substring(prefix.length()).split("/", 2)[0]);
        WebPost post = service.findPostById(postId).orElse(null);
        if (post == null) {
            return new PageMeta(
                    trimToLength(serverName + " - Post", 80),
                    trimToLength("投稿が見つからないか、削除されています。", 220),
                    url,
                    fallbackImage
            );
        }
        String author = displayPostAuthor(post);
        String text = normalizeWhitespace(post.getText());
        String description = text.isBlank() ? author + " の投稿です。" : text;
        String image = firstPreviewImage(post, fallbackImage);
        return new PageMeta(
                trimToLength(author + " on " + serverName, 80),
                trimToLength(description, 220),
                url,
                absoluteImageUrl(exchange, image)
        );
    }

    private String displayPostAuthor(WebPost post) {
        String nickname = normalizeWhitespace(post.getNickname());
        if (!nickname.isBlank()) {
            return nickname;
        }
        String displayName = normalizeWhitespace(post.getDisplayName());
        if (!displayName.isBlank()) {
            return displayName;
        }
        String username = normalizeWhitespace(post.getUsername());
        return username.isBlank() ? "Player" : username;
    }

    private String firstPreviewImage(WebPost post, String fallbackImage) {
        if (post.getAttachments() != null) {
            for (WebPost.Attachment attachment : post.getAttachments()) {
                if (attachment == null || attachment.getUrl() == null || attachment.getUrl().isBlank()) {
                    continue;
                }
                String attachmentUrl = attachment.getUrl();
                if ("image".equalsIgnoreCase(attachment.getType()) || attachmentUrl.startsWith("http") || attachmentUrl.startsWith("/")) {
                    return attachmentUrl;
                }
            }
        }
        String faceUrl = normalizeWhitespace(post.getFaceUrl());
        return faceUrl.isBlank() ? fallbackImage : faceUrl;
    }

    private String absoluteImageUrl(HttpExchange exchange, String image) {
        if (image == null || image.isBlank()) {
            return absoluteRequestBase(exchange) + "/images/wiki/pexserver-hero.png";
        }
        if (image.startsWith("http://") || image.startsWith("https://")) {
            return image;
        }
        if (image.startsWith("/")) {
            return absoluteRequestBase(exchange) + image;
        }
        return absoluteRequestBase(exchange) + "/" + image;
    }

    private String buildPageTitle(String serverName, String worldDisplayName) {
        String base = (serverName == null || serverName.isBlank()) ? "Minecraft Server" : serverName.trim();
        if (worldDisplayName == null || worldDisplayName.isBlank()) {
            return base + " WebMap";
        }
        return base + " WebMap - " + worldDisplayName.trim();
    }

    private String buildPageDescription(String serverName, String worldDisplayName) {
        String base = (serverName == null || serverName.isBlank()) ? "Minecraft Server" : serverName.trim();
        StringBuilder builder = new StringBuilder();
        builder.append(base).append(" のオンライン地図です。");
        if (worldDisplayName != null && !worldDisplayName.isBlank()) {
            builder.append(" 現在のワールド: ").append(worldDisplayName.trim()).append("。");
        }
        builder.append(" プレイヤー位置、ウェイポイント、土地保護エリアを見やすく確認できます。");
        String motd = normalizeWhitespace(ServerInfoUtil.getServerDescription());
        if (!motd.isBlank() && !motd.equalsIgnoreCase(base)) {
            builder.append(" ").append(motd);
            if (!motd.endsWith("。")) {
                builder.append("。");
            }
        }
        return builder.toString().trim();
    }

    private String resolveWorldDisplayName(String worldKey) {
        if (worldKey == null || worldKey.isBlank()) {
            return "";
        }
        for (World world : module.getPlugin().getServer().getWorlds()) {
            if (world.getKey().toString().equals(worldKey) || world.getName().equals(worldKey)) {
                WebMapDimensionSettings dimension = module.getDimensionSettings(world);
                return dimension.getDisplayName();
            }
        }
        return "";
    }

    private String absoluteRequestUrl(HttpExchange exchange) {
        URI uri = exchange.getRequestURI();
        StringBuilder builder = new StringBuilder(absoluteRequestBase(exchange));
        if (uri.getRawPath() != null) {
            builder.append(uri.getRawPath());
        }
        if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
            builder.append("?").append(uri.getRawQuery());
        }
        return builder.toString();
    }

    private String absoluteRequestBase(HttpExchange exchange) {
        String host = exchange.getRequestHeaders().getFirst("Host");
        if (host == null || host.isBlank()) {
            InetSocketAddress address = exchange.getLocalAddress();
            host = address.getHostString() + ":" + address.getPort();
        }
        return "http://" + host;
    }

    private String normalizeWhitespace(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private String trimToLength(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        if (maxLength <= 1) {
            return value.substring(0, 1);
        }
        return value.substring(0, maxLength - 1).trim() + "…";
    }

    private String decodeQueryValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }

    private String escapeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private record PageMeta(String title, String description, String url, String image) {
    }

    private byte[] staticResource(String path) throws IOException {
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        if (normalized.isBlank() || normalized.contains("..") || normalized.startsWith("/") || normalized.startsWith("\\")) {
            return null;
        }
        try (InputStream inputStream = module.getPlugin().getClass().getClassLoader().getResourceAsStream("website/" + normalized)) {
            if (inputStream == null) {
                return null;
            }
            return inputStream.readAllBytes();
        }
    }

    private String decodeRequestPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return rawPath;
        }
        try {
            return URLDecoder.decode(rawPath, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isStaticAssetPath(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        return lower.startsWith("/assets/")
                || lower.startsWith("/images/")
                || lower.startsWith("/pwa/")
                || lower.equals("/manifest.webmanifest")
                || lower.endsWith(".ico")
                || lower.endsWith(".svg")
                || lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".css")
                || lower.endsWith(".js")
                || lower.endsWith(".map");
    }

    private void handleAuthRegister(HttpExchange exchange) throws IOException {
        WebServiceModule service = webServiceOrReject(exchange);
        if (service == null) {
            return;
        }
        if (!guardJsonPost(exchange) || !guardSameOrigin(exchange)) {
            return;
        }
        Map<String, Object> request = readJsonObject(exchange);
        WebServiceModule.AuthResult result = service.register(
                stringValue(request.get("username")),
                stringValue(request.get("code")),
                stringValue(request.get("password"))
        );
        writeAuthResult(exchange, service, result);
    }

    private void handleProfileUpdate(HttpExchange exchange) throws IOException {
        WebServiceModule service = webServiceOrReject(exchange);
        if (service == null) {
            return;
        }
        if (!guardAuthenticatedJsonPost(exchange, service)) {
            return;
        }
        WebServiceModule.ProfileResult result = service.updateProfile(bearerToken(exchange), readJsonObject(exchange));
        if (!result.success()) {
            writeJson(exchange, Map.of("success", false, "message", result.message()), 0);
            return;
        }
        writeJson(exchange, Map.of(
                "success", true,
                "profile", service.profilePayload(result.profile())
        ), 0);
    }

    private void handleFeed(HttpExchange exchange) throws IOException {
        WebServiceModule service = webServiceOrReject(exchange);
        if (service == null) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writePlain(exchange, 405, "Method Not Allowed");
            return;
        }
        long since = parseLong(queryValue(exchange, "since"), 0L);
        int limit = parseInt(queryValue(exchange, "limit"), 50);
        String viewerUuid = service.sessionUuid(bearerToken(exchange)).orElse("");
        List<WebPost> listed = service.listPosts(since, limit);
        // 表示回数を記録（同一IPは30分のクールダウン）
        service.recordViews(listed, clientIp(exchange));
        List<Map<String, Object>> posts = listed.stream()
                .map(post -> service.postPayload(post, viewerUuid))
                .toList();
        writeJson(exchange, Map.of(
                "success", true,
                "posts", posts,
                "retentionDays", service.getFeedRetentionDays(),
                "updatedAt", System.currentTimeMillis()
        ), 0);
    }

    private void handleFeedUpdates(HttpExchange exchange) throws IOException {
        WebServiceModule service = webServiceOrReject(exchange);
        if (service == null) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writePlain(exchange, 405, "Method Not Allowed");
            return;
        }
        long since = parseLong(queryValue(exchange, "since"), 0L);
        long deadline = System.currentTimeMillis() + 15_000L;
        List<WebPost> posts;
        do {
            posts = service.listPosts(since, 50);
            if (!posts.isEmpty() || System.currentTimeMillis() >= deadline) {
                break;
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        } while (true);
        String viewerUuid = service.sessionUuid(bearerToken(exchange)).orElse("");
        service.recordViews(posts, clientIp(exchange));
        List<Map<String, Object>> payloadPosts = posts.stream().map(post -> service.postPayload(post, viewerUuid)).toList();
        writeJson(exchange, Map.of(
                "success", true,
                "posts", payloadPosts,
                "updatedAt", System.currentTimeMillis()
        ), 0);
    }

    /**
     * 投稿の添付画像を配信する ( /api/v1/feed/image/{postId}/{index} )。
     * Web でアップロードされた data URL 画像をデコードして返し、
     * 外部 URL (Discord CDN 等) の場合はリダイレクトする。
     * Minecraft のクリック可能テキストからの閲覧口。
     */
    private void handleFeedImage(HttpExchange exchange) throws IOException {
        WebServiceModule service = webServiceOrReject(exchange);
        if (service == null) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writePlain(exchange, 405, "Method Not Allowed");
            return;
        }
        String[] segments = exchange.getRequestURI().getPath().split("/");
        // ["", "api", "v1", "feed", "image", postId, index]
        if (segments.length < 7) {
            writePlain(exchange, 404, "Not Found");
            return;
        }
        WebPost post = service.findPostById(segments[5]).orElse(null);
        int index = parseInt(segments[6], -1);
        if (post == null || index < 0 || index >= post.getAttachments().size()) {
            writePlain(exchange, 404, "Not Found");
            return;
        }
        String url = post.getAttachments().get(index).getUrl();
        if (url.startsWith("http://") || url.startsWith("https://")) {
            securityHeaders(exchange);
            exchange.getResponseHeaders().set("Location", url);
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
            return;
        }
        String mime = org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService.FeedTextUtil.imageDataUrlMime(url);
        byte[] bytes = org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService.FeedTextUtil.decodeImageDataUrl(url);
        if (mime == null || bytes == null) {
            writePlain(exchange, 404, "Not Found");
            return;
        }
        securityHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", mime);
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400, immutable");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    /** 個人情報の開示・訂正・削除・利用停止の申請を受け付ける。 */
    private void handlePrivacyRequest(HttpExchange exchange) throws IOException {
        WebServiceModule service = webServiceOrReject(exchange);
        if (service == null) {
            return;
        }
        if (!guardAuthenticatedJsonPost(exchange, service)) {
            return;
        }
        Map<String, Object> request = readJsonObject(exchange);
        WebServiceModule.PrivacyRequestResult result = service.createPrivacyRequest(
                bearerToken(exchange),
                stringValue(request.get("type")),
                stringValue(request.get("detail"))
        );
        if (!result.success()) {
            writeJson(exchange, Map.of("success", false, "message", result.message()), 0);
            return;
        }
        writeJson(exchange, Map.of(
                "success", true,
                "request", service.privacyRequestPayload(result.request())
        ), 0);
    }

    /** 自分の申請履歴を返す（本人のみ）。 */
    private void handlePrivacyRequests(HttpExchange exchange) throws IOException {
        WebServiceModule service = webServiceOrReject(exchange);
        if (service == null) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writePlain(exchange, 405, "Method Not Allowed");
            return;
        }
        List<Map<String, Object>> requests = service.listOwnPrivacyRequests(bearerToken(exchange)).stream()
                .map(service::privacyRequestPayload)
                .toList();
        writeJson(exchange, Map.of("success", true, "requests", requests), 0);
    }

    /** 開示請求のセルフサービス即時応答: 自分のプロフィール・投稿・請求履歴をまとめて返す。 */
    private void handlePrivacyDisclosure(HttpExchange exchange) throws IOException {
        WebServiceModule service = webServiceOrReject(exchange);
        if (service == null) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writePlain(exchange, 405, "Method Not Allowed");
            return;
        }
        Optional<Map<String, Object>> payload = service.buildDisclosurePayload(bearerToken(exchange));
        if (payload.isEmpty()) {
            writeJson(exchange, Map.of("success", false, "message", "ログインが必要です"), 0);
            return;
        }
        Map<String, Object> body = new LinkedHashMap<>(payload.get());
        body.put("success", true);
        writeJson(exchange, body, 0);
    }

    /** 連携アカウントが現在OPかどうかを返すだけの軽量な認可チェック。Web管理ページのゲートに使う。 */
    private void handleAdminMe(HttpExchange exchange) throws IOException {
        WebServiceModule service = webServiceOrReject(exchange);
        if (service == null) {
            return;
        }
        if (!guardApi(exchange)) {
            return;
        }
        writeJson(exchange, Map.of("isAdmin", service.isSessionAdmin(bearerToken(exchange))), 0);
    }

    /** 全申請一覧（Web管理ページ用、OPのみ）。 */
    private void handleAdminPrivacyRequests(HttpExchange exchange) throws IOException {
        WebServiceModule service = webServiceOrReject(exchange);
        if (service == null) {
            return;
        }
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            writePlain(exchange, 405, "Method Not Allowed");
            return;
        }
        if (!service.isSessionAdmin(bearerToken(exchange))) {
            writeJson(exchange, Map.of("success", false, "message", "権限がありません"), 0);
            return;
        }
        List<Map<String, Object>> requests = service.listAllPrivacyRequests().stream()
                .map(service::privacyRequestPayload)
                .toList();
        writeJson(exchange, Map.of("success", true, "requests", requests), 0);
    }

    /** 申請を対応済みにする（Web管理ページ用、OPのみ）。 */
    private void handleAdminPrivacyResolve(HttpExchange exchange) throws IOException {
        WebServiceModule service = webServiceOrReject(exchange);
        if (service == null) {
            return;
        }
        if (!guardAuthenticatedJsonPost(exchange, service)) {
            return;
        }
        String token = bearerToken(exchange);
        if (!service.isSessionAdmin(token)) {
            writeJson(exchange, Map.of("success", false, "message", "権限がありません"), 0);
            return;
        }
        Map<String, Object> request = readJsonObject(exchange);
        boolean resolved = service.resolvePrivacyRequest(stringValue(request.get("id")));
        writeJson(exchange, Map.of("success", resolved, "message", resolved ? "OK" : "対応できませんでした"), 0);
    }

    private void handleFeedPost(HttpExchange exchange) throws IOException {
        WebServiceModule service = webServiceOrReject(exchange);
        if (service == null) {
            return;
        }
        if (!guardAuthenticatedJsonPost(exchange, service)) {
            return;
        }
        Map<String, Object> request = readJsonObject(exchange);
        WebServiceModule.PostResult result = service.createWebPost(
                bearerToken(exchange),
                stringValue(request.get("text")),
                attachmentList(exchange, request.get("attachments"))
        );
        if (!result.success()) {
            writeJson(exchange, Map.of("success", false, "message", result.message()), 0);
            return;
        }
        writeJson(exchange, Map.of(
                "success", true,
                "post", service.postPayload(result.post())
        ), 0);
    }

    private void handleFeedLike(HttpExchange exchange) throws IOException {
        handleFeedReaction(exchange, "like");
    }

    private void handleFeedRepost(HttpExchange exchange) throws IOException {
        handleFeedReaction(exchange, "repost");
    }

    private void handleFeedReaction(HttpExchange exchange, String action) throws IOException {
        WebServiceModule service = webServiceOrReject(exchange);
        if (service == null) {
            return;
        }
        if (!guardAuthenticatedJsonPost(exchange, service)) {
            return;
        }
        Map<String, Object> request = readJsonObject(exchange);
        String token = bearerToken(exchange);
        WebServiceModule.PostResult result = "repost".equals(action)
                ? service.repostPost(token, stringValue(request.get("postId")))
                : service.likePost(token, stringValue(request.get("postId")));
        if (!result.success()) {
            writeJson(exchange, Map.of("success", false, "message", result.message()), 0);
            return;
        }
        String viewerUuid = service.sessionUuid(token).orElse("");
        writeJson(exchange, Map.of(
                "success", true,
                "post", service.postPayload(result.post(), viewerUuid)
        ), 0);
    }

    private void handleFeedDelete(HttpExchange exchange) throws IOException {
        WebServiceModule service = webServiceOrReject(exchange);
        if (service == null) {
            return;
        }
        if (!guardAuthenticatedJsonPost(exchange, service)) {
            return;
        }
        Map<String, Object> request = readJsonObject(exchange);
        boolean deleted = service.deletePost(bearerToken(exchange), stringValue(request.get("postId")));
        writeJson(exchange, Map.of("success", deleted, "message", deleted ? "OK" : "削除できませんでした"), 0);
    }

    private void handleAuthLogin(HttpExchange exchange) throws IOException {
        WebServiceModule service = webServiceOrReject(exchange);
        if (service == null) {
            return;
        }
        if (!guardJsonPost(exchange) || !guardSameOrigin(exchange)) {
            return;
        }
        Map<String, Object> request = readJsonObject(exchange);
        WebServiceModule.AuthResult result = service.login(
                stringValue(request.get("username")),
                stringValue(request.get("password"))
        );
        writeAuthResult(exchange, service, result);
    }

    private void handleAuthLogout(HttpExchange exchange) throws IOException {
        WebServiceModule service = webServiceOrReject(exchange);
        if (service == null) {
            return;
        }
        if (!guardAuthenticatedJsonPost(exchange, service)) {
            return;
        }
        service.logout(bearerToken(exchange));
        clearSessionCookie(exchange);
        writeJson(exchange, Map.of("success", true), 0);
    }

    private void handleAuthMe(HttpExchange exchange) throws IOException {
        WebServiceModule service = webServiceOrReject(exchange);
        if (service == null) {
            return;
        }
        if (!guardApi(exchange)) {
            return;
        }
        String token = bearerToken(exchange);
        WebProfile profile = service.findProfileBySession(token).orElse(null);
        if (profile == null) {
            writeJson(exchange, Map.of("authenticated", false), 0);
            return;
        }
        setSessionCookie(exchange, token);
        writeJson(exchange, Map.of(
                "authenticated", true,
                "token", token,
                "profile", service.profilePayload(profile),
                "csrfToken", service.csrfTokenForSession(token)
        ), 0);
    }

    private void writeAuthResult(HttpExchange exchange, WebServiceModule service, WebServiceModule.AuthResult result) throws IOException {
        if (!result.success()) {
            writeJson(exchange, Map.of(
                    "success", false,
                    "message", result.message()
            ), 0);
            return;
        }
        setSessionCookie(exchange, result.token());
        writeJson(exchange, Map.of(
                "success", true,
                "token", result.token(),
                "csrfToken", result.csrfToken(),
                "profile", service.profilePayload(result.profile())
        ), 0);
    }

    private WebServiceModule webServiceOrReject(HttpExchange exchange) throws IOException {
        WebServiceModule service = module.getPlugin().getWebServiceModule();
        if (service == null || !service.isGloballyEnabled()) {
            writeJson(exchange, Map.of("success", false, "message", "WebService disabled"), 0);
            return null;
        }
        if (!guardApi(exchange)) {
            return null;
        }
        return service;
    }

    private boolean guardWebMap(HttpExchange exchange) throws IOException {
        WebServiceModule service = module.getPlugin().getWebServiceModule();
        if (service != null && service.isWebMapAccessEnabled()) {
            return true;
        }
        writePlain(exchange, 404, "WebMap disabled by WebService safety gate");
        return false;
    }

    private Map<String, Object> readJsonObject(HttpExchange exchange) throws IOException {
        try (InputStream inputStream = exchange.getRequestBody()) {
            Object payload = GSON.fromJson(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8), Object.class);
            if (payload instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() instanceof String key) {
                        result.put(key, entry.getValue());
                    }
                }
                return result;
            }
        } catch (RuntimeException ignored) {
        }
        return Map.of();
    }

    private static final String SESSION_COOKIE_NAME = "bs_token";
    private static final int SESSION_COOKIE_MAX_AGE_SECONDS = 7 * 24 * 60 * 60;

    private String bearerToken(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        return cookieValue(exchange, SESSION_COOKIE_NAME);
    }

    private String cookieValue(HttpExchange exchange, String name) {
        String header = exchange.getRequestHeaders().getFirst("Cookie");
        if (header == null || header.isBlank()) {
            return "";
        }
        for (String part : header.split(";")) {
            String trimmed = part.trim();
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            if (trimmed.substring(0, separator).equals(name)) {
                return trimmed.substring(separator + 1).trim();
            }
        }
        return "";
    }

    private void setSessionCookie(HttpExchange exchange, String token) {
        exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE_NAME + "=" + token
                + "; Path=/; Max-Age=" + SESSION_COOKIE_MAX_AGE_SECONDS + "; SameSite=Lax; HttpOnly");
    }

    private void clearSessionCookie(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE_NAME + "=; Path=/; Max-Age=0; SameSite=Lax; HttpOnly");
    }

    private String stringValue(Object value) {
        return value instanceof String string ? string : "";
    }

    private List<WebPost.Attachment> attachmentList(HttpExchange exchange, Object value) {
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        List<WebPost.Attachment> attachments = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object url = map.get("url");
            if (!(url instanceof String urlString) || urlString.isBlank()) {
                continue;
            }
            if (!urlString.startsWith("data:image/")) {
                continue;
            }
            StoredUpload upload = storeUpload(exchange, urlString);
            if (upload == null) {
                continue;
            }
            WebPost.Attachment attachment = new WebPost.Attachment();
            attachment.setType("image");
            attachment.setUrl(upload.url());
            if (map.get("name") instanceof String name && !name.isBlank()) {
                attachment.setName(org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService.FeedTextUtil
                        .sanitizeFileName(name, ""));
            }
            Object width = map.get("width");
            Object height = map.get("height");
            if (width instanceof Number number) {
                attachment.setWidth(number.intValue());
            }
            if (height instanceof Number number) {
                attachment.setHeight(number.intValue());
            }
            attachments.add(attachment);
            if (attachments.size() >= 4) {
                break;
            }
        }
        return attachments;
    }

    private StoredUpload storeUpload(HttpExchange exchange, String dataUrl) {
        int commaIndex = dataUrl == null ? -1 : dataUrl.indexOf(',');
        if (commaIndex <= 0) {
            return null;
        }
        String header = dataUrl.substring(0, commaIndex).toLowerCase(Locale.ROOT);
        String extension = imageExtension(header);
        if (extension.isBlank() || !header.contains(";base64")) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUrl.substring(commaIndex + 1));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        if (bytes.length == 0 || bytes.length > 1_500_000) {
            return null;
        }
        String fileName = java.util.UUID.randomUUID() + "." + extension;
        File directory = new File(module.getPlugin().getConfigManager().getBaseDir(), "WebService/uploads/feed");
        if (!directory.exists() && !directory.mkdirs()) {
            return null;
        }
        File file = new File(directory, fileName);
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(bytes);
        } catch (IOException ignored) {
            return null;
        }
        return new StoredUpload(absoluteRequestBase(exchange) + "/uploads/feed/" + fileName);
    }

    private String imageExtension(String dataUrlHeader) {
        if (dataUrlHeader == null) {
            return "";
        }
        if (dataUrlHeader.startsWith("data:image/jpeg") || dataUrlHeader.startsWith("data:image/jpg")) {
            return "jpg";
        }
        if (dataUrlHeader.startsWith("data:image/png")) {
            return "png";
        }
        if (dataUrlHeader.startsWith("data:image/gif")) {
            return "gif";
        }
        if (dataUrlHeader.startsWith("data:image/webp")) {
            return "webp";
        }
        return "";
    }

    private record StoredUpload(String url) {
    }

    private World resolveWorldByName(String worldName) {
        for (World world : module.getPlugin().getServer().getWorlds()) {
            if (world.getName().equals(worldName)) {
                return world;
            }
        }
        return null;
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String queryValue(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null || query.isBlank()) {
            return "";
        }
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            if (pair.substring(0, separator).equals(key)) {
                return pair.substring(separator + 1);
            }
        }
        return "";
    }

    private int parseColor(String hex) {
        if (hex == null || hex.isBlank()) {
            return 0x00000000;
        }
        return (0xFF << 24) | Integer.parseInt(hex.substring(1), 16);
    }

    private String mimeType(String path) {
        if (path.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".ico")) return "image/x-icon";
        if (path.endsWith(".webmanifest")) return "application/manifest+json; charset=utf-8";
        if (path.endsWith(".json")) return "application/json; charset=utf-8";
        return "text/html; charset=utf-8";
    }

    private void securityHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("Referrer-Policy", "same-origin");
        exchange.getResponseHeaders().set("Permissions-Policy", "geolocation=(), microphone=(), camera=()");
    }

    private boolean guardJsonPost(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writePlain(exchange, 405, "Method Not Allowed");
            return false;
        }
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).contains("application/json")) {
            writeJson(exchange, Map.of("success", false, "message", "JSON request required"), 0);
            return false;
        }
        return true;
    }

    private boolean guardAuthenticatedJsonPost(HttpExchange exchange, WebServiceModule service) throws IOException {
        return guardJsonPost(exchange) && guardSameOrigin(exchange) && guardCsrf(exchange, service);
    }

    private boolean guardCsrf(HttpExchange exchange, WebServiceModule service) throws IOException {
        String token = bearerToken(exchange);
        String csrfToken = exchange.getRequestHeaders().getFirst("X-BetterSurvival-CSRF");
        if (!service.verifyCsrfToken(token, csrfToken)) {
            writeJson(exchange, Map.of("success", false, "message", "CSRF validation failed"), 403);
            return false;
        }
        return true;
    }

    private boolean guardSameOrigin(HttpExchange exchange) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        String referer = exchange.getRequestHeaders().getFirst("Referer");
        java.util.Set<String> allowed = allowedOriginBases(exchange);

        if (origin != null && !origin.isBlank()) {
            String normalizedOrigin = origin.trim();
            for (String base : allowed) {
                if (base.equalsIgnoreCase(normalizedOrigin)) {
                    return true;
                }
            }
            rejectCrossOrigin(exchange, "origin", normalizedOrigin, allowed);
            return false;
        }
        if (referer != null && !referer.isBlank()) {
            String normalizedReferer = referer.trim();
            for (String base : allowed) {
                if (normalizedReferer.equalsIgnoreCase(base)
                        || normalizedReferer.startsWith(base + "/")
                        || normalizedReferer.startsWith(base + "?")
                        || normalizedReferer.startsWith(base + "#")) {
                    return true;
                }
            }
            rejectCrossOrigin(exchange, "referer", normalizedReferer, allowed);
            return false;
        }
        return true;
    }


    private java.util.Set<String> allowedOriginBases(HttpExchange exchange) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();

        String fwdHost = firstHeaderValue(exchange, "X-Forwarded-Host");
        String fwdProto = firstHeaderValue(exchange, "X-Forwarded-Proto");
        String fwdPort = firstHeaderValue(exchange, "X-Forwarded-Port");
        String hostHeader = exchange.getRequestHeaders().getFirst("Host");

        if (fwdHost != null && !fwdHost.isBlank()) {
            String hostWithPort = fwdHost;
            if (fwdPort != null && !fwdPort.isBlank() && !fwdHost.contains(":")) {
                if (!("80".equals(fwdPort) && "http".equalsIgnoreCase(fwdProto))
                        && !("443".equals(fwdPort) && "https".equalsIgnoreCase(fwdProto))) {
                    hostWithPort = fwdHost + ":" + fwdPort;
                }
            }
            if (fwdProto != null && !fwdProto.isBlank()) {
                set.add(fwdProto.toLowerCase(Locale.ROOT) + "://" + hostWithPort);
            } else {
                set.add("http://" + hostWithPort);
                set.add("https://" + hostWithPort);
            }
            set.add("http://" + fwdHost);
            set.add("https://" + fwdHost);
        }

        if (hostHeader != null && !hostHeader.isBlank()) {
            set.add("http://" + hostHeader);
            set.add("https://" + hostHeader);
        }

        InetSocketAddress local = exchange.getLocalAddress();
        if (local != null) {
            String localBase = local.getHostString() + ":" + local.getPort();
            set.add("http://" + localBase);
            set.add("https://" + localBase);
        }

        return set;
    }

    private String firstHeaderValue(HttpExchange exchange, String name) {
        String raw = exchange.getRequestHeaders().getFirst(name);
        if (raw == null || raw.isBlank()) return null;
        int comma = raw.indexOf(',');
        return (comma >= 0 ? raw.substring(0, comma) : raw).trim();
    }

    private void rejectCrossOrigin(HttpExchange exchange, String field, String actual,
                                    java.util.Set<String> expected) throws IOException {
        try {
            module.getPlugin().getLogger().warning(
                    "[WebMap] Cross-origin request blocked. " + field + "=\"" + actual
                            + "\" expected one of " + expected
                            + " path=" + exchange.getRequestURI());
        } catch (Throwable ignored) {
            // ignore
        }
        writeJson(exchange, Map.of(
                "success", false,
                "message", "Cross-origin request blocked",
                "debug", Map.of(
                        "field", field,
                        "actual", actual,
                        "expected", new java.util.ArrayList<>(expected)
                )
        ), 403);
    }

    private boolean guardApi(HttpExchange exchange) throws IOException {
        return guard(exchange, apiLimiter, "api");
    }

    private boolean guardTile(HttpExchange exchange) throws IOException {
        return guard(exchange, tileLimiter, "tile");
    }

    private boolean guard(HttpExchange exchange, WebMapRateLimiter limiter, String prefix) throws IOException {
        String key = prefix + ":" + clientIp(exchange);
        if (limiter.allow(key)) {
            return true;
        }
        exchange.getResponseHeaders().set("Retry-After", "1");
        writePlain(exchange, 429, "Too Many Requests");
        return false;
    }
}


