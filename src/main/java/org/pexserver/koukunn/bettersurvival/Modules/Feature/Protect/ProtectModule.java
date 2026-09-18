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
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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

    public ProtectModule(Loader plugin, ToggleModule toggle) {
        this.plugin = plugin;
        this.toggle = toggle;
        this.settings = new ProtectSettings(plugin);
        this.database = new ProtectDatabase(plugin, settings);
    }

    public boolean isEnabled() {
        return toggle.getGlobal(FEATURE_KEY);
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
        recordBlock(
                event.getPlayer(),
                block.getLocation(),
                ProtectAction.BLOCK_BREAK,
                block.getBlockData().getAsString(),
                "minecraft:air",
                block.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isEnabled()) {
            return;
        }
        recordBlock(
                event.getPlayer(),
                event.getBlockPlaced().getLocation(),
                ProtectAction.BLOCK_PLACE,
                event.getBlockReplacedState().getBlockData().getAsString(),
                event.getBlockPlaced().getBlockData().getAsString(),
                event.getBlockPlaced().getType().name());
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
        if (!isEnabled()) {
            admin.sendMessage("§cProtect は無効です");
            return;
        }

        int safeRadius = Math.max(0, Math.min(256, radius));
        int safeHours = Math.max(1, Math.min(24 * 365, hours));
        Location origin = admin.getLocation();
        long since = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(safeHours);

        admin.sendMessage("§e[Protect] ロールバック対象を検索中...");
        database.queryRollback(
                        origin.getWorld().getUID().toString(),
                        origin.getBlockX(),
                        origin.getBlockY(),
                        origin.getBlockZ(),
                        safeRadius,
                        since,
                        normalizeActor(actorName),
                        MAX_ROLLBACK_EVENTS)
                .whenComplete((records, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        plugin.getLogger().warning("Protect rollback query failed: " + throwable.getMessage());
                        if (admin.isOnline()) {
                            admin.sendMessage("§c[Protect] 検索に失敗しました");
                        }
                        return;
                    }
                    if (records == null || records.isEmpty()) {
                        if (admin.isOnline()) {
                            admin.sendMessage("§e[Protect] 対象ログはありません");
                        }
                        return;
                    }
                    applyRollback(admin, records);
                }));
    }

    public void rollbackSingle(Player admin, ProtectRecord record) {
        if (record == null || !record.reversible() || record.rolledBack()) {
            admin.sendMessage("§c[Protect] このログはロールバックできません");
            return;
        }
        applyRollback(admin, List.of(record));
    }

    public void shutdown() {
        inspectors.clear();
        containerSessions.clear();
        pendingContainerScans.clear();
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
        Deque<ProtectRecord> remaining = new ArrayDeque<>(source);
        List<Long> appliedIds = new ArrayList<>();
        int total = source.size();

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int processed = 0;
            while (processed < ROLLBACK_PER_TICK && !remaining.isEmpty()) {
                ProtectRecord record = remaining.removeFirst();
                if (applyRecord(record)) {
                    appliedIds.add(record.id());
                }
                processed++;
            }

            if (!remaining.isEmpty()) {
                return;
            }

            database.markRolledBack(appliedIds, admin.getName());
            if (admin.isOnline()) {
                admin.sendMessage("§a[Protect] ロールバック完了: "
                        + appliedIds.size() + "/" + total + " 件");
            }
            task.cancel();
        }, 1L, 1L);
    }

    private boolean applyRecord(ProtectRecord record) {
        World world;
        try {
            world = Bukkit.getWorld(UUID.fromString(record.worldUuid()));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        if (world == null) {
            return false;
        }

        Block block = world.getBlockAt(record.x(), record.y(), record.z());
        try {
            if (record.action().isBlockMutation()) {
                return restoreBlock(block, record.blockBefore());
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

    private boolean restoreBlock(Block block, String blockDataText) {
        if (blockDataText == null || blockDataText.isBlank()) {
            return false;
        }
        BlockData blockData = Bukkit.createBlockData(blockDataText);
        block.setBlockData(blockData, false);
        return true;
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
        if (player == null) {
            recordActor(null, "#unknown", location, action, blockBefore, blockAfter, slot,
                    itemBefore, itemAfter, detail);
            return;
        }
        recordActor(
                player.getUniqueId().toString(),
                player.getName(),
                location,
                action,
                blockBefore,
                blockAfter,
                slot,
                itemBefore,
                itemAfter,
                detail);
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
        recordActor(null, actorName, location, action, blockBefore, blockAfter, slot,
                itemBefore, itemAfter, detail);
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
        if (!isEnabled() || action == null || location == null || location.getWorld() == null) {
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
                false));
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
        return null;
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
        if (oldItem == null && newItem != null) {
            return "DEPOSIT " + itemSummary(newItem);
        }
        if (oldItem != null && newItem == null) {
            return "WITHDRAW " + itemSummary(oldItem);
        }
        if (oldItem != null && newItem != null
                && oldItem.isSimilar(newItem)) {
            int delta = newItem.getAmount() - oldItem.getAmount();
            if (delta > 0) {
                return "DEPOSIT " + newItem.getType().name() + " x" + delta;
            }
            if (delta < 0) {
                return "WITHDRAW " + oldItem.getType().name() + " x" + (-delta);
            }
        }
        return "REPLACE " + itemSummary(oldItem) + " -> " + itemSummary(newItem);
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
