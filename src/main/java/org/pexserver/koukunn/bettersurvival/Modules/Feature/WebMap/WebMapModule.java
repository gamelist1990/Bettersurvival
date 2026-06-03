package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Api.McApiClient;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public class WebMapModule implements Listener {
    private static final int PLAYER_CAPTURE_RADIUS = 3;
    private static final int PLAYER_SWEEP_RADIUS = 3;
    private static final int CHUNKS_PER_TILE = 32;
    private static final NmsMapColorResolver NMS_MAP_COLOR_RESOLVER = NmsMapColorResolver.create();

    private final Loader plugin;
    private final WebMapStore store;
    private final WebMapDataStore dataStore;
    private final WebMapHttpServer httpServer;
    private final Map<UUID, String> lastPlayerChunk = new ConcurrentHashMap<>();
    private final Map<String, ChunkGenJob> chunkGenJobs = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<QueuedChunkCapture> urgentChunkCaptures = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<QueuedChunkCapture> normalChunkCaptures = new ConcurrentLinkedDeque<>();
    private final java.util.Set<String> queuedChunkKeys = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> recentChunkCaptures = new ConcurrentHashMap<>();
    private final Map<String, Long> changedTiles = new ConcurrentHashMap<>();
    private final Map<String, BossBar> chunkGenBossBars = new ConcurrentHashMap<>();
    private final Map<String, List<WebMapMarkerRecord>> markerSnapshots = new ConcurrentHashMap<>();
    private final Map<String, Map<String, WebMapMarkerRecord>> waypointSnapshots = new ConcurrentHashMap<>();
    private final AtomicInteger pendingChunkWork = new AtomicInteger();
    private final AtomicLong nmsColorHits = new AtomicLong();
    private final AtomicLong fallbackColorHits = new AtomicLong();
    private final AtomicLong recentChunkCaptureCleanupAt = new AtomicLong();
    private BossBar globalTpsBar;

    private volatile boolean globalEnabled;
    private volatile boolean shutdownRequested;
    private volatile boolean shutdownComplete;
    private WebMapSettings settings;
    private BukkitTask dirtyFlushTask;
    private BukkitTask featureSyncTask;
    private BukkitTask chunkGenTask;
    private BukkitTask chunkCaptureTask;
    private BukkitTask nearbySweepTask;
    private BukkitTask markerRefreshTask;
    private BukkitTask shutdownWatchTask;

    public WebMapModule(Loader plugin) {
        this.plugin = plugin;
        this.store = new WebMapStore(plugin.getConfigManager());
        this.dataStore = new WebMapDataStore(store);
        this.httpServer = new WebMapHttpServer(this);
        this.settings = store.loadSettings();
        syncKnownWorlds();
        store.saveSettings(settings);
        refreshGlobalEnabled();
        restorePersistedMarkerSnapshots();
        refreshMarkerSnapshots();
        startTasks();
        logMapColorProcessingMode();
        syncRuntimeState();
    }

    public Loader getPlugin() {
        return plugin;
    }

    public WebMapSettings getSettings() {
        return settings;
    }

    public boolean saveSettings(WebMapSettings settings) {
        this.settings = settings;
        syncKnownWorlds();
        boolean saved = store.saveSettings(settings);
        syncRuntimeState();
        return saved;
    }

    public void openSettings(Player player) {
        syncKnownWorlds();
        WebMapSettingsMenu.openMainMenu(player, this);
    }

    public boolean isGloballyEnabled() {
        return globalEnabled;
    }

    public boolean isServerRunning() {
        return httpServer.isRunning();
    }

    public String getPublicUrl() {
        String host;
        if (settings.isPublicAccess()) {
            String configuredIp = plugin.getServer().getIp();
            host = configuredIp == null || configuredIp.isBlank() ? "localhost" : configuredIp;
        } else {
            host = "127.0.0.1";
        }
        return "http://" + host + ":" + settings.getPort() + "/";
    }

    public WebMapDimensionSettings getDimensionSettings(World world) {
        syncKnownWorlds();
        return store.ensureDimensionSettings(settings, world);
    }

    public List<WebMapDimensionSettings> getDimensionSettingsList() {
        syncKnownWorlds();
        List<WebMapDimensionSettings> dimensions = new ArrayList<>(settings.getDimensions().values());
        dimensions.sort(Comparator.comparing(WebMapDimensionSettings::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        return dimensions;
    }

    public void togglePaused() {
        settings.setPaused(!settings.isPaused());
        store.saveSettings(settings);
        syncRuntimeState();
    }

    public void togglePublicAccess() {
        settings.setPublicAccess(!settings.isPublicAccess());
        store.saveSettings(settings);
        syncRuntimeState();
    }

    public void toggleAutoTrackPlayers() {
        settings.setAutoTrackPlayers(!settings.isAutoTrackPlayers());
        store.saveSettings(settings);
    }

    public void toggleEventPlayerMove() {
        settings.getEvents().setPlayerMove(!settings.getEvents().isPlayerMove());
        store.saveSettings(settings);
    }

    public void toggleShowTpsBar() {
        settings.setShowTpsBar(!settings.isShowTpsBar());
        store.saveSettings(settings);
        updateGlobalTpsBar();
    }

    public void toggleEventChunkLoad() {
        settings.getEvents().setChunkLoad(!settings.getEvents().isChunkLoad());
        store.saveSettings(settings);
    }

    public void toggleEventChunkPopulate() {
        settings.getEvents().setChunkPopulate(!settings.getEvents().isChunkPopulate());
        store.saveSettings(settings);
    }

    public void toggleEventBlockUpdate() {
        settings.getEvents().setBlockUpdate(!settings.getEvents().isBlockUpdate());
        store.saveSettings(settings);
    }

    public void restartServer() {
        syncRuntimeState(true);
    }

    public void updatePort(int port) {
        settings.setPort(Math.max(1024, Math.min(65535, port)));
        store.saveSettings(settings);
        syncRuntimeState(true);
    }

    public void toggleWorldVisible(String worldKey) {
        WebMapDimensionSettings dimension = settings.getDimensions().get(worldKey);
        if (dimension == null) {
            return;
        }
        dimension.setVisible(!dimension.isVisible());
        store.saveSettings(settings);
    }

    public void toggleWorldTracking(String worldKey) {
        WebMapDimensionSettings dimension = settings.getDimensions().get(worldKey);
        if (dimension == null) {
            return;
        }
        dimension.setAutoTrack(!dimension.isAutoTrack());
        store.saveSettings(settings);
    }

    public void toggleChunkGen(String worldKey) {
        WebMapDimensionSettings dimension = settings.getDimensions().get(worldKey);
        if (dimension == null) {
            return;
        }
        dimension.setChunkGenEnabled(!dimension.isChunkGenEnabled());
        if (!dimension.isChunkGenEnabled()) {
            chunkGenJobs.remove(worldKey);
            hideChunkGenBossBar(worldKey);
        }
        store.saveSettings(settings);
    }

    public void stopAllChunkGen() {
        for (WebMapDimensionSettings dimension : settings.getDimensions().values()) {
            dimension.setChunkGenEnabled(false);
        }
        chunkGenJobs.clear();
        hideAllChunkGenBossBars();
        store.saveSettings(settings);
    }

    public int getChunkCount(String worldKey) {
        return dataStore.chunkCount(worldKey);
    }

    public Collection<WebMapChunkRecord> snapshotChunks(String worldKey) {
        return dataStore.snapshotChunks(worldKey);
    }

    public WebMapChunkRecord getChunk(String worldKey, int chunkX, int chunkZ) {
        return dataStore.getChunk(worldKey, chunkX, chunkZ);
    }

    public List<Map<String, Object>> getOnlinePlayersSnapshot() {
        List<Map<String, Object>> players = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Map<String, Object> row = new ConcurrentHashMap<>();
            String worldKey = player.getWorld().getKey().toString();
            String faceUrl = McApiClient.getFaceUrl(player.getUniqueId(), player.getName(), org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil.isBedrock(player));
            row.put("name", player.getName());
            row.put("displayName", player.getName());
            row.put("uuid", player.getUniqueId().toString());
            row.put("world", player.getWorld().getName());
            row.put("worldKey", worldKey);
            row.put("x", player.getLocation().getBlockX());
            row.put("y", player.getLocation().getBlockY());
            row.put("z", player.getLocation().getBlockZ());
            row.put("yaw", player.getLocation().getYaw());
            row.put("chunkReady", dataStore.getChunk(worldKey, player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4) != null);
            row.put("face_url", faceUrl);
            row.put("faceUrl", faceUrl);
            row.put("health", Math.round(player.getHealth()));
            row.put("armor", player.getAttribute(Attribute.ARMOR) == null ? 0 : Math.round((float) player.getAttribute(Attribute.ARMOR).getValue()));
            players.add(row);
        }
        return players;
    }

    public int getActiveChunkGenCount() {
        int count = 0;
        for (WebMapDimensionSettings dimension : settings.getDimensions().values()) {
            if (dimension.isChunkGenEnabled()) {
                count++;
            }
        }
        return count;
    }

    public String getMapColorProcessingStatus() {
        long nms = nmsColorHits.get();
        long fallback = fallbackColorHits.get();
        if (NMS_MAP_COLOR_RESOLVER.isEnabled()) {
            return "NMSでマップカラー生成 (NMS=" + nms + ", フォールバック=" + fallback + ")";
        }
        return "フォールバック生成 (NMS=0, フォールバック=" + fallback + ")";
    }

    public List<Map<String, Object>> getChangedTilesSince(String worldKey, long since) {
        long cutoff = System.currentTimeMillis() - 600_000L;
        List<Map<String, Object>> tiles = new ArrayList<>();
        List<String> expired = new ArrayList<>();
        for (Map.Entry<String, Long> entry : changedTiles.entrySet()) {
            long updatedAt = entry.getValue();
            if (updatedAt < cutoff) {
                expired.add(entry.getKey());
                continue;
            }
            if (updatedAt <= since) {
                continue;
            }
            String[] parts = entry.getKey().split(":");
            if (parts.length != 3) {
                continue;
            }
            if (!parts[0].equals(worldKey)) {
                continue;
            }
            Map<String, Object> row = new ConcurrentHashMap<>();
            row.put("x", parseInt(parts[1]));
            row.put("z", parseInt(parts[2]));
            row.put("updatedAt", updatedAt);
            tiles.add(row);
        }
        for (String key : expired) {
            changedTiles.remove(key);
        }
        tiles.sort(Comparator.comparingLong(row -> ((Number) row.get("updatedAt")).longValue()));
        return tiles;
    }

    public List<WebMapMarkerRecord> snapshotMarkers(String worldKey) {
        List<WebMapMarkerRecord> markers = markerSnapshots.get(worldKey);
        if (markers != null) {
            return List.copyOf(markers);
        }
        Map<String, WebMapMarkerRecord> storedWaypoints = dataStore.snapshotWaypoints(worldKey);
        if (storedWaypoints.isEmpty()) {
            return List.of();
        }
        List<WebMapMarkerRecord> restored = new ArrayList<>(storedWaypoints.values());
        restored.sort(Comparator.comparing(WebMapMarkerRecord::kind).thenComparing(WebMapMarkerRecord::displayName));
        return List.copyOf(restored);
    }

    public void shutdown() {
        if (shutdownComplete) {
            return;
        }
        shutdownRequested = true;
        if (shutdownWatchTask != null) {
            shutdownWatchTask.cancel();
            shutdownWatchTask = null;
        }
        if (dirtyFlushTask != null) {
            dirtyFlushTask.cancel();
        }
        if (featureSyncTask != null) {
            featureSyncTask.cancel();
        }
        if (chunkGenTask != null) {
            chunkGenTask.cancel();
        }
        if (chunkCaptureTask != null) {
            chunkCaptureTask.cancel();
        }
        if (nearbySweepTask != null) {
            nearbySweepTask.cancel();
        }
        if (markerRefreshTask != null) {
            markerRefreshTask.cancel();
        }
        stopAllChunkGen();
        hideAllChunkGenBossBars();
        if (globalTpsBar != null) {
            globalTpsBar.removeAll();
            globalTpsBar.setVisible(false);
            globalTpsBar = null;
        }
        httpServer.stop();
        dataStore.flushAll();
        shutdownComplete = true;
    }

    public void requestGracefulShutdown() {
        if (shutdownRequested || shutdownComplete) {
            return;
        }
        shutdownRequested = true;
        stopAllChunkGen();
        if (shutdownWatchTask == null) {
            shutdownWatchTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tryFinishGracefulShutdown, 1L, 1L);
        }
    }

    private void tryFinishGracefulShutdown() {
        if (!shutdownRequested || shutdownComplete) {
            return;
        }
        if (hasPendingShutdownWork()) {
            return;
        }
        shutdown();
        plugin.getServer().shutdown();
    }

    private boolean hasPendingShutdownWork() {
        return getActiveChunkGenCount() > 0
                || pendingChunkWork.get() > 0
                || !urgentChunkCaptures.isEmpty()
                || !normalChunkCaptures.isEmpty()
                || !queuedChunkKeys.isEmpty();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        trackPlayerChunk(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastPlayerChunk.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        String command = normalizeServerCommand(event.getCommand());
        if (!"stop".equals(command) && !"restart".equals(command)) {
            return;
        }
        if (shutdownRequested) {
            event.setCancelled(true);
            return;
        }
        if (!hasPendingShutdownWork()) {
            return;
        }
        event.setCancelled(true);
        event.getSender().sendMessage("WebMap ChunkGen を安全に停止してからサーバーを終了します");
        plugin.getLogger().info("サーバー停止コマンドを受け取り、WebMap ChunkGen の終了を待機します");
        requestGracefulShutdown();
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        trackPlayerChunk(event.getPlayer(), event.getTo());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        trackPlayerChunk(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!settings.getEvents().isPlayerMove()) {
            return;
        }
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        Location from = event.getFrom();
        // ブロック単位の座標（XYZ）が同じ（ただのカメラ視線移動など）であれば完全スルーして超高速処理
        if (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY() && from.getBlockZ() == to.getBlockZ()) {
            return;
        }
        // チャンク座標に変換し、チャンク境界をまたいだ一歩のときのみ WebMap の更新ジョブを投げる
        int fromX = from.getBlockX() >> 4;
        int fromZ = from.getBlockZ() >> 4;
        int toX = to.getBlockX() >> 4;
        int toZ = to.getBlockZ() >> 4;
        if (fromX == toX && fromZ == toZ && from.getWorld().getUID().equals(to.getWorld().getUID())) {
            return;
        }
        trackPlayerChunk(event.getPlayer(), to);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!settings.getEvents().isChunkLoad()) {
            return;
        }
        enqueueChunkCapture(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ(), false, false);
        processPendingChunkCaptures(1);
    }

    @EventHandler
    public void onChunkPopulate(ChunkPopulateEvent event) {
        if (!settings.getEvents().isChunkPopulate()) {
            return;
        }
        enqueueChunkCapture(event.getWorld(), event.getChunk().getX(), event.getChunk().getZ(), true, false);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!settings.getEvents().isBlockUpdate()) {
            return;
        }
        markBlockChunkUrgent(event.getBlockPlaced().getLocation());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!settings.getEvents().isBlockUpdate()) {
            return;
        }
        markBlockChunkUrgent(event.getBlock().getLocation());
    }

    private void trackPlayerChunk(Player player, Location location) {
        if (location == null || !isGloballyEnabled() || !settings.isEnabled() || settings.isPaused() || !settings.isAutoTrackPlayers()) {
            return;
        }
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        WebMapDimensionSettings dimension = getDimensionSettings(world);
        if (!dimension.isVisible() || !dimension.isAutoTrack()) {
            return;
        }
        String chunkId = world.getKey() + ":" + (location.getBlockX() >> 4) + ":" + (location.getBlockZ() >> 4);
        String previous = lastPlayerChunk.put(player.getUniqueId(), chunkId);
        if (chunkId.equals(previous)) {
            return;
        }
        int centerChunkX = location.getBlockX() >> 4;
        int centerChunkZ = location.getBlockZ() >> 4;
        enqueueNearbyChunkCaptures(world, centerChunkX, centerChunkZ, PLAYER_CAPTURE_RADIUS, true);
        processPendingChunkCaptures(3);
    }

    private void markBlockChunkUrgent(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        enqueueChunkCapture(world, location.getBlockX() >> 4, location.getBlockZ() >> 4, true, true);
        processPendingChunkCaptures(1);
    }

    private void captureChunk(World world, int chunkX, int chunkZ) {
        queueChunkColorWrite(world, chunkX, chunkZ, false);
    }

    private void queueChunkColorWrite(World world, int chunkX, int chunkZ, boolean gen) {
        pendingChunkWork.incrementAndGet();
        try {
            world.getChunkAtAsync(chunkX, chunkZ, gen).thenCompose(chunk -> {
                if (chunk == null) {
                    return CompletableFuture.completedFuture(null);
                }
                return writeChunkColorAsync(world, chunk);
            }).whenComplete((ignored, error) -> {
                pendingChunkWork.decrementAndGet();
                if (error != null) {
                    plugin.getLogger().log(Level.FINE, "WebMap chunk capture failed: " + world.getName() + " " + chunkX + ":" + chunkZ, error);
                }
            });
        } catch (Throwable error) {
            pendingChunkWork.decrementAndGet();
            plugin.getLogger().log(Level.FINE, "WebMap chunk capture failed: " + world.getName() + " " + chunkX + ":" + chunkZ, error);
        }
    }

    private CompletableFuture<Void> writeChunkColorAsync(World world, Chunk chunk) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (chunk == null) {
            future.complete(null);
            return future;
        }
        org.bukkit.ChunkSnapshot mcsnapshot = chunk.getChunkSnapshot(true, true, false);
        String worldKey = world.getKey().toString();
        String worldName = world.getName();
        String envName = world.getEnvironment().name();
        int minHeight = world.getMinHeight();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    ChunkSnapshot chunkSnapshot = sampleChunkSnapshot(mcsnapshot, chunkX, chunkZ, envName, minHeight);
                    long updatedAt = System.currentTimeMillis();
                    dataStore.updateChunk(
                            worldKey,
                            worldName,
                            chunkX,
                            chunkZ,
                            chunkSnapshot.averageColor(),
                            chunkSnapshot.pixels(),
                            updatedAt
                    );
                    markTileChanged(worldKey, Math.floorDiv(chunkX, CHUNKS_PER_TILE), Math.floorDiv(chunkZ, CHUNKS_PER_TILE), updatedAt);
                    future.complete(null);
                } catch (Throwable error) {
                    plugin.getLogger().log(Level.FINE, "WebMap chunk color update failed: " + worldName + " " + chunkX + ":" + chunkZ, error);
                    future.completeExceptionally(error);
                }
            });
        } catch (Throwable error) {
            future.completeExceptionally(error);
        }
        return future;
    }

    private ChunkSnapshot sampleChunkSnapshot(org.bukkit.ChunkSnapshot mcsnapshot, int chunkX, int chunkZ, String environment, int minHeight) {
        int[] rgb = new int[3];
        int samples = 0;
        String[] pixels = new String[256];
        for (int localZ = 0; localZ < 16; localZ++) {
            int previousHeight = Integer.MIN_VALUE;
            for (int localX = 0; localX < 16; localX++) {
                int topY = mcsnapshot.getHighestBlockYAt(localX, localZ);
                int sampleY = Math.max(minHeight, topY);
                Material type = mcsnapshot.getBlockType(localX, sampleY, localZ);
                BlockData blockData = mcsnapshot.getBlockData(localX, sampleY, localZ);
                Biome biome = mcsnapshot.getBiome(localX, sampleY, localZ);
                int waterDepth = estimateWaterDepth(mcsnapshot, localX, localZ, sampleY, minHeight);
                int[] color = materialColor(type, blockData, biome, environment, waterDepth);
                if (previousHeight != Integer.MIN_VALUE) {
                    color = applyShade(color, topY - previousHeight);
                }
                previousHeight = topY;
                rgb[0] += color[0];
                rgb[1] += color[1];
                rgb[2] += color[2];
                samples++;
                pixels[(localZ * 16) + localX] = toHex(color);
            }
        }
        if (samples == 0) {
            return new ChunkSnapshot("#777777", pixels);
        }
        int[] average = new int[]{rgb[0] / samples, rgb[1] / samples, rgb[2] / samples};
        return new ChunkSnapshot(toHex(average), pixels);
    }

    private int estimateWaterDepth(org.bukkit.ChunkSnapshot snapshot, int localX, int localZ, int topY, int minHeight) {
        if (snapshot.getBlockType(localX, topY, localZ) != Material.WATER) {
            return 0;
        }
        int depth = 1;
        int floorY = Math.max(minHeight, topY - 12);
        for (int y = topY - 1; y >= floorY; y--) {
            if (snapshot.getBlockType(localX, y, localZ) != Material.WATER) {
                break;
            }
            depth++;
        }
        return depth;
    }

    private int[] materialColor(Material material, BlockData blockData, Biome biome, String environment, int waterDepth) {
        int[] nmsColor = NMS_MAP_COLOR_RESOLVER.color(blockData);
        if (nmsColor != null) {
            nmsColorHits.incrementAndGet();
            return applyBiomeAdjustments(material, nmsColor, biome, waterDepth);
        }
        fallbackColorHits.incrementAndGet();
        if (material == Material.WATER) return applyWaterDepthShading(waterColor(biome), waterDepth);
        if (blockData instanceof Ageable ageable) return cropColor(material, ageable);
        if (isGrassTinted(material)) return grassColor(biome);
        if (Tag.LEAVES.isTagged(material)) return foliageColor(material, biome);
        if (Tag.LOGS.isTagged(material) || Tag.PLANKS.isTagged(material)) return woodColor(material);
        int[] dyed = dyedBlockColor(material);
        if (dyed != null) return dyed;
        return switch (material) {
            case LAVA -> new int[]{207, 92, 36};
            case FARMLAND -> new int[]{118, 82, 43};
            case MOSS_BLOCK, MOSS_CARPET, AZALEA, FLOWERING_AZALEA -> new int[]{78, 119, 58};
            case SNOW, SNOW_BLOCK, POWDER_SNOW -> new int[]{238, 248, 248};
            case ICE, PACKED_ICE, BLUE_ICE, FROSTED_ICE -> new int[]{156, 203, 231};
            case SAND, SANDSTONE, SMOOTH_SANDSTONE, CHISELED_SANDSTONE, CUT_SANDSTONE -> new int[]{219, 207, 163};
            case RED_SAND, RED_SANDSTONE, SMOOTH_RED_SANDSTONE, CHISELED_RED_SANDSTONE, CUT_RED_SANDSTONE -> new int[]{154, 94, 63};
            case END_STONE, END_STONE_BRICKS -> new int[]{221, 223, 165};
            case NETHERRACK, NETHER_WART_BLOCK, NETHER_WART -> new int[]{111, 48, 48};
            case WARPED_WART_BLOCK, WARPED_NYLIUM, WARPED_STEM, WARPED_HYPHAE, WARPED_PLANKS -> new int[]{45, 133, 118};
            case CRIMSON_NYLIUM, CRIMSON_STEM, CRIMSON_HYPHAE, CRIMSON_PLANKS -> new int[]{126, 43, 57};
            case BASALT, SMOOTH_BASALT, POLISHED_BASALT, BLACKSTONE, POLISHED_BLACKSTONE -> new int[]{51, 47, 55};
            case SOUL_SAND, SOUL_SOIL -> new int[]{82, 64, 51};
            case DEEPSLATE, COBBLED_DEEPSLATE, POLISHED_DEEPSLATE, DEEPSLATE_BRICKS, DEEPSLATE_TILES -> new int[]{76, 76, 82};
            case STONE, COBBLESTONE, MOSSY_COBBLESTONE, ANDESITE, POLISHED_ANDESITE, TUFF, POLISHED_TUFF, TUFF_BRICKS -> new int[]{127, 127, 127};
            case DIORITE, POLISHED_DIORITE, CALCITE -> new int[]{189, 189, 183};
            case GRANITE, POLISHED_GRANITE -> new int[]{151, 103, 86};
            case DIRT, COARSE_DIRT, ROOTED_DIRT, PODZOL, DIRT_PATH -> new int[]{134, 96, 67};
            case MUD, PACKED_MUD, MUD_BRICKS -> new int[]{64, 58, 60};
            case COPPER_BLOCK, CUT_COPPER, EXPOSED_COPPER, EXPOSED_CUT_COPPER, WEATHERED_COPPER, WEATHERED_CUT_COPPER, OXIDIZED_COPPER, OXIDIZED_CUT_COPPER -> new int[]{160, 132, 95};
            case AMETHYST_BLOCK, BUDDING_AMETHYST, AMETHYST_CLUSTER -> new int[]{137, 103, 191};
            default -> "NETHER".equals(environment) ? new int[]{111, 48, 48} : new int[]{145, 145, 145};
        };
    }

    private int[] applyBiomeAdjustments(Material material, int[] nmsColor, Biome biome, int waterDepth) {
        if (material == Material.WATER) {
            int[] blended = mix(nmsColor, waterColor(biome), 0.55D);
            return applyWaterDepthShading(blended, waterDepth);
        }
        if (isGrassTinted(material)) {
            return mix(nmsColor, grassColor(biome), 0.28D);
        }
        if (Tag.LEAVES.isTagged(material)) {
            return mix(nmsColor, foliageColor(material, biome), 0.35D);
        }
        return nmsColor;
    }

    private int[] applyWaterDepthShading(int[] color, int waterDepth) {
        if (waterDepth <= 1) {
            return color;
        }
        double darken = Math.min(0.22D, (waterDepth - 1) * 0.018D);
        int boostedBlue = clampColor(color[2] + Math.min(18, (waterDepth - 1) * 2));
        return new int[]{
                clampColor((int) Math.round(color[0] * (1.0D - darken))),
                clampColor((int) Math.round(color[1] * (1.0D - darken))),
                clampColor((int) Math.round(boostedBlue * (1.0D - (darken * 0.35D))))
        };
    }

    private int[] cropColor(Material material, Ageable ageable) {
        double growth = ageable.getMaximumAge() <= 0 ? 1D : ageable.getAge() / (double) ageable.getMaximumAge();
        if (material == Material.BEETROOTS) return mix(new int[]{58, 118, 48}, new int[]{143, 38, 50}, growth);
        if (material == Material.CARROTS) return mix(new int[]{69, 132, 51}, new int[]{191, 122, 35}, growth);
        if (material == Material.POTATOES) return mix(new int[]{73, 129, 50}, new int[]{154, 133, 78}, growth);
        if (material == Material.NETHER_WART) return mix(new int[]{91, 27, 35}, new int[]{128, 22, 34}, growth);
        if (material == Material.MELON_STEM || material == Material.PUMPKIN_STEM) return mix(new int[]{82, 130, 44}, new int[]{151, 143, 54}, growth);
        if (material == Material.SWEET_BERRY_BUSH) return mix(new int[]{58, 102, 55}, new int[]{128, 55, 59}, growth);
        return mix(new int[]{79, 129, 48}, new int[]{191, 169, 73}, growth);
    }

    private int[] grassColor(Biome biome) {
        return switch (biomeKey(biome)) {
            case "swamp", "mangrove_swamp" -> new int[]{77, 99, 43};
            case "jungle", "sparse_jungle", "bamboo_jungle", "lush_caves" -> new int[]{48, 142, 52};
            case "savanna", "savanna_plateau", "windswept_savanna", "badlands", "wooded_badlands", "eroded_badlands", "desert" -> new int[]{167, 157, 81};
            case "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", "grove" -> new int[]{92, 137, 74};
            case "snowy_plains", "snowy_taiga", "snowy_slopes", "frozen_peaks", "frozen_river", "frozen_ocean", "deep_frozen_ocean", "ice_spikes" -> new int[]{130, 153, 103};
            case "pale_garden" -> new int[]{116, 124, 106};
            default -> new int[]{91, 155, 70};
        };
    }

    private int[] foliageColor(Material material, Biome biome) {
        return switch (material) {
            case SPRUCE_LEAVES -> new int[]{78, 101, 64};
            case BIRCH_LEAVES -> new int[]{128, 167, 85};
            case MANGROVE_LEAVES -> new int[]{71, 113, 56};
            case PALE_OAK_LEAVES -> new int[]{126, 132, 116};
            default -> switch (biomeKey(biome)) {
                case "swamp", "mangrove_swamp" -> new int[]{75, 95, 43};
                case "jungle", "sparse_jungle", "bamboo_jungle", "lush_caves" -> new int[]{42, 127, 47};
                case "savanna", "savanna_plateau", "windswept_savanna", "desert" -> new int[]{128, 128, 61};
                case "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", "snowy_taiga", "snowy_plains" -> new int[]{86, 111, 67};
                default -> new int[]{72, 131, 55};
            };
        };
    }

    private int[] waterColor(Biome biome) {
        return switch (biomeKey(biome)) {
            case "swamp", "mangrove_swamp" -> new int[]{66, 87, 59};
            case "warm_ocean" -> new int[]{67, 177, 191};
            case "lukewarm_ocean", "deep_lukewarm_ocean" -> new int[]{69, 141, 174};
            case "cold_ocean", "deep_cold_ocean", "frozen_ocean", "deep_frozen_ocean", "frozen_river" -> new int[]{58, 83, 161};
            case "river" -> new int[]{49, 98, 180};
            default -> new int[]{44, 112, 201};
        };
    }

    private int[] woodColor(Material material) {
        return switch (material) {
            case BIRCH_LOG, BIRCH_WOOD, STRIPPED_BIRCH_LOG, STRIPPED_BIRCH_WOOD, BIRCH_PLANKS -> new int[]{194, 176, 119};
            case SPRUCE_LOG, SPRUCE_WOOD, STRIPPED_SPRUCE_LOG, STRIPPED_SPRUCE_WOOD, SPRUCE_PLANKS -> new int[]{93, 68, 42};
            case DARK_OAK_LOG, DARK_OAK_WOOD, STRIPPED_DARK_OAK_LOG, STRIPPED_DARK_OAK_WOOD, DARK_OAK_PLANKS -> new int[]{66, 43, 24};
            case JUNGLE_LOG, JUNGLE_WOOD, STRIPPED_JUNGLE_LOG, STRIPPED_JUNGLE_WOOD, JUNGLE_PLANKS -> new int[]{151, 109, 77};
            case ACACIA_LOG, ACACIA_WOOD, STRIPPED_ACACIA_LOG, STRIPPED_ACACIA_WOOD, ACACIA_PLANKS -> new int[]{168, 91, 51};
            case MANGROVE_LOG, MANGROVE_WOOD, STRIPPED_MANGROVE_LOG, STRIPPED_MANGROVE_WOOD, MANGROVE_PLANKS -> new int[]{118, 54, 47};
            case CHERRY_LOG, CHERRY_WOOD, STRIPPED_CHERRY_LOG, STRIPPED_CHERRY_WOOD, CHERRY_PLANKS -> new int[]{214, 149, 158};
            case PALE_OAK_LOG, PALE_OAK_WOOD, STRIPPED_PALE_OAK_LOG, STRIPPED_PALE_OAK_WOOD, PALE_OAK_PLANKS -> new int[]{194, 190, 174};
            case WARPED_STEM, WARPED_HYPHAE, STRIPPED_WARPED_STEM, STRIPPED_WARPED_HYPHAE, WARPED_PLANKS -> new int[]{54, 131, 127};
            case CRIMSON_STEM, CRIMSON_HYPHAE, STRIPPED_CRIMSON_STEM, STRIPPED_CRIMSON_HYPHAE, CRIMSON_PLANKS -> new int[]{126, 58, 86};
            default -> new int[]{139, 101, 60};
        };
    }

    private int[] dyedBlockColor(Material material) {
        return switch (material) {
            case WHITE_WOOL, WHITE_CONCRETE, WHITE_GLAZED_TERRACOTTA, WHITE_TERRACOTTA -> new int[]{207, 213, 214};
            case LIGHT_GRAY_WOOL, LIGHT_GRAY_CONCRETE, LIGHT_GRAY_GLAZED_TERRACOTTA, LIGHT_GRAY_TERRACOTTA -> new int[]{142, 142, 134};
            case GRAY_WOOL, GRAY_CONCRETE, GRAY_GLAZED_TERRACOTTA, GRAY_TERRACOTTA -> new int[]{62, 68, 71};
            case BLACK_WOOL, BLACK_CONCRETE, BLACK_GLAZED_TERRACOTTA, BLACK_TERRACOTTA -> new int[]{21, 22, 26};
            case BROWN_WOOL, BROWN_CONCRETE, BROWN_GLAZED_TERRACOTTA, BROWN_TERRACOTTA -> new int[]{114, 71, 40};
            case RED_WOOL, RED_CONCRETE, RED_GLAZED_TERRACOTTA, RED_TERRACOTTA -> new int[]{160, 39, 34};
            case ORANGE_WOOL, ORANGE_CONCRETE, ORANGE_GLAZED_TERRACOTTA, ORANGE_TERRACOTTA -> new int[]{240, 118, 19};
            case YELLOW_WOOL, YELLOW_CONCRETE, YELLOW_GLAZED_TERRACOTTA, YELLOW_TERRACOTTA -> new int[]{248, 197, 39};
            case LIME_WOOL, LIME_CONCRETE, LIME_GLAZED_TERRACOTTA, LIME_TERRACOTTA -> new int[]{112, 185, 25};
            case GREEN_WOOL, GREEN_CONCRETE, GREEN_GLAZED_TERRACOTTA, GREEN_TERRACOTTA -> new int[]{84, 109, 27};
            case CYAN_WOOL, CYAN_CONCRETE, CYAN_GLAZED_TERRACOTTA, CYAN_TERRACOTTA -> new int[]{21, 137, 145};
            case LIGHT_BLUE_WOOL, LIGHT_BLUE_CONCRETE, LIGHT_BLUE_GLAZED_TERRACOTTA, LIGHT_BLUE_TERRACOTTA -> new int[]{58, 175, 217};
            case BLUE_WOOL, BLUE_CONCRETE, BLUE_GLAZED_TERRACOTTA, BLUE_TERRACOTTA -> new int[]{53, 57, 157};
            case PURPLE_WOOL, PURPLE_CONCRETE, PURPLE_GLAZED_TERRACOTTA, PURPLE_TERRACOTTA -> new int[]{121, 42, 172};
            case MAGENTA_WOOL, MAGENTA_CONCRETE, MAGENTA_GLAZED_TERRACOTTA, MAGENTA_TERRACOTTA -> new int[]{189, 68, 179};
            case PINK_WOOL, PINK_CONCRETE, PINK_GLAZED_TERRACOTTA, PINK_TERRACOTTA -> new int[]{237, 141, 172};
            default -> null;
        };
    }

    private boolean isGrassTinted(Material material) {
        return switch (material) {
            case GRASS_BLOCK, SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN, VINE -> true;
            default -> false;
        };
    }

    private String biomeKey(Biome biome) {
        return biome == null ? "" : biome.getKey().getKey();
    }

    private void logMapColorProcessingMode() {
        if (NMS_MAP_COLOR_RESOLVER.isEnabled()) {
            plugin.getLogger().info("[WebMap] NMSでマップカラー生成");
            return;
        }
        plugin.getLogger().info("[WebMap] フォールバック生成");
    }

    private static final class NmsMapColorResolver {
        private static final NmsMapColorResolver DISABLED = new NmsMapColorResolver(null, null, null, null, null, null);

        private final Class<?> craftBlockDataClass;
        private final MethodHandle getState;
        private final MethodHandle getMapColor;
        private final MethodHandle getColorValue;
        private final Object emptyBlockGetter;
        private final Object blockPosZero;

        private NmsMapColorResolver(
                Class<?> craftBlockDataClass,
                MethodHandle getState,
                MethodHandle getMapColor,
                MethodHandle getColorValue,
                Object emptyBlockGetter,
                Object blockPosZero
        ) {
            this.craftBlockDataClass = craftBlockDataClass;
            this.getState = getState;
            this.getMapColor = getMapColor;
            this.getColorValue = getColorValue;
            this.emptyBlockGetter = emptyBlockGetter;
            this.blockPosZero = blockPosZero;
        }

        public static NmsMapColorResolver create() {
            try {
                // NMS v26.1.2 時点の実装。リフレクションで呼び出すメソッドは、CraftBlockData#getState() -> BlockState#getMapColor(BlockGetter, BlockPos) -> MapColor.col の順。
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                Class<?> craftBlockDataClass = Class.forName("org.bukkit.craftbukkit.block.data.CraftBlockData");
                Class<?> blockStateClass = Class.forName("net.minecraft.world.level.block.state.BlockState");
                Class<?> blockGetterClass = Class.forName("net.minecraft.world.level.BlockGetter");
                Class<?> blockPosClass = Class.forName("net.minecraft.core.BlockPos");
                Class<?> mapColorClass = Class.forName("net.minecraft.world.level.material.MapColor");
                Class<?> emptyBlockGetterClass = Class.forName("net.minecraft.world.level.EmptyBlockGetter");
                MethodHandle getState = lookup.findVirtual(craftBlockDataClass, "getState", MethodType.methodType(blockStateClass));
                MethodHandle getMapColor = lookup.findVirtual(blockStateClass, "getMapColor", MethodType.methodType(mapColorClass, blockGetterClass, blockPosClass));
                MethodHandle getColorValue = lookup.findGetter(mapColorClass, "col", int.class);
                Object emptyBlockGetter = lookup.findStaticGetter(emptyBlockGetterClass, "INSTANCE", emptyBlockGetterClass).invoke();
                Object blockPosZero = lookup.findStaticGetter(blockPosClass, "ZERO", blockPosClass).invoke();
                return new NmsMapColorResolver(craftBlockDataClass, getState, getMapColor, getColorValue, emptyBlockGetter, blockPosZero);
            } catch (Throwable ignored) {
                return DISABLED;
            }
        }

        public int[] color(BlockData blockData) {
            if (craftBlockDataClass == null || blockData == null || !craftBlockDataClass.isInstance(blockData)) {
                return null;
            }
            try {
                Object state = getState.invoke(blockData);
                Object mapColor = getMapColor.invoke(state, emptyBlockGetter, blockPosZero);
                int color = (int) getColorValue.invoke(mapColor);
                if (color == 0) {
                    return null;
                }
                return new int[]{
                        (color >> 16) & 0xFF,
                        (color >> 8) & 0xFF,
                        color & 0xFF
                };
            } catch (Throwable ignored) {
                return null;
            }
        }

        public boolean isEnabled() {
            return craftBlockDataClass != null;
        }
    }

    private int[] mix(int[] from, int[] to, double amount) {
        double clamped = Math.max(0D, Math.min(1D, amount));
        return new int[]{
                clampColor((int) Math.round(from[0] + ((to[0] - from[0]) * clamped))),
                clampColor((int) Math.round(from[1] + ((to[1] - from[1]) * clamped))),
                clampColor((int) Math.round(from[2] + ((to[2] - from[2]) * clamped)))
        };
    }

    private int[] applyShade(int[] color, int delta) {
        double multiplier = delta > 0 ? 1.12D : delta < 0 ? 0.88D : 1.0D;
        return new int[]{
                clampColor((int) Math.round(color[0] * multiplier)),
                clampColor((int) Math.round(color[1] * multiplier)),
                clampColor((int) Math.round(color[2] * multiplier))
        };
    }

    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private String toHex(int[] color) {
        return WebMapColorPool.canonicalize(String.format(java.util.Locale.ROOT, "#%02x%02x%02x", color[0], color[1], color[2]));
    }

    private void syncKnownWorlds() {
        for (World world : plugin.getServer().getWorlds()) {
            store.ensureDimensionSettings(settings, world);
        }
    }

    private void restorePersistedMarkerSnapshots() {
        markerSnapshots.clear();
        waypointSnapshots.clear();
        for (World world : plugin.getServer().getWorlds()) {
            WebMapDimensionSettings dimension = getDimensionSettings(world);
            if (!dimension.isVisible()) {
                continue;
            }
            String worldKey = world.getKey().toString();
            Map<String, WebMapMarkerRecord> storedWaypoints = dataStore.snapshotWaypoints(worldKey);
            if (storedWaypoints.isEmpty()) {
                continue;
            }
            waypointSnapshots.put(worldKey, new LinkedHashMap<>(storedWaypoints));
            List<WebMapMarkerRecord> restoredMarkers = new ArrayList<>(storedWaypoints.values());
            restoredMarkers.sort(Comparator.comparing(WebMapMarkerRecord::kind).thenComparing(WebMapMarkerRecord::displayName));
            markerSnapshots.put(worldKey, restoredMarkers);
        }
    }

    private void startTasks() {
        dirtyFlushTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> dataStore.flushDirty(), 200L, 200L);
        featureSyncTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            refreshGlobalEnabled();
            syncRuntimeState();
            updateGlobalTpsBar();
        }, 40L, 40L);
        chunkGenTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tickChunkGen(), 20L, 1L);
        chunkCaptureTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> processPendingChunkCaptures(4), 2L, 2L);
        nearbySweepTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweepNearbyLoadedChunks, 1200L, 1200L);
        markerRefreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refreshMarkerSnapshots, 40L, 40L);
    }

    private void tickChunkGen() {
        if (shutdownRequested || !isGloballyEnabled() || settings.isPaused() || !settings.isEnabled()) {
            return;
        }
        int budget = 4;
        for (WebMapDimensionSettings dimension : getDimensionSettingsList()) {
            if (budget <= 0) {
                break;
            }
            if (!dimension.isChunkGenEnabled() || !dimension.isVisible()) {
                hideChunkGenBossBar(dimension.getWorldKey());
                continue;
            }
            World world = resolveWorld(dimension.getWorldKey());
            if (world == null) {
                continue;
            }
            ChunkGenJob job = chunkGenJobs.computeIfAbsent(dimension.getWorldKey(), key -> ChunkGenJob.create(world));
            while (budget > 0) {
                ChunkCoordinate next = job.next();
                if (next == null) {
                    break;
                }
                budget--;
                queueChunkColorWrite(world, next.x(), next.z(), true);
            }
            updateChunkGenBossBar(world, job);
        }
    }

    private void syncRuntimeState() {
        syncRuntimeState(false);
    }

    private void syncRuntimeState(boolean forceRestart) {
        if (shutdownRequested) {
            return;
        }
        if (!isGloballyEnabled() || !settings.isEnabled() || settings.isPaused()) {
            httpServer.stop();
            return;
        }
        if (!httpServer.isRunning()) {
            try {
                httpServer.start(settings);
            } catch (IOException error) {
                plugin.getLogger().log(Level.WARNING, "WebMap サーバー起動失敗: " + error.getMessage());
            }
            return;
        }
        if (forceRestart) {
            try {
                httpServer.start(settings);
            } catch (IOException error) {
                plugin.getLogger().log(Level.WARNING, "WebMap サーバー再起動失敗: " + error.getMessage());
            }
        }
    }

    private World resolveWorld(String worldKey) {
        for (World world : plugin.getServer().getWorlds()) {
            if (world.getKey().toString().equals(worldKey)) {
                return world;
            }
        }
        return null;
    }

    private void refreshGlobalEnabled() {
        globalEnabled = plugin.getToggleModule() != null && plugin.getToggleModule().getGlobal("webmap");
    }

    private void updateChunkGenBossBar(World world, ChunkGenJob job) {
        String worldKey = world.getKey().toString();
        BossBar bar = chunkGenBossBars.computeIfAbsent(worldKey, key ->
                Bukkit.createBossBar("ChunkGen", BarColor.BLUE, BarStyle.SOLID));
        double[] tpsValues = Bukkit.getTPS();
        double tps = tpsValues.length == 0 ? 20D : Math.max(0D, Math.min(20D, tpsValues[0]));
        double load = 1D - (tps / 20D);
        int loadPercent = (int) Math.round(load * 100D);
        bar.setColor(loadPercent >= 50 ? BarColor.RED : loadPercent >= 25 ? BarColor.YELLOW : BarColor.GREEN);
        bar.setProgress(Math.max(0.05D, Math.min(1D, tps / 20D)));
        bar.setTitle("§b" + world.getName()
                + " §7ChunkGen §fTPS:" + String.format(java.util.Locale.ROOT, "%.2f", tps)
                + " §fLoad:" + loadPercent + "%"
                + " §fGenerated:" + job.generatedCount());
        for (Player player : world.getPlayers()) {
            if (!bar.getPlayers().contains(player)) {
                bar.addPlayer(player);
            }
        }
        for (Player player : new ArrayList<>(bar.getPlayers())) {
            if (!player.getWorld().getUID().equals(world.getUID())) {
                bar.removePlayer(player);
            }
        }
        bar.setVisible(true);
    }

    private void hideAllChunkGenBossBars() {
        for (BossBar bar : chunkGenBossBars.values()) {
            bar.removeAll();
            bar.setVisible(false);
        }
        chunkGenBossBars.clear();
    }

    private void hideChunkGenBossBar(String worldKey) {
        BossBar bar = chunkGenBossBars.remove(worldKey);
        if (bar == null) {
            return;
        }
        bar.removeAll();
        bar.setVisible(false);
    }

    private void refreshMarkerSnapshots() {
        Map<String, List<WebMapMarkerRecord>> nextSnapshots = new HashMap<>();
        Map<String, Map<String, WebMapMarkerRecord>> nextWaypointSnapshots = new HashMap<>();
        for (World world : plugin.getServer().getWorlds()) {
            WebMapDimensionSettings dimension = getDimensionSettings(world);
            if (!dimension.isVisible()) {
                continue;
            }
            String worldKey = world.getKey().toString();
            Map<String, WebMapMarkerRecord> previousWaypoints = dataStore.snapshotWaypoints(worldKey);
            if (previousWaypoints.isEmpty()) {
                previousWaypoints = waypointSnapshots.getOrDefault(worldKey, Map.of());
            }
            MarkerSnapshot markerSnapshot = collectMarkers(world, previousWaypoints);
            dataStore.replaceWaypoints(worldKey, world.getName(), markerSnapshot.waypointByChunk());
            nextSnapshots.put(worldKey, markerSnapshot.markers());
            nextWaypointSnapshots.put(worldKey, markerSnapshot.waypointByChunk());
        }
        markerSnapshots.clear();
        markerSnapshots.putAll(nextSnapshots);
        waypointSnapshots.clear();
        waypointSnapshots.putAll(nextWaypointSnapshots);
    }

    private MarkerSnapshot collectMarkers(World world, Map<String, WebMapMarkerRecord> previousWaypoints) {
        Map<String, WebMapMarkerRecord> waypointByChunk = new LinkedHashMap<>();
        List<String> acceptedNames = new ArrayList<>();

        java.util.Set<String> loadedChunkKeys = new java.util.HashSet<>();
        for (Chunk loadedChunk : world.getLoadedChunks()) {
            loadedChunkKeys.add(loadedChunk.getX() + ":" + loadedChunk.getZ());
        }

        for (Map.Entry<String, WebMapMarkerRecord> entry : previousWaypoints.entrySet()) {
            if (loadedChunkKeys.contains(entry.getKey())) {
                continue;
            }
            waypointByChunk.put(entry.getKey(), entry.getValue());
            String normalized = normalizeMarkerName(entry.getValue().name());
            if (!normalized.isBlank()) {
                acceptedNames.add(normalized);
            }
        }

        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof ItemFrame frame)) {
                continue;
            }
            if (frame.getItem().getType() != Material.FILLED_MAP) {
                continue;
            }
            org.bukkit.inventory.meta.ItemMeta itemMeta = frame.getItem().getItemMeta();
            if (itemMeta == null || !itemMeta.hasDisplayName()) {
                continue;
            }
            String rawName = ComponentUtils.getDisplayName(itemMeta);
            if (rawName == null || rawName.isBlank()) {
                continue;
            }
            String visibleName = translateColorCodes(rawName);
            String strippedName = stripColorCodes(visibleName);
            if (strippedName == null || strippedName.isBlank()) {
                continue;
            }
            String normalized = normalizeMarkerName(strippedName);
            if (normalized.isBlank() || isSimilarMarkerName(normalized, acceptedNames)) {
                continue;
            }
            acceptedNames.add(normalized);
            int chunkX = frame.getLocation().getBlockX() >> 4;
            int chunkZ = frame.getLocation().getBlockZ() >> 4;
            String chunkKey = chunkX + ":" + chunkZ;
            if (waypointByChunk.containsKey(chunkKey)) {
                continue;
            }
            waypointByChunk.put(chunkKey, new WebMapMarkerRecord(
                    world.getKey() + ":waypoint:" + chunkKey,
                    "waypoint",
                    strippedName,
                    visibleName,
                    extractLegacyColor(visibleName),
                    frame.getLocation().getBlockX(),
                    frame.getLocation().getBlockZ(),
                    chunkX,
                    chunkZ,
                    waypointRotation(frame)
            ));
        }

        List<WebMapMarkerRecord> markers = new ArrayList<>(waypointByChunk.values());
        java.util.Set<String> loadedChunkKeysForMarkers = new java.util.HashSet<>();
        for (Chunk chunk : world.getLoadedChunks()) {
            int chunkX = chunk.getX();
            int chunkZ = chunk.getZ();
            String chunkKey = chunkX + ":" + chunkZ;
            if (!loadedChunkKeysForMarkers.add(chunkKey)) {
                continue;
            }
            markers.add(new WebMapMarkerRecord(
                    world.getKey() + ":loaded-chunk:" + chunkX + ":" + chunkZ,
                    "loadedchunk",
                    "Loaded Chunk",
                    "Loaded Chunk",
                    "#8fd8ff",
                    (chunkX << 4) + 8,
                    (chunkZ << 4) + 8,
                    chunkX,
                    chunkZ,
                    0
            ));
        }

        java.util.Set<String> persistentChunkKeys = new java.util.HashSet<>();
        for (Chunk chunk : world.getLoadedChunks()) {
            int chunkX = chunk.getX();
            int chunkZ = chunk.getZ();
            String chunkKey = chunkX + ":" + chunkZ;
            if (!world.getPlayersSeeingChunk(chunk).isEmpty()) {
                continue;
            }
            if (!persistentChunkKeys.add(chunkKey)) {
                continue;
            }
            markers.add(new WebMapMarkerRecord(
                    world.getKey() + ":persistent-loaded:" + chunkX + ":" + chunkZ,
                    "persistentchunk",
                    "Persistent Chunk",
                    "Persistent Chunk",
                    "#66d1ff",
                    (chunkX << 4) + 8,
                    (chunkZ << 4) + 8,
                    chunkX,
                    chunkZ,
                    0
            ));
        }

        for (Chunk chunk : world.getForceLoadedChunks()) {
            int chunkX = chunk.getX();
            int chunkZ = chunk.getZ();
            String chunkKey = chunkX + ":" + chunkZ;
            if (!persistentChunkKeys.add(chunkKey)) {
                continue;
            }
            markers.add(new WebMapMarkerRecord(
                    world.getKey() + ":chunkloader:" + chunkX + ":" + chunkZ,
                    "persistentchunk",
                    "Persistent Chunk",
                    "Persistent Chunk",
                    "#66d1ff",
                    (chunkX << 4) + 8,
                    (chunkZ << 4) + 8,
                    chunkX,
                    chunkZ,
                    0
            ));
        }

        for (Map.Entry<org.bukkit.plugin.Plugin, Collection<Chunk>> entry : world.getPluginChunkTickets().entrySet()) {
            String pluginName = entry.getKey().getName();
            for (Chunk chunk : entry.getValue()) {
                int chunkX = chunk.getX();
                int chunkZ = chunk.getZ();
                String chunkKey = chunkX + ":" + chunkZ;
                if (!persistentChunkKeys.add(chunkKey)) {
                    continue;
                }
                markers.add(new WebMapMarkerRecord(
                        world.getKey() + ":plugin-loader:" + chunkX + ":" + chunkZ,
                        "persistentchunk",
                        "Plugin Ticket (" + pluginName + ")",
                        "Plugin Ticket (" + pluginName + ")",
                        "#66d1ff",
                        (chunkX << 4) + 8,
                        (chunkZ << 4) + 8,
                        chunkX,
                        chunkZ,
                        0
                ));
            }
        }
        markers.sort(Comparator.comparing(WebMapMarkerRecord::kind).thenComparing(WebMapMarkerRecord::displayName));
        return new MarkerSnapshot(markers, waypointByChunk);
    }

    private record MarkerSnapshot(List<WebMapMarkerRecord> markers, Map<String, WebMapMarkerRecord> waypointByChunk) {
    }

    private int waypointRotation(ItemFrame frame) {
        int base = switch (frame.getFacing()) {
            case NORTH -> 180;
            case EAST -> 270;
            case SOUTH -> 0;
            case WEST -> 90;
            default -> 0;
        };
        int itemRotation = switch (frame.getRotation()) {
            case NONE -> 0;
            case CLOCKWISE_45 -> 45;
            case CLOCKWISE -> 90;
            case CLOCKWISE_135 -> 135;
            case FLIPPED -> 180;
            case FLIPPED_45 -> 225;
            case COUNTER_CLOCKWISE -> 270;
            case COUNTER_CLOCKWISE_45 -> 315;
        };
        return Math.floorMod(base + itemRotation, 360);
    }

    private boolean isSimilarMarkerName(String normalizedName, List<String> acceptedNames) {
        for (String accepted : acceptedNames) {
            if (normalizedName.equals(accepted)) {
                return true;
            }
            if (Math.abs(normalizedName.length() - accepted.length()) > 1) {
                continue;
            }
            if (levenshteinDistance(normalizedName, accepted) <= 1) {
                return true;
            }
        }
        return false;
    }

    private String normalizeMarkerName(String value) {
        return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9ぁ-んァ-ヶ一-龠]+", "").trim();
    }

    private String translateColorCodes(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '&' && index + 1 < value.length()) {
                char code = Character.toLowerCase(value.charAt(index + 1));
                if (isColorCode(code)) {
                    builder.append('§').append(code);
                    index++;
                    continue;
                }
            }
            builder.append(current);
        }
        return builder.toString();
    }

    private String stripColorCodes(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(value.length());
        boolean skip = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (skip) {
                skip = false;
                continue;
            }
            if (current == '§') {
                skip = true;
                continue;
            }
            builder.append(current);
        }
        return builder.toString().trim();
    }

    private boolean isColorCode(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f', 'k', 'l', 'm', 'n', 'o', 'r' -> true;
            default -> false;
        };
    }

    private int levenshteinDistance(String left, String right) {
        int[] costs = new int[right.length() + 1];
        for (int j = 0; j < costs.length; j++) {
            costs[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            costs[0] = i;
            int previous = i - 1;
            for (int j = 1; j <= right.length(); j++) {
                int current = costs[j];
                int substitution = previous + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1);
                int insertion = costs[j] + 1;
                int deletion = costs[j - 1] + 1;
                costs[j] = Math.min(Math.min(insertion, deletion), substitution);
                previous = current;
            }
        }
        return costs[right.length()];
    }

    private String extractLegacyColor(String text) {
        String legacy = translateColorCodes(text);
        for (int index = 0; index < legacy.length() - 1; index++) {
            if (legacy.charAt(index) != '§') {
                continue;
            }
            String color = switch (Character.toLowerCase(legacy.charAt(index + 1))) {
                case '0' -> "#000000";
                case '1' -> "#0000aa";
                case '2' -> "#00aa00";
                case '3' -> "#00aaaa";
                case '4' -> "#aa0000";
                case '5' -> "#aa00aa";
                case '6' -> "#ffaa00";
                case '7' -> "#aaaaaa";
                case '8' -> "#555555";
                case '9' -> "#5555ff";
                case 'a' -> "#55ff55";
                case 'b' -> "#55ffff";
                case 'c' -> "#ff5555";
                case 'd' -> "#ff55ff";
                case 'e' -> "#ffff55";
                case 'f' -> "#ffffff";
                default -> null;
            };
            if (color != null) {
                return color;
            }
        }
        return "#ffffff";
    }

    private void updateGlobalTpsBar() {
        if (!isGloballyEnabled() || !settings.isEnabled() || settings.isPaused() || !settings.isShowTpsBar()) {
            if (globalTpsBar != null) {
                globalTpsBar.removeAll();
                globalTpsBar.setVisible(false);
                globalTpsBar = null;
            }
            return;
        }

        double[] tpsValues = Bukkit.getTPS();
        double tps = tpsValues.length == 0 ? 20.0D : Math.max(0.0D, Math.min(20.0D, tpsValues[0]));
        
        long maxMem = Runtime.getRuntime().maxMemory();
        long freeMem = Runtime.getRuntime().freeMemory();
        long totalMem = Runtime.getRuntime().totalMemory();
        long usedMem = totalMem - freeMem;
        
        String title = String.format(java.util.Locale.ROOT,
                "§eTPS: §f%.2f §7| §bMemory: §f%dMB / %dMB §7| §aOnline: §f%d",
                tps, usedMem / 1024 / 1024, maxMem / 1024 / 1024, Bukkit.getOnlinePlayers().size());
        
        BarColor color = tps >= 18.0D ? BarColor.GREEN : tps >= 15.0D ? BarColor.YELLOW : BarColor.RED;
        double progress = Math.max(0.0D, Math.min(1.0D, tps / 20.0D));

        if (globalTpsBar == null) {
            globalTpsBar = Bukkit.createBossBar(title, color, BarStyle.SOLID);
        } else {
            globalTpsBar.setTitle(title);
            globalTpsBar.setColor(color);
        }
        globalTpsBar.setProgress(progress);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!globalTpsBar.getPlayers().contains(player)) {
                globalTpsBar.addPlayer(player);
            }
        }
        
        for (Player player : new ArrayList<>(globalTpsBar.getPlayers())) {
            if (!player.isOnline()) {
                globalTpsBar.removePlayer(player);
            }
        }
        
        globalTpsBar.setVisible(true);
    }

    private void markTileChanged(String worldKey, int tileX, int tileZ, long updatedAt) {
        changedTiles.put(worldKey + ":" + tileX + ":" + tileZ, updatedAt);
    }

    private void sweepNearbyLoadedChunks() {
        if (!isGloballyEnabled() || !settings.isEnabled() || settings.isPaused() || !settings.isAutoTrackPlayers()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            World world = player.getWorld();
            WebMapDimensionSettings dimension = getDimensionSettings(world);
            if (!dimension.isVisible() || !dimension.isAutoTrack()) {
                continue;
            }
            int centerChunkX = player.getLocation().getBlockX() >> 4;
            int centerChunkZ = player.getLocation().getBlockZ() >> 4;
            List<ChunkCoordinate> coordinates = new ArrayList<>();
            for (int chunkX = centerChunkX - PLAYER_SWEEP_RADIUS; chunkX <= centerChunkX + PLAYER_SWEEP_RADIUS; chunkX++) {
                for (int chunkZ = centerChunkZ - PLAYER_SWEEP_RADIUS; chunkZ <= centerChunkZ + PLAYER_SWEEP_RADIUS; chunkZ++) {
                    if (!world.isChunkLoaded(chunkX, chunkZ)) {
                        continue;
                    }
                    coordinates.add(new ChunkCoordinate(chunkX, chunkZ));
                }
            }
            coordinates.sort(Comparator.comparingInt(entry ->
                    Math.abs(entry.x() - centerChunkX) + Math.abs(entry.z() - centerChunkZ)));
            for (ChunkCoordinate coordinate : coordinates) {
                enqueueChunkCapture(world, coordinate.x(), coordinate.z(), false, true);
            }
        }
        processPendingChunkCaptures(2);
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void enqueueChunkCapture(World world, int chunkX, int chunkZ, boolean force, boolean urgent) {
        if (shutdownRequested || !isGloballyEnabled() || !settings.isEnabled() || settings.isPaused()) {
            return;
        }
        if (world == null) {
            return;
        }
        pruneRecentChunkCaptures();
        WebMapDimensionSettings dimension = getDimensionSettings(world);
        if (!dimension.isVisible() || !dimension.isAutoTrack()) {
            return;
        }
        String worldKey = world.getKey().toString();
        String chunkKey = worldKey + ":" + chunkX + ":" + chunkZ;
        long now = System.currentTimeMillis();
        Long lastCapture = recentChunkCaptures.get(chunkKey);
        long throttleMillis = urgent ? 1_000L : 15_000L;
        if (!force && lastCapture != null && (now - lastCapture) < throttleMillis) {
            return;
        }
        if (!force && dataStore.getChunk(worldKey, chunkX, chunkZ) != null && lastCapture != null && (now - lastCapture) < 120_000L) {
            return;
        }
        if (!queuedChunkKeys.add(chunkKey)) {
            return;
        }
        QueuedChunkCapture capture = new QueuedChunkCapture(worldKey, chunkX, chunkZ, chunkKey);
        if (urgent) {
            urgentChunkCaptures.addLast(capture);
        } else {
            normalChunkCaptures.addLast(capture);
        }
    }

    private void pruneRecentChunkCaptures() {
        long now = System.currentTimeMillis();
        long lastCleanup = recentChunkCaptureCleanupAt.get();
        if (now - lastCleanup < 60_000L) {
            return;
        }
        if (!recentChunkCaptureCleanupAt.compareAndSet(lastCleanup, now)) {
            return;
        }
        long cutoff = now - 1_800_000L;
        for (Map.Entry<String, Long> entry : recentChunkCaptures.entrySet()) {
            Long updatedAt = entry.getValue();
            if (updatedAt != null && updatedAt < cutoff) {
                recentChunkCaptures.remove(entry.getKey(), updatedAt);
            }
        }
    }

    private String normalizeServerCommand(String command) {
        if (command == null) {
            return "";
        }
        String normalized = command.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        int spaceIndex = normalized.indexOf(' ');
        if (spaceIndex >= 0) {
            normalized = normalized.substring(0, spaceIndex);
        }
        int namespaceIndex = normalized.indexOf(':');
        if (namespaceIndex >= 0) {
            normalized = normalized.substring(namespaceIndex + 1);
        }
        return normalized.toLowerCase(java.util.Locale.ROOT);
    }

    private void enqueueNearbyChunkCaptures(World world, int centerChunkX, int centerChunkZ, int radius, boolean urgent) {
        List<ChunkCoordinate> coordinates = new ArrayList<>();
        for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
            for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
                coordinates.add(new ChunkCoordinate(chunkX, chunkZ));
            }
        }
        coordinates.sort(Comparator.comparingInt(entry ->
                Math.abs(entry.x() - centerChunkX) + Math.abs(entry.z() - centerChunkZ)));
        for (ChunkCoordinate coordinate : coordinates) {
            enqueueChunkCapture(world, coordinate.x(), coordinate.z(), true, urgent);
        }
    }

    private void processPendingChunkCaptures() {
        processPendingChunkCaptures(4);
    }

    private void processPendingChunkCaptures(int budget) {
        while (budget-- > 0) {
            QueuedChunkCapture queued = urgentChunkCaptures.pollFirst();
            if (queued == null) {
                queued = normalChunkCaptures.pollFirst();
            }
            if (queued == null) {
                return;
            }
            queuedChunkKeys.remove(queued.chunkKey());
            World world = resolveWorld(queued.worldKey());
            if (world == null) {
                continue;
            }
            recentChunkCaptures.put(queued.chunkKey(), System.currentTimeMillis());
            captureChunk(world, queued.chunkX(), queued.chunkZ());
        }
    }

    private record ChunkCoordinate(int x, int z) {
    }

    private record QueuedChunkCapture(String worldKey, int chunkX, int chunkZ, String chunkKey) {
    }

    private static final class ChunkGenJob {
        private final int centerX;
        private final int centerZ;
        private int currentX;
        private int currentZ;
        private int stepLength = 1;
        private int stepsTaken;
        private int legProgress;
        private int directionIndex;
        private boolean emitCenter = true;
        private long generatedCount;

        private ChunkGenJob(int centerX, int centerZ) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.currentX = centerX;
            this.currentZ = centerZ;
        }

        public static ChunkGenJob create(World world) {
            return new ChunkGenJob(world.getSpawnLocation().getBlockX() >> 4, world.getSpawnLocation().getBlockZ() >> 4);
        }

        public ChunkCoordinate next() {
            if (emitCenter) {
                emitCenter = false;
                generatedCount++;
                return new ChunkCoordinate(centerX, centerZ);
            }
            int[][] directions = new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
            int[] direction = directions[directionIndex];
            currentX += direction[0];
            currentZ += direction[1];
            legProgress++;
            stepsTaken++;
            if (legProgress >= stepLength) {
                legProgress = 0;
                directionIndex = (directionIndex + 1) % directions.length;
                if (directionIndex == 0 || directionIndex == 2) {
                    stepLength++;
                }
            }
            generatedCount++;
            return new ChunkCoordinate(currentX, currentZ);
        }

        public long generatedCount() {
            return generatedCount;
        }
    }

    private record ChunkSnapshot(String averageColor, String[] pixels) {
    }
}
