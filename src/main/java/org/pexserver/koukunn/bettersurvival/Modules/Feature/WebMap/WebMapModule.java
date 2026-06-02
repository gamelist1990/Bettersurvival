package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Api.McApiClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;

public class WebMapModule implements Listener {
    private static final int PLAYER_CAPTURE_RADIUS = 3;
    private static final int PLAYER_SWEEP_RADIUS = 3;
    private static final int CHUNKS_PER_TILE = 32;

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
    private BossBar globalTpsBar;

    private volatile boolean globalEnabled;
    private WebMapSettings settings;
    private BukkitTask dirtyFlushTask;
    private BukkitTask featureSyncTask;
    private BukkitTask chunkGenTask;
    private BukkitTask chunkCaptureTask;
    private BukkitTask nearbySweepTask;
    private BukkitTask markerRefreshTask;

    public WebMapModule(Loader plugin) {
        this.plugin = plugin;
        this.store = new WebMapStore(plugin.getConfigManager());
        this.dataStore = new WebMapDataStore(store);
        this.httpServer = new WebMapHttpServer(this);
        this.settings = store.loadSettings();
        syncKnownWorlds();
        store.saveSettings(settings);
        refreshGlobalEnabled();
        startTasks();
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
        return markers == null ? List.of() : List.copyOf(markers);
    }

