package org.pexserver.koukunn.bettersurvival.Modules.Feature.Protect;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * CoreProtect 風の軽量監査・ロールバック機能。
 *
 * DBアクセスは ProtectDatabase の専用I/Oスレッドへ分離し、イベント側では
 * 必要最小限の情報採取だけを行う。
 */
public final class ProtectModule implements Listener {
    public static final String FEATURE_KEY = "protect";
    private static final int ROLLBACK_PER_TICK = 80;
    private static final int MAX_ROLLBACK_EVENTS = 10_000;

    private final Loader plugin;
    private final ToggleModule toggle;
    private final ProtectSettings settings;
    private final ProtectDatabase database;
    private final Map<UUID, ContainerSession> containerSessions = new ConcurrentHashMap<>();
    private final Set<UUID> pendingContainerScans = ConcurrentHashMap.newKeySet();
    private final Set<UUID> inspectors = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<Long>> redoIdsByAdmin = new ConcurrentHashMap<>();
    private final AtomicBoolean replayActive = new AtomicBoolean();
    private volatile boolean replaying;

    public ProtectModule(Loader plugin, ToggleModule toggle) {
        this.plugin = plugin;
        this.toggle = toggle;
        this.settings = new ProtectSettings(plugin);
        this.database = new ProtectDatabase(plugin, settings);
    }

    public boolean isEnabled() {
        return toggle.getGlobal(FEATURE_KEY);
    }

    public boolean isRecordingEnabled() {
        return isEnabled() && !replaying;
    }

    public int getRetentionDays() {
        return settings.getRetentionDays();
    }

    public void setRetentionDays(int days) {
        settings.setRetentionDays(days);
        database.requestCleanup();
    }

    public long getDatabaseSizeBytes() {
        return database.getDatabaseSizeBytes();
    }

    public long getDroppedRecords() {
        return database.getDroppedRecords();
    }

    public ProtectDatabase getDatabase() {
        return database;
    }

    public boolean toggleInspector(Player player) {
        UUID uuid = player.getUniqueId();
        if (inspectors.remove(uuid)) {
            return false;
        }
        inspectors.add(uuid);
        return true;
    }

