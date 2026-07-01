package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.World;
import org.pexserver.koukunn.bettersurvival.Core.Util.ServerInfoUtil;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService.WebProfile;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService.WebServiceModule;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        server = HttpServer.create(new InetSocketAddress(host, settings.getPort()), 0);
        server.createContext("/api/v1/bootstrap", safe(this::handleBootstrap));
        server.createContext("/api/v1/status", safe(this::handleStatus));
        server.createContext("/api/v1/auth/register", safe(this::handleAuthRegister));
        server.createContext("/api/v1/auth/login", safe(this::handleAuthLogin));
        server.createContext("/api/v1/auth/logout", safe(this::handleAuthLogout));
        server.createContext("/api/v1/auth/me", safe(this::handleAuthMe));
        server.createContext("/api/v1/worlds", safe(this::handleApiWorlds));
        server.createContext("/api/v1/players", safe(this::handleApiPlayers));
        server.createContext("/api/status", safe(this::handleStatus));
        server.createContext("/tiles", safe(this::handleTiles));
        server.createContext("/", safe(this::handleStatic));
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
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
        if (server != null) {
            server.stop(0);
            server = null;
        }
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
        String path = exchange.getRequestURI().getPath();
        if (path == null || path.equals("/") || path.isBlank() || path.equals("/webmap") || path.startsWith("/webmap/")) {
            path = "/index.html";
        }
        if (path.startsWith("/api/") || path.startsWith("/tiles")) {
            writePlain(exchange, 404, "Not Found");
            return;
        }
        byte[] body = staticResource(path);
        if (body == null) {
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
        byte[] body = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=" + Math.max(0, maxAgeSeconds));
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private void writePlain(HttpExchange exchange, int status, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private void writePng(HttpExchange exchange, byte[] body, String etag, int maxAgeSeconds) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "image/png");
        exchange.getResponseHeaders().set("Cache-Control", "public, max-age=" + Math.max(0, maxAgeSeconds));
        exchange.getResponseHeaders().set("ETag", etag);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private void writeEmptyTile(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(TILE_SIZE, TILE_SIZE, BufferedImage.TYPE_INT_ARGB), "png", byteStream);
        writePng(exchange, byteStream.toByteArray(), "\"empty\"", 30);
    }

    private byte[] renderIndexHtml(HttpExchange exchange, byte[] templateBytes) {
        String serverName = ServerInfoUtil.getServerName();
        String worldDisplayName = resolveWorldDisplayName(decodeQueryValue(queryValue(exchange, "world")));
        String title = trimToLength(buildPageTitle(serverName, worldDisplayName), 80);
        String description = trimToLength(buildPageDescription(serverName, worldDisplayName), 220);
        String url = absoluteRequestUrl(exchange);
        String image = absoluteRequestBase(exchange) + "/images/og.png";
        String html = new String(templateBytes, StandardCharsets.UTF_8)
                .replace(PLACEHOLDER_TITLE, escapeHtml(title))
                .replace(PLACEHOLDER_DESCRIPTION, escapeHtml(description))
                .replace(PLACEHOLDER_SITE_NAME, escapeHtml(serverName))
                .replace(PLACEHOLDER_URL, escapeHtml(url))
                .replace(PLACEHOLDER_IMAGE, escapeHtml(image))
                .replace(PLACEHOLDER_IMAGE_ALT, escapeHtml(title));
        return html.getBytes(StandardCharsets.UTF_8);
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
        builder.append(" プレイヤー位置、ウェイポイント、チャンク情報を見やすく確認できます。");
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

    private byte[] staticResource(String path) throws IOException {
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        try (InputStream inputStream = module.getPlugin().getClass().getClassLoader().getResourceAsStream("website/" + normalized)) {
            if (inputStream == null) {
                return null;
            }
            return inputStream.readAllBytes();
        }
    }

    private void handleAuthRegister(HttpExchange exchange) throws IOException {
        WebServiceModule service = webServiceOrReject(exchange);
        if (service == null) {
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writePlain(exchange, 405, "Method Not Allowed");
            return;
        }
        Map<String, Object> request = readJsonObject(exchange);
        WebServiceModule.AuthResult result = service.register(
                stringValue(request.get("username")),
                stringValue(request.get("code")),
                stringValue(request.get("email")),
                stringValue(request.get("password")),
                booleanValue(request.get("passkeyRequested"))
        );
        writeAuthResult(exchange, service, result);
    }

    private void handleAuthLogin(HttpExchange exchange) throws IOException {
        WebServiceModule service = webServiceOrReject(exchange);
        if (service == null) {
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            writePlain(exchange, 405, "Method Not Allowed");
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
        service.logout(bearerToken(exchange));
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
        WebProfile profile = service.findProfileBySession(bearerToken(exchange)).orElse(null);
        if (profile == null) {
            writeJson(exchange, Map.of("authenticated", false), 0);
            return;
        }
        writeJson(exchange, Map.of(
                "authenticated", true,
                "profile", service.profilePayload(profile)
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
        writeJson(exchange, Map.of(
                "success", true,
                "token", result.token(),
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
        if (module.isGloballyEnabled()) {
            return true;
        }
        writePlain(exchange, 404, "WebMap disabled");
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

    private String bearerToken(HttpExchange exchange) {
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring("Bearer ".length()).trim();
    }

    private String stringValue(Object value) {
        return value instanceof String string ? string : "";
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool && bool;
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

    private boolean guardApi(HttpExchange exchange) throws IOException {
        return guard(exchange, apiLimiter, "api");
    }

    private boolean guardTile(HttpExchange exchange) throws IOException {
        return guard(exchange, tileLimiter, "tile");
    }

    private boolean guard(HttpExchange exchange, WebMapRateLimiter limiter, String prefix) throws IOException {
        String remote = "unknown";
        if (exchange.getRemoteAddress() != null && exchange.getRemoteAddress().getAddress() != null) {
            remote = exchange.getRemoteAddress().getAddress().getHostAddress();
        }
        String key = prefix + ":" + remote;
        if (limiter.allow(key)) {
            return true;
        }
        exchange.getResponseHeaders().set("Retry-After", "1");
        writePlain(exchange, 429, "Too Many Requests");
        return false;
    }
}