    public void shutdown() {
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
        hideAllChunkGenBossBars();
        if (globalTpsBar != null) {
            globalTpsBar.removeAll();
            globalTpsBar.setVisible(false);
            globalTpsBar = null;
        }
        httpServer.stop();
        dataStore.flushAll();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        trackPlayerChunk(event.getPlayer(), event.getPlayer().getLocation());
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
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                world.getChunkAtAsync(chunkX, chunkZ, false).thenAccept(chunk -> {
                    if (chunk != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> writeChunkColor(world, chunk));
                    }
                });
                return;
            }
            writeChunkColor(world, world.getChunkAt(chunkX, chunkZ));
        });
    }

    private void writeChunkColor(World world, Chunk chunk) {
        if (chunk == null) {
            return;
        }
        org.bukkit.ChunkSnapshot mcsnapshot = chunk.getChunkSnapshot(true, false, false);
        String worldKey = world.getKey().toString();
        String worldName = world.getName();
        String envName = world.getEnvironment().name();
        int minHeight = world.getMinHeight();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
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
        });
    }

    private ChunkSnapshot sampleChunkSnapshot(org.bukkit.ChunkSnapshot mcsnapshot, int chunkX, int chunkZ, String environment, int minHeight) {
        int[] rgb = new int[3];
        int samples = 0;
        String[] pixels = new String[256];
        for (int localZ = 0; localZ < 16; localZ++) {
            int previousHeight = Integer.MIN_VALUE;
            for (int localX = 0; localX < 16; localX++) {
                int topY = mcsnapshot.getHighestBlockYAt(localX, localZ);
                Material type = mcsnapshot.getBlockType(localX, Math.max(minHeight, topY), localZ);
                int[] color = materialColor(type, environment);
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

    private int[] materialColor(Material material, String environment) {
        String name = material.name();
        if (name.contains("WATER")) return new int[]{44, 112, 201};
        if (name.contains("LAVA")) return new int[]{222, 108, 38};
        if (name.contains("GRASS") || name.contains("MOSS")) return new int[]{84, 147, 76};
        if (name.contains("LEAVES") || name.contains("AZALEA")) return new int[]{61, 120, 56};
        if (name.contains("SNOW") || name.contains("ICE")) return new int[]{218, 236, 243};
        if (name.contains("SAND") || name.contains("END_STONE")) return new int[]{215, 206, 130};
        if (name.contains("NETHERRACK") || environment.contains("NETHER")) return new int[]{120, 46, 46};
        if (name.contains("STONE") || name.contains("DEEPSLATE") || name.contains("COBBLE")) return new int[]{112, 112, 112};
        if (name.contains("DIRT") || name.contains("MUD") || name.contains("SOUL")) return new int[]{126, 94, 61};
        if (name.contains("LOG") || name.contains("WOOD") || name.contains("PLANKS")) return new int[]{132, 98, 62};
        if (name.contains("COPPER")) return new int[]{160, 132, 95};
        if (name.contains("AMETHYST")) return new int[]{137, 103, 191};
        return new int[]{145, 145, 145};
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
        return String.format(java.util.Locale.ROOT, "#%02x%02x%02x", color[0], color[1], color[2]);
    }

    private void syncKnownWorlds() {
        for (World world : plugin.getServer().getWorlds()) {
            store.ensureDimensionSettings(settings, world);
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
        if (!isGloballyEnabled() || settings.isPaused() || !settings.isEnabled()) {
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
                world.getChunkAtAsync(next.x(), next.z(), true).thenAccept(chunk ->
                        Bukkit.getScheduler().runTask(plugin, () -> writeChunkColor(world, chunk)));
            }
            updateChunkGenBossBar(world, job);
        }
    }

    private void syncRuntimeState() {
        syncRuntimeState(false);
    }

    private void syncRuntimeState(boolean forceRestart) {
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
            Map<String, WebMapMarkerRecord> previousWaypoints = waypointSnapshots.getOrDefault(worldKey, Map.of());
            MarkerSnapshot markerSnapshot = collectMarkers(world, previousWaypoints);
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
                    chunkZ
            ));
        }

        List<WebMapMarkerRecord> markers = new ArrayList<>(waypointByChunk.values());
        java.util.Set<String> chunkLoaderKeys = new java.util.HashSet<>();
        for (Chunk chunk : world.getForceLoadedChunks()) {
            int chunkX = chunk.getX();
            int chunkZ = chunk.getZ();
            String chunkKey = chunkX + ":" + chunkZ;
            if (!chunkLoaderKeys.add(chunkKey)) {
                continue;
            }
            markers.add(new WebMapMarkerRecord(
                    world.getKey() + ":chunkloader:" + chunkX + ":" + chunkZ,
                    "chunkloader",
                    "Chunk Loader",
                    "Chunk Loader",
                    "#8fd8ff",
                    (chunkX << 4) + 8,
                    (chunkZ << 4) + 8,
                    chunkX,
                    chunkZ
            ));
        }

        for (Map.Entry<org.bukkit.plugin.Plugin, Collection<Chunk>> entry : world.getPluginChunkTickets().entrySet()) {
            String pluginName = entry.getKey().getName();
            for (Chunk chunk : entry.getValue()) {
                int chunkX = chunk.getX();
                int chunkZ = chunk.getZ();
                String chunkKey = chunkX + ":" + chunkZ;
                if (!chunkLoaderKeys.add(chunkKey)) {
                    continue;
                }
                markers.add(new WebMapMarkerRecord(
                        world.getKey() + ":plugin-loader:" + chunkX + ":" + chunkZ,
                        "chunkloader",
                        "Plugin Ticket (" + pluginName + ")",
                        "Plugin Ticket (" + pluginName + ")",
                        "#8fd8ff",
                        (chunkX << 4) + 8,
                        (chunkZ << 4) + 8,
                        chunkX,
                        chunkZ
                ));
            }
        }
        markers.sort(Comparator.comparing(WebMapMarkerRecord::kind).thenComparing(WebMapMarkerRecord::displayName));
        return new MarkerSnapshot(markers, waypointByChunk);
    }

    private record MarkerSnapshot(List<WebMapMarkerRecord> markers, Map<String, WebMapMarkerRecord> waypointByChunk) {
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
        if (!isGloballyEnabled() || !settings.isEnabled() || settings.isPaused()) {
            return;
        }
        if (world == null) {
            return;
        }
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