    public boolean isInspector(Player player) {
        return inspectors.contains(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isEnabled()) {
            return;
        }
        Block block = event.getBlock();
        BlockState beforeState = block.getState();
        record(
                event.getPlayer(),
                block.getLocation(),
                ProtectAction.BLOCK_BREAK,
                block.getBlockData().getAsString(),
                "minecraft:air",
                null,
                ProtectBlockSnapshot.capture(beforeState),
                null,
                block.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isEnabled() || event instanceof BlockMultiPlaceEvent) {
            return;
        }
        Block placed = event.getBlockPlaced();
        record(
                event.getPlayer(),
                placed.getLocation(),
                ProtectAction.BLOCK_PLACE,
                event.getBlockReplacedState().getBlockData().getAsString(),
                placed.getBlockData().getAsString(),
                null,
                ProtectBlockSnapshot.capture(event.getBlockReplacedState()),
                ProtectBlockSnapshot.capture(placed.getState()),
                placed.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockMultiPlace(BlockMultiPlaceEvent event) {
        if (!isEnabled()) {
            return;
        }
        String operationId = newOperationId("multi-place");
        for (BlockState replaced : event.getReplacedBlockStates()) {
            Block placed = replaced.getBlock();
            recordPlayerGrouped(
                    event.getPlayer(),
                    placed.getLocation(),
                    ProtectAction.BLOCK_PLACE,
                    replaced.getBlockData().getAsString(),
                    placed.getBlockData().getAsString(),
                    null,
                    ProtectBlockSnapshot.capture(replaced),
                    ProtectBlockSnapshot.capture(placed.getState()),
                    placed.getType().name(),
                    operationId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!isEnabled() || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        Inventory inventory = event.getInventory();
        Location location = resolveInventoryLocation(inventory);
        if (location == null) {
            return;
        }

        ContainerSession session = new ContainerSession(
                location.clone(),
                inventory,
                snapshot(inventory),
                inventory.getType().name());
        containerSessions.put(player.getUniqueId(), session);
        record(
                player,
                location,
                ProtectAction.CONTAINER_OPEN,
                null,
                null,
                null,
                null,
                null,
                session.type());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isEnabled() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (containerSessions.containsKey(player.getUniqueId())) {
            scheduleContainerScan(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isEnabled() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (containerSessions.containsKey(player.getUniqueId())) {
            scheduleContainerScan(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        pendingContainerScans.remove(uuid);
        ContainerSession session = containerSessions.remove(uuid);
        if (session == null || !isEnabled()) {
            return;
        }

        scanContainer(player, session);
        record(
                player,
                session.location(),
                ProtectAction.CONTAINER_CLOSE,
                null,
                null,
                null,
                null,
                null,
                session.type());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInspectorInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isEnabled() || !player.isOp() || !isInspector(player)) {
            return;
        }
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }

        event.setCancelled(true);
        ProtectMenu.openHistoryAt(player, this, clicked.getLocation(), 0, null, null);
    }

    public void rollback(Player admin, int radius, int hours, String actorName) {
        rollbackFiltered(
                admin,
                admin.getLocation(),
                System.currentTimeMillis() - TimeUnit.HOURS.toMillis(Math.max(1, Math.min(24 * 365, hours))),
                normalizeActor(actorName),
                null,
                Math.max(0, Math.min(256, radius)),
                MAX_ROLLBACK_EVENTS,
                false);
    }

    public void lookup(
            Player admin,
            Location origin,
            long sinceMs,
            String actorName,
            Set<ProtectAction> actions,
            int radius,
            int page,
            int limit) {
        if (!isEnabled() || origin == null || origin.getWorld() == null) {
            admin.sendMessage("§c[Protect] Protectが無効、または検索地点が不正です");
            return;
        }
        int pageSize = Math.max(1, Math.min(45, limit));
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * pageSize;
        admin.sendMessage("§7[Protect] 履歴を検索中...");
        database.queryNearby(
                        origin.getWorld().getUID().toString(),
                        origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(),
                        Math.max(0, Math.min(256, radius)),
                        sinceMs,
                        normalizeActor(actorName),
                        actions,
                        pageSize,
                        offset)
                .whenComplete((records, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!admin.isOnline()) return;
                    if (throwable != null) {
                        admin.sendMessage("§c[Protect] lookupに失敗しました");
                        plugin.getLogger().warning("Protect lookup failed: " + throwable.getMessage());
                        return;
                    }
                    admin.sendMessage("§b[Protect] lookup page=" + safePage + " results=" + records.size());
                    for (ProtectRecord record : records) {
                        admin.sendMessage(formatLookupRecord(record));
                    }
                    if (records.isEmpty()) admin.sendMessage("§e[Protect] 条件に一致する履歴はありません");
                }));
    }

    public void rollbackFiltered(
            Player admin,
            Location origin,
            long sinceMs,
            String actorName,
            Set<ProtectAction> actions,
            int radius,
            int limit,
            boolean preview) {
        if (!validateReplay(admin, origin)) return;
        admin.sendMessage("§e[Protect] ロールバック対象を検索中...");
        database.queryRollback(
                        origin.getWorld().getUID().toString(),
                        origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(),
                        Math.max(0, Math.min(256, radius)),
                        sinceMs,
                        normalizeActor(actorName),
                        actions,
                        Math.max(1, Math.min(MAX_ROLLBACK_EVENTS, limit)))
                .whenComplete((records, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!handleReplayQuery(admin, records, throwable, "rollback")) return;
                    if (preview) {
                        admin.sendMessage("§e[Protect] PREVIEW: " + records.size()
                                + "件がロールバック対象です。ワールドは変更していません。");
                        return;
                    }
                    applyRollback(admin, records);
                }));
    }

    public void restoreFiltered(
            Player admin,
            Location origin,
            long sinceMs,
            String actorName,
            Set<ProtectAction> actions,
            int radius,
            int limit,
            boolean preview) {
        if (!validateReplay(admin, origin)) return;
        admin.sendMessage("§e[Protect] Restore対象を検索中...");
        database.queryRestore(
                        origin.getWorld().getUID().toString(),
                        origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(),
                        Math.max(0, Math.min(256, radius)),
                        sinceMs,
                        normalizeActor(actorName),
                        actions,
                        Math.max(1, Math.min(MAX_ROLLBACK_EVENTS, limit)))
                .whenComplete((records, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!handleReplayQuery(admin, records, throwable, "restore")) return;
                    if (preview) {
                        admin.sendMessage("§e[Protect] PREVIEW: " + records.size()
                                + "件がRestore対象です。ワールドは変更していません。");
                        return;
                    }
                    applyRestore(admin, records, true);
                }));
    }

    public void undo(Player admin) {
        if (!isEnabled()) {
            admin.sendMessage("§c[Protect] Protectは無効です");
            return;
        }
        admin.sendMessage("§e[Protect] 最後のロールバックを検索中...");
        database.queryLastRollback(admin.getName(), MAX_ROLLBACK_EVENTS)
                .whenComplete((records, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!handleReplayQuery(admin, records, throwable, "undo")) return;
                    applyRestore(admin, records, true);
                }));
    }

    public void redo(Player admin) {
        List<Long> ids = redoIdsByAdmin.get(admin.getUniqueId());
        if (ids == null || ids.isEmpty()) {
            admin.sendMessage("§e[Protect] このサーバー起動中にredoできるUndoはありません");
            return;
        }
        database.queryByIds(ids)
                .whenComplete((records, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!handleReplayQuery(admin, records, throwable, "redo")) return;
                    applyRollback(admin, records);
                    redoIdsByAdmin.remove(admin.getUniqueId());
                }));
    }

    public void stats(Player admin) {
        database.getStatusSnapshot().whenComplete((status, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!admin.isOnline()) return;
                    if (throwable != null || status == null) {
                        admin.sendMessage("§c[Protect] Status取得に失敗しました");
                        return;
                    }
                    admin.sendMessage("§b[Protect Status] "
                            + (status.ready() ? "§aONLINE" : "§cOFFLINE"));
                    admin.sendMessage("§7Records: §f" + status.totalRecords()
                            + " §8(24h: " + status.last24hRecords()
                            + ", rolled-back: " + status.rolledBackRecords() + ")");
                    admin.sendMessage("§7Storage: §f" + formatBytes(status.totalStorageBytes())
                            + " §8(DB " + formatBytes(status.databaseBytes())
                            + " / WAL " + formatBytes(status.walBytes())
                            + " / SHM " + formatBytes(status.shmBytes()) + ")");
                    admin.sendMessage("§7Disk free: §f" + formatBytes(status.diskUsableBytes())
                            + " §8/ " + formatBytes(status.diskTotalBytes()));
                    admin.sendMessage("§7Queue: §f" + status.queueSize() + "/" + status.queueCapacity()
                            + String.format(" §8(%.1f%%)", status.queueUsagePercent())
                            + " §7Dropped: §f" + status.droppedRecords());
                    admin.sendMessage("§7Retention: §f" + getRetentionDays() + "d");
                }));
    }

    public void resetDatabase(Player admin, Runnable onComplete) {
        if (!replayActive.compareAndSet(false, true)) {
            admin.sendMessage("§c[Protect] Rollback/RestoreまたはDBメンテナンス実行中です");
            if (onComplete != null) onComplete.run();
            return;
        }
        database.resetDatabase().whenComplete((result, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    replayActive.set(false);
                    if (throwable != null || result == null) {
                        plugin.getLogger().warning("Protect database reset failed: "
                                + (throwable == null ? "unknown" : throwable.getMessage()));
                        if (admin.isOnline()) {
                            admin.sendMessage("§c[Protect] DB初期化に失敗しました");
                        }
                        if (onComplete != null) onComplete.run();
                        return;
                    }
                    redoIdsByAdmin.clear();
                    if (admin.isOnline()) {
                        admin.sendMessage("§a[Protect] DB初期化完了: "
                                + result.deletedRecords() + "件削除 / Queue "
                                + result.clearedQueuedRecords() + "件破棄");
                    }
                    if (onComplete != null) onComplete.run();
                }));
    }

    public void purge(Player admin, long cutoffMs, String actorName) {
        database.purgeOlderThan(cutoffMs, normalizeActor(actorName))
                .whenComplete((deleted, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!admin.isOnline()) return;
                    if (throwable != null) {
                        admin.sendMessage("§c[Protect] purgeに失敗しました");
                        return;
                    }
                    admin.sendMessage("§a[Protect] " + deleted + "件の古いログを削除しました");
                }));
    }

    public void rollbackSingle(Player admin, ProtectRecord record) {
        if (record == null || !record.reversible() || record.rolledBack()) {
            admin.sendMessage("§c[Protect] このログはロールバックできません");
            return;
        }
        database.queryOperationGroup(record, 0)
                .whenComplete((records, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!handleReplayQuery(admin, records, throwable, "single rollback")) return;
                    applyRollback(admin, records);
                }));
    }

    public void restoreSingle(Player admin, ProtectRecord record) {
        if (record == null || !record.reversible() || !record.rolledBack()) {
            admin.sendMessage("§c[Protect] このログはRestoreできません");
            return;
        }
        database.queryOperationGroup(record, 1)
                .whenComplete((records, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!handleReplayQuery(admin, records, throwable, "single restore")) return;
                    applyRestore(admin, records, true);
                }));
    }

    public void shutdown() {
        inspectors.clear();
        containerSessions.clear();
        pendingContainerScans.clear();
        redoIdsByAdmin.clear();
        database.shutdown();
    }

    private void scheduleContainerScan(Player player) {
        UUID uuid = player.getUniqueId();
        if (!pendingContainerScans.add(uuid)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingContainerScans.remove(uuid);
            ContainerSession session = containerSessions.get(uuid);
            if (session == null || !player.isOnline() || !isEnabled()) {
                return;
            }
            scanContainer(player, session);
        });
    }

    private void scanContainer(Player player, ContainerSession session) {
        Inventory inventory = session.inventory();
        ItemStack[] previous = session.snapshot();
        int size = Math.min(previous.length, inventory.getSize());

        for (int slot = 0; slot < size; slot++) {
            ItemStack before = previous[slot];
            ItemStack after = copyItem(inventory.getItem(slot));
            if (Objects.equals(before, after)) {
                continue;
            }

            record(
                    player,
                    session.location(),
                    ProtectAction.CONTAINER_CHANGE,
                    null,
                    null,
                    slot,
                    serializeItem(before),
                    serializeItem(after),
                    describeContainerChange(before, after));
            previous[slot] = after;
        }
    }

    private void applyRollback(Player admin, List<ProtectRecord> source) {
        List<ProtectRecord> ordered = new ArrayList<>(source);
        ordered.sort(Comparator.comparingLong(ProtectRecord::timeMs)
                .thenComparingLong(ProtectRecord::id)
                .reversed());
        replay(admin, ordered, false, "ロールバック", appliedIds ->
                redoIdsByAdmin.remove(admin.getUniqueId()));
    }

    private void applyRestore(Player admin, List<ProtectRecord> source, boolean rememberForRedo) {
        List<ProtectRecord> ordered = new ArrayList<>(source);
        ordered.sort(Comparator.comparingLong(ProtectRecord::timeMs)
                .thenComparingLong(ProtectRecord::id));
        replay(admin, ordered, true, "Restore", appliedIds -> {
            if (rememberForRedo) {
                redoIdsByAdmin.put(admin.getUniqueId(), List.copyOf(appliedIds));
            }
        });
    }

    private void replay(
            Player admin,
            List<ProtectRecord> source,
            boolean forward,
            String label,
            Consumer<List<Long>> completion) {
        if (!replayActive.compareAndSet(false, true)) {
            admin.sendMessage("§c[Protect] 別のRollback/Restoreが実行中です。完了後に再実行してください");
            return;
        }

        Deque<ProtectRecord> remaining = new ArrayDeque<>(source);
        List<Long> appliedIds = new ArrayList<>();
        Set<String> loadingChunks = ConcurrentHashMap.newKeySet();
        Set<String> unavailableChunks = ConcurrentHashMap.newKeySet();
        int total = source.size();

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int processed = 0;
            replaying = true;
            try {
                while (processed < ROLLBACK_PER_TICK && !remaining.isEmpty()) {
                    ProtectRecord record = remaining.peekFirst();

                    if (record.operationId() != null && !record.operationId().isBlank()) {
                        String operationId = record.operationId();
                        List<ProtectRecord> group = remaining.stream()
                                .filter(candidate -> operationId.equals(candidate.operationId()))
                                .toList();
                        ChunkReadiness readiness = ensureChunksLoaded(
                                group, loadingChunks, unavailableChunks);
                        if (readiness == ChunkReadiness.LOADING) {
                            break;
                        }
                        remaining.removeIf(candidate -> operationId.equals(candidate.operationId()));
                        if (readiness == ChunkReadiness.UNAVAILABLE) {
                            processed += group.size();
                            continue;
                        }

                        for (ProtectRecord groupedRecord : group) {
                            boolean applied = forward
                                    ? applyRecordForward(groupedRecord)
                                    : applyRecord(groupedRecord);
                            if (applied) {
                                appliedIds.add(groupedRecord.id());
                            }
                        }
                        processed += group.size();
                        continue;
                    }

                    ChunkReadiness readiness = ensureChunksLoaded(
                            List.of(record), loadingChunks, unavailableChunks);
                    if (readiness == ChunkReadiness.LOADING) {
                        break;
                    }

                    remaining.removeFirst();
                    if (readiness == ChunkReadiness.UNAVAILABLE) {
                        processed++;
                        continue;
                    }

                    boolean applied = forward ? applyRecordForward(record) : applyRecord(record);
                    if (applied) {
                        appliedIds.add(record.id());
                    }
                    processed++;
                }
            } finally {
                replaying = false;
            }

            if (!remaining.isEmpty()) {
                return;
            }

            if (forward) {
                database.markRestored(appliedIds);
            } else {
                database.markRolledBack(appliedIds, admin.getName());
            }
            if (completion != null) {
                completion.accept(List.copyOf(appliedIds));
            }
            replayActive.set(false);
            if (admin.isOnline()) {
                admin.sendMessage("§a[Protect] " + label + "完了: "
                        + appliedIds.size() + "/" + total + " 件"
                        + (unavailableChunks.isEmpty() ? "" : " §e(未読込Chunkを一部スキップ)"));
            }
            task.cancel();
        }, 1L, 1L);
    }

    private ChunkReadiness ensureChunksLoaded(
            List<ProtectRecord> records,
            Set<String> loadingChunks,
            Set<String> unavailableChunks) {
        boolean waiting = false;
        for (ProtectRecord record : records) {
            World world = worldFor(record);
            if (world == null) {
                return ChunkReadiness.UNAVAILABLE;
            }

            int chunkX = record.x() >> 4;
            int chunkZ = record.z() >> 4;
            String chunkKey = record.worldUuid() + ":" + chunkX + ":" + chunkZ;
            if (unavailableChunks.contains(chunkKey)) {
                return ChunkReadiness.UNAVAILABLE;
            }
            if (world.isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }

            waiting = true;
            if (loadingChunks.add(chunkKey)) {
                try {
                    world.getChunkAtAsync(chunkX, chunkZ, false).whenComplete((chunk, throwable) -> {
                        loadingChunks.remove(chunkKey);
                        if (throwable != null || chunk == null) {
                            unavailableChunks.add(chunkKey);
                        }
                    });
                } catch (Throwable throwable) {
                    loadingChunks.remove(chunkKey);
                    unavailableChunks.add(chunkKey);
                    return ChunkReadiness.UNAVAILABLE;
                }
            }
        }
        return waiting ? ChunkReadiness.LOADING : ChunkReadiness.READY;
    }

    private enum ChunkReadiness {
        READY,
        LOADING,
        UNAVAILABLE
    }

    private boolean applyRecord(ProtectRecord record) {
        World world = worldFor(record);
        if (world == null) {
            return false;
        }

        Block block = world.getBlockAt(record.x(), record.y(), record.z());
        try {
            if (record.action().isBlockMutation()) {
                return restoreBlock(block, record.blockBefore(), record.itemBefore());
            }
            if (record.action() == ProtectAction.CONTAINER_CHANGE) {
                return restoreContainerSlot(block, record.slot(), record.itemBefore());
            }
            return false;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Protect rollback apply failed at "
                    + record.worldName() + " " + record.x() + "," + record.y() + "," + record.z()
                    + ": " + throwable.getMessage());
            return false;
        }
    }

    private boolean applyRecordForward(ProtectRecord record) {
        World world = worldFor(record);
        if (world == null) return false;

        Block block = world.getBlockAt(record.x(), record.y(), record.z());
        try {
            if (record.action().isBlockMutation()) {
                return restoreBlock(block, record.blockAfter(), record.itemAfter());
            }
            if (record.action() == ProtectAction.CONTAINER_CHANGE) {
                return restoreContainerSlot(block, record.slot(), record.itemAfter());
            }
            return false;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Protect restore apply failed at "
                    + record.worldName() + " " + record.x() + "," + record.y() + "," + record.z()
                    + ": " + throwable.getMessage());
            return false;
        }
    }

    private boolean validateReplay(Player admin, Location origin) {
        if (!isEnabled()) {
            admin.sendMessage("§c[Protect] Protectは無効です");
            return false;
        }
        if (origin == null || origin.getWorld() == null) {
            admin.sendMessage("§c[Protect] 対象地点が不正です");
            return false;
        }
        return true;
    }

    private boolean handleReplayQuery(
            Player admin,
            List<ProtectRecord> records,
            Throwable throwable,
            String operation) {
        if (!admin.isOnline()) return false;
        if (throwable != null) {
            plugin.getLogger().warning("Protect " + operation + " query failed: " + throwable.getMessage());
            admin.sendMessage("§c[Protect] " + operation + "検索に失敗しました");
            return false;
        }
        if (records == null || records.isEmpty()) {
            admin.sendMessage("§e[Protect] 条件に一致する復元可能なログはありません");
            return false;
        }
        return true;
    }

    private String formatLookupRecord(ProtectRecord record) {
        long ageSeconds = Math.max(0L, (System.currentTimeMillis() - record.timeMs()) / 1000L);
        String age = ageSeconds < 60 ? ageSeconds + "s"
                : ageSeconds < 3600 ? (ageSeconds / 60) + "m"
                : ageSeconds < 86400 ? (ageSeconds / 3600) + "h"
                : (ageSeconds / 86400) + "d";
        return "§8#" + record.id()
                + " §7" + age
                + " §f" + (record.actorName() == null ? "#unknown" : record.actorName())
                + " §b" + record.action().name().toLowerCase()
                + " §7@" + record.x() + "," + record.y() + "," + record.z()
                + (record.rolledBack() ? " §8[rolled-back]" : "")
                + (record.detail() == null || record.detail().isBlank() ? "" : " §8" + record.detail());
    }

    private boolean restoreBlock(Block block, String blockDataText, byte[] blockSnapshot) {
        if (blockDataText == null || blockDataText.isBlank()) {
            return false;
        }
        BlockData blockData = Bukkit.createBlockData(blockDataText);
        block.setBlockData(blockData, false);
        if (blockSnapshot != null && blockSnapshot.length > 0) {
            BlockState state = block.getState();
            ProtectBlockSnapshot.apply(state, blockSnapshot);
            state.update(true, false);
        }
        return true;
    }

    private World worldFor(ProtectRecord record) {
        try {
            return Bukkit.getWorld(UUID.fromString(record.worldUuid()));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private boolean restoreContainerSlot(Block block, Integer slot, byte[] itemBytes) {
        if (slot == null) {
            return false;
        }
        BlockState state = block.getState();
        if (!(state instanceof Container container)) {
            return false;
        }
        Inventory inventory = container.getInventory();
        if (slot < 0 || slot >= inventory.getSize()) {
            return false;
        }
        inventory.setItem(slot, deserializeItem(itemBytes));
        return true;
    }

    private void recordBlock(
            Player player,
            Location location,
            ProtectAction action,
            String before,
            String after,
            String detail) {
        record(player, location, action, before, after, null, null, null, detail);
    }

    void recordPlayer(
            Player player,
            Location location,
            ProtectAction action,
            String blockBefore,
            String blockAfter,
            Integer slot,
            byte[] itemBefore,
            byte[] itemAfter,
            String detail) {
        recordPlayerGrouped(player, location, action, blockBefore, blockAfter, slot,
                itemBefore, itemAfter, detail, null);
    }

    void recordPlayerGrouped(
            Player player,
            Location location,
            ProtectAction action,
            String blockBefore,
            String blockAfter,
            Integer slot,
            byte[] itemBefore,
            byte[] itemAfter,
            String detail,
            String operationId) {
        if (player == null) {
            recordActorGrouped(null, "#unknown", location, action, blockBefore, blockAfter, slot,
                    itemBefore, itemAfter, detail, operationId);
            return;
        }
        recordActorGrouped(
                player.getUniqueId().toString(),
                player.getName(),
                location,
                action,
                blockBefore,
                blockAfter,
                slot,
                itemBefore,
                itemAfter,
                detail,
                operationId);
    }

    void recordSystem(
            String actorName,
            Location location,
            ProtectAction action,
            String blockBefore,
            String blockAfter,
            Integer slot,
            byte[] itemBefore,
            byte[] itemAfter,
            String detail) {
        recordSystemGrouped(actorName, location, action, blockBefore, blockAfter, slot,
                itemBefore, itemAfter, detail, null);
    }

    void recordSystemGrouped(
            String actorName,
            Location location,
            ProtectAction action,
            String blockBefore,
            String blockAfter,
            Integer slot,
            byte[] itemBefore,
            byte[] itemAfter,
            String detail,
            String operationId) {
        recordActorGrouped(null, actorName, location, action, blockBefore, blockAfter, slot,
                itemBefore, itemAfter, detail, operationId);
    }

    void recordActor(
            String actorUuid,
            String actorName,
            Location location,
            ProtectAction action,
            String blockBefore,
            String blockAfter,
            Integer slot,
            byte[] itemBefore,
            byte[] itemAfter,
            String detail) {
        recordActorGrouped(actorUuid, actorName, location, action, blockBefore, blockAfter, slot,
                itemBefore, itemAfter, detail, null);
    }

    void recordActorGrouped(
            String actorUuid,
            String actorName,
            Location location,
            ProtectAction action,
            String blockBefore,
            String blockAfter,
            Integer slot,
            byte[] itemBefore,
            byte[] itemAfter,
            String detail,
            String operationId) {
        if (!isRecordingEnabled() || action == null || location == null || location.getWorld() == null) {
            return;
        }
        database.enqueue(new ProtectRecord(
                0L,
                System.currentTimeMillis(),
                actorUuid,
                actorName == null || actorName.isBlank() ? "#unknown" : actorName,
                location.getWorld().getUID().toString(),
                location.getWorld().getName(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                action,
                blockBefore,
                blockAfter,
                slot,
                itemBefore,
                itemAfter,
                detail,
                operationId,
                false));
    }

    static String newOperationId(String kind) {
        String prefix = kind == null || kind.isBlank() ? "op" : kind;
        return prefix + ":" + UUID.randomUUID();
    }

    private void record(
            Player player,
            Location location,
            ProtectAction action,
            String blockBefore,
            String blockAfter,
            Integer slot,
            byte[] itemBefore,
            byte[] itemAfter,
            String detail) {
        recordPlayer(player, location, action, blockBefore, blockAfter, slot,
                itemBefore, itemAfter, detail);
    }

    private Location resolveInventoryLocation(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof DoubleChest doubleChest) {
            return doubleChest.getLocation();
        }
        if (holder instanceof BlockInventoryHolder blockHolder) {
            try {
                return blockHolder.getBlock().getLocation();
            } catch (IllegalStateException ignored) {
                return null;
            }
        }
        try {
            return inventory.getLocation();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private ItemStack[] snapshot(Inventory inventory) {
        ItemStack[] source = inventory.getContents();
        ItemStack[] result = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            result[i] = copyItem(source[i]);
        }
        return result;
    }

    static ItemStack copyItem(ItemStack item) {
        if (item == null || item.getAmount() <= 0 || item.getType().isAir()) {
            return null;
        }
        return item.clone();
    }

    public void logCustomBlockChange(
            Player player,
            Location location,
            String beforeState,
            String afterState,
            String detail) {
        recordPlayer(player, location, ProtectAction.CUSTOM_BLOCK,
                beforeState, afterState, null, null, null, detail);
    }

    public void logCustomBlockChange(
            String actorName,
            Location location,
            String beforeState,
            String afterState,
            String detail) {
        recordSystem(actorName == null ? "#custom" : actorName, location,
                ProtectAction.CUSTOM_BLOCK,
                beforeState, afterState, null, null, null, detail);
    }

    private static String describeContainerChange(ItemStack before, ItemStack after) {
        ItemStack oldItem = copyItem(before);
        ItemStack newItem = copyItem(after);
        String tag = (oldItem != null && oldItem.getType() == Material.BUNDLE)
                || (newItem != null && newItem.getType() == Material.BUNDLE)
                ? "#bundle " : "";
        if (oldItem == null && newItem != null) {
            return tag + "DEPOSIT " + itemSummary(newItem);
        }
        if (oldItem != null && newItem == null) {
            return tag + "WITHDRAW " + itemSummary(oldItem);
        }
        if (oldItem != null && newItem != null
                && oldItem.isSimilar(newItem)) {
            int delta = newItem.getAmount() - oldItem.getAmount();
            if (delta > 0) {
                return tag + "DEPOSIT " + newItem.getType().name() + " x" + delta;
            }
            if (delta < 0) {
                return tag + "WITHDRAW " + oldItem.getType().name() + " x" + (-delta);
            }
        }
        return tag + "REPLACE " + itemSummary(oldItem) + " -> " + itemSummary(newItem);
    }

    static byte[] serializeItem(ItemStack item) {
        ItemStack copy = copyItem(item);
        return copy == null ? null : copy.serializeAsBytes();
    }

    public static ItemStack deserializeItem(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try {
            ItemStack item = ItemStack.deserializeBytes(data);
            return copyItem(item);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String itemSummary(byte[] data) {
        return itemSummary(deserializeItem(data));
    }

    static String itemSummary(ItemStack item) {
        ItemStack copy = copyItem(item);
        if (copy == null) {
            return "空";
        }
        return copy.getType().name() + " x" + copy.getAmount();
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        double kib = bytes / 1024.0;
        if (kib < 1024.0) return String.format("%.1f KiB", kib);
        double mib = kib / 1024.0;
        if (mib < 1024.0) return String.format("%.1f MiB", mib);
        double gib = mib / 1024.0;
        return String.format("%.2f GiB", gib);
    }

    private static String normalizeActor(String actorName) {
        if (actorName == null) {
            return null;
        }
        String trimmed = actorName.trim();
        return trimmed.isEmpty() || "*".equals(trimmed) ? null : trimmed;
    }

    private record ContainerSession(
            Location location,
            Inventory inventory,
            ItemStack[] snapshot,
            String type) {
    }
}
