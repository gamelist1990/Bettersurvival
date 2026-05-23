package org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.persistence.PersistentDataType;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock.ChestLockModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopModule;
import org.pexserver.koukunn.bettersurvival.Modules.ItemCombineModule;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

/**
 * chest-id / chestsub-id の NameTag とチェストを合成して作る共有ストレージ機能。
 *
 * main と sub は単体チェスト / ラージチェストの両方をサポートし、
 * main から close 時の再仕分けと sort 棒 UI からの設定変更を行う。
 */
@SuppressWarnings("deprecation")
public class SharedStorageModule implements Listener {

    private static final String FEATURE_KEY = "sharedstorage";
    private static final String ROLE_MAIN = "main";
    private static final String ROLE_SUB = "sub";
    private static final String MAIN_PREFIX = "chest-";
    private static final String SUB_PREFIX = "chestsub-";
    private static final double MAX_SUB_DISTANCE = 15.0D;
    private static final boolean DEFAULT_ALLOW_SUB_INSERT = false;
    private static final boolean DEFAULT_ALLOW_SUB_EXTRACT = true;
    private static final boolean DEFAULT_ALLOW_SUB_HOPPER_INSERT = false;
    private static final boolean DEFAULT_ALLOW_SUB_HOPPER_EXTRACT = true;
    private static final boolean DEFAULT_ALLOW_MAIN_INSERT = true;
    private static final boolean DEFAULT_ALLOW_MAIN_EXTRACT = true;
    private static final boolean DEFAULT_ENABLE_TRANSFER_PARTICLES = true;
    private static final boolean DEFAULT_ENABLE_SUB_FRAME_FILTER = false;
    private static final int MENU_TOGGLE_SLOT = 11;
    private static final int MENU_RESET_SLOT = 13;
    private static final int MENU_EXTRACT_SLOT = 15;
    private static final int MENU_SUB_HOPPER_INSERT_SLOT = 29;
    private static final int MENU_SUB_HOPPER_EXTRACT_SLOT = 33;
    private static final int MENU_MAIN_INSERT_SLOT = 20;
    private static final int MENU_MAIN_EXTRACT_SLOT = 24;
    private static final int MENU_PARTICLE_SLOT = 31;
    private static final int MENU_FRAME_FILTER_SLOT = 30;
    private static final int MENU_CLOSE_SLOT = 22;

    private final Loader plugin;
    private final ToggleModule toggle;
    private final ChestLockModule chestLock;
    private final ChestShopModule chestShop;
    private final SharedStorageStore store;
    private final NamespacedKey roleKey;
    private final NamespacedKey idKey;
    private final Map<String, SharedNetwork> networks = new LinkedHashMap<>();
    private final Map<String, Placement> placements = new LinkedHashMap<>();
    private final Set<String> scheduledRedistributions = new LinkedHashSet<>();
    private final Map<String, List<ItemStack>> pendingMainContributions = new LinkedHashMap<>();
    private final Map<String, List<ItemStack>> pendingInjectedItems = new LinkedHashMap<>();
    private final Map<UUID, MainInventorySnapshot> mainSnapshots = new LinkedHashMap<>();

    public SharedStorageModule(
            Loader plugin,
            ToggleModule toggle,
            ItemCombineModule itemCombineModule,
            ChestLockModule chestLock,
            ChestShopModule chestShop) {
        this.plugin = plugin;
        this.toggle = toggle;
        this.chestLock = chestLock;
        this.chestShop = chestShop;
        this.store = new SharedStorageStore(plugin.getConfigManager());
        this.roleKey = new NamespacedKey(plugin, "shared_storage_role");
        this.idKey = new NamespacedKey(plugin, "shared_storage_id");
        loadNetworks();
        if (this.chestLock != null) {
            this.chestLock.registerExternalResolver(this::resolveMainLockLocation);
            this.chestLock.registerExternalProtectionResolver(this::resolveChestLockProtection);
        }
        itemCombineModule.recipe("shared_storage_main")
                .first(this::isPlainChestItem)
                .second(this::isMainNameTag)
                .groundRadius(0.5D)
                .airRadius(1.5D)
                .verticalRadius(2.0D)
                .allowAirCombine(true)
                .then(match -> craftSharedChest(match, ROLE_MAIN));
        itemCombineModule.recipe("shared_storage_sub")
                .first(this::isPlainChestItem)
                .second(this::isSubNameTag)
                .groundRadius(0.5D)
                .airRadius(1.5D)
                .verticalRadius(2.0D)
                .allowAirCombine(true)
                .then(match -> craftSharedChest(match, ROLE_SUB));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.CHEST)
            return;
        StorageItemData itemData = getStorageItemData(event.getItemInHand());

        if (itemData == null) {
            if (isAdjacentToMainChest(event.getBlockPlaced())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§c主チェストの隣にはチェストを設置できません");
            }
            return;
        }

        if (ROLE_MAIN.equals(itemData.role()) && hasAdjacentChest(event.getBlockPlaced())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c主チェストは単体でのみ設置できます");
            return;
        }

        List<Location> footprint = resolveContainerLocations(event.getBlockPlaced().getLocation());
        if (ROLE_MAIN.equals(itemData.role()) && footprint.size() > 1) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c主チェストはラージチェストにできません");
            return;
        }
        PlacementConflict conflict = findPlacementConflict(footprint, itemData);
        if (conflict != PlacementConflict.NONE) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(conflict.message(itemData.id()));
            return;
        }

        PlacementResult result = registerPlacement(itemData, footprint);
        if (result != PlacementResult.SUCCESS) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(result.message(itemData.id()));
            return;
        }
        saveNetworks();
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Placement placement = resolvePlacement(event.getBlock().getLocation());
        if (placement == null)
            return;

        SharedNetwork network = networks.get(placement.id());
        if (network == null)
            return;

        handleDestroyedContainers(List.of(event.getBlock().getLocation()));

        if (event.getPlayer().getGameMode() != org.bukkit.GameMode.CREATIVE) {
            event.setDropItems(false);
            event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation().add(0.5D, 0.1D, 0.5D),
                    createSharedChestItem(placement.id(), placement.role()));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.CHEST)
            return;
        Placement placement = resolvePlacement(clicked.getLocation());
        if (placement == null || !placement.isMain())
            return;
        if (!toggle.getGlobal(FEATURE_KEY))
            return;
        SharedNetwork network = networks.get(placement.id());
        if (network == null) {
            event.getPlayer().sendMessage("§c共有ストレージの情報が見つかりません");
            return;
        }
        ItemStack item = event.getItem();
        boolean hopperClick = event.getAction().isRightClick()
                && item != null
                && item.getType() == Material.HOPPER;
        if (hopperClick && ((placement.isMain() && network.allowMainInsert()) || (!placement.isMain() && network.allowSubHopperInsert()))) {
            return;
        }
        if (!event.getPlayer().isSneaking())
            return;
        if (!canUseContainer(event.getPlayer(), clicked.getLocation()))
            return;

        event.setCancelled(true);
        openMainMenu(event.getPlayer(), network);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        Placement placement = resolvePlacement(event.getInventory());
        if (placement == null || !placement.isMain() || !toggle.getGlobal(FEATURE_KEY))
            return;
        SharedNetwork network = networks.get(placement.id());
        if (network == null)
            return;
        if (!(event.getPlayer() instanceof Player player))
            return;
        mainSnapshots.put(player.getUniqueId(),
                new MainInventorySnapshot(network.id(), snapshotInventory(event.getInventory())));
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        Placement placement = resolvePlacement(event.getInventory());
        if (placement == null || !placement.isMain() || !toggle.getGlobal(FEATURE_KEY))
            return;
        SharedNetwork network = networks.get(placement.id());
        if (network == null)
            return;
        if (scheduledRedistributions.contains(network.id()))
            return;
        Player player = event.getPlayer() instanceof Player p ? p : null;
        MainInventorySnapshot snapshot = player == null ? null : mainSnapshots.remove(player.getUniqueId());
        if (snapshot != null && Objects.equals(snapshot.networkId(), network.id())
                && inventoryMatchesSnapshot(event.getInventory(), snapshot.contents()))
            return;
        redistributeNetwork(network, player, false);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        SubAccess access = getSubAccess(event.getView().getTopInventory());
        if (access == null)
            return;
        Inventory top = event.getView().getTopInventory();
        Placement placement = resolvePlacement(top);
        ItemStack subFilter = resolveSubFrameFilter(placement);
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0)
            return;
        if (!access.allowExtract() && rawSlot < top.getSize()) {
            ItemStack current = event.getCurrentItem();
            if (current != null && current.getType() != Material.AIR) {
                event.setCancelled(true);
                return;
            }
        }
        if (event.isShiftClick() && rawSlot >= top.getSize()) {
            ItemStack current = event.getCurrentItem();
            if (!access.allowInsert() || (subFilter != null && current != null && current.getType() != Material.AIR && !matchesFilter(current, subFilter)))
                event.setCancelled(true);
            return;
        }
        if (rawSlot >= top.getSize())
            return;
        ItemStack cursor = event.getCursor();
        ItemStack hotbarItem = event.getClick().isKeyboardClick() ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) : null;
        ItemStack insertItem = cursor != null && cursor.getType() != Material.AIR ? cursor : hotbarItem;
        if (!access.allowInsert() && insertItem != null && insertItem.getType() != Material.AIR) {
            event.setCancelled(true);
            return;
        }
        if (subFilter != null && insertItem != null && insertItem.getType() != Material.AIR && !matchesFilter(insertItem, subFilter))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        SubAccess access = getSubAccess(event.getView().getTopInventory());
        if (access == null)
            return;
        Placement placement = resolvePlacement(event.getView().getTopInventory());
        ItemStack subFilter = resolveSubFrameFilter(placement);
        if (access.allowInsert() && subFilter == null)
            return;
        ItemStack draggedItem = event.getOldCursor();
        if (draggedItem == null || draggedItem.getType() == Material.AIR)
            return;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < event.getView().getTopInventory().getSize()) {
                if (!access.allowInsert() || (subFilter != null && !matchesFilter(draggedItem, subFilter)))
                    event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        Placement destinationPlacement = resolvePlacement(event.getDestination());
        if (destinationPlacement != null) {
            SharedNetwork network = networks.get(destinationPlacement.id());
            if (network != null) {
                if (destinationPlacement.isMain() && !network.allowMainInsert()) {
                    event.setCancelled(true);
                    return;
                }
                if (!destinationPlacement.isMain() && network.enableSubFrameFilter()) {
                    ItemStack filter = resolveSubFrameFilter(destinationPlacement);
                    ItemStack moved = event.getItem();
                    if (filter != null && moved != null && moved.getType() != Material.AIR && !matchesFilter(moved, filter)) {
                        event.setCancelled(true);
                        return;
                    }
                }
                if (!destinationPlacement.isMain() && !network.allowSubHopperInsert()) {
                    event.setCancelled(true);
                    return;
                }
                if (destinationPlacement.isMain()) {
                    ItemStack moved = event.getItem() == null ? null : event.getItem().clone();
                    if (moved != null && moved.getType() != Material.AIR) {
                        if (canAcceptItem(event.getDestination(), moved)) {
                            pendingMainContributions.computeIfAbsent(network.id(), id -> new ArrayList<>()).add(moved);
                            scheduleRedistribution(network);
                        } else if (canAcceptSubForHopper(network, moved) && removeFromInventory(event.getSource(), moved)) {
                            event.setCancelled(true);
                            pendingInjectedItems.computeIfAbsent(network.id(), id -> new ArrayList<>()).add(moved);
                            scheduleRedistribution(network);
                        }
                    }
                }
            }
        }
        Placement sourcePlacement = resolvePlacement(event.getSource());
        if (sourcePlacement != null) {
            SharedNetwork network = networks.get(sourcePlacement.id());
            if (network != null) {
                if (sourcePlacement.isMain() && !network.allowMainExtract()) {
                    event.setCancelled(true);
                    return;
                }
                if (!sourcePlacement.isMain() && !network.allowSubHopperExtract())
                    event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event) {
        handleDestroyedContainers(event.blockList().stream().map(Block::getLocation).toList());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockExplode(BlockExplodeEvent event) {
        handleDestroyedContainers(event.blockList().stream().map(Block::getLocation).toList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        if (containsSharedStoragePlacement(event.getBlocks()))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        if (containsSharedStoragePlacement(event.getBlocks()))
            event.setCancelled(true);
    }

    private void openMainMenu(Player player, SharedNetwork network) {
        ChestUI.builder()
                .title("共有ストレージ " + network.id())
                .size(36)
                .addButtonAt(3, "§b共有ストレージ", Material.CHEST,
                        "§7ID: " + network.id() + "\n§7sub数: " + countResolvedSubs(network))
                .addButtonAt(MENU_TOGGLE_SLOT,
                        network.allowSubInsert() ? "§asub直接投入: 許可" : "§csub直接投入: 禁止",
                        network.allowSubInsert() ? Material.LIME_DYE : Material.RED_DYE,
                        "§7クリックで切り替え")
                .addButtonAt(MENU_RESET_SLOT, "§bReset", Material.HOPPER,
                        "§7main / sub 全体を再仕分けします")
                .addButtonAt(MENU_EXTRACT_SLOT,
                        network.allowSubExtract() ? "§asub取出し: 許可" : "§csub取出し: 禁止",
                        network.allowSubExtract() ? Material.LIME_CANDLE : Material.RED_CANDLE,
                        "§7クリックで切り替え")
                .addButtonAt(MENU_MAIN_INSERT_SLOT,
                        network.allowMainInsert() ? "§amain搬入: 許可" : "§cmain搬入: 禁止",
                        network.allowMainInsert() ? Material.HOPPER : Material.BARRIER,
                        "§7ホッパー等から main に入れる")
                .addButtonAt(MENU_MAIN_EXTRACT_SLOT,
                        network.allowMainExtract() ? "§amain搬出: 許可" : "§cmain搬出: 禁止",
                        network.allowMainExtract() ? Material.DROPPER : Material.BARRIER,
                        "§7ホッパー等で main から吸い出す")
                .addButtonAt(MENU_SUB_HOPPER_INSERT_SLOT,
                        network.allowSubHopperInsert() ? "§asub搬入: 許可" : "§csub搬入: 禁止",
                        network.allowSubHopperInsert() ? Material.HOPPER : Material.BARRIER,
                        "§7ホッパー等から sub に入れる")
                .addButtonAt(MENU_SUB_HOPPER_EXTRACT_SLOT,
                        network.allowSubHopperExtract() ? "§asub搬出: 許可" : "§csub搬出: 禁止",
                        network.allowSubHopperExtract() ? Material.DROPPER : Material.BARRIER,
                        "§7ホッパー等で sub から吸い出す")
                .addButtonAt(MENU_PARTICLE_SLOT,
                        network.enableTransferParticles() ? "§aParticle演出: ON" : "§cParticle演出: OFF",
                        network.enableTransferParticles() ? Material.BLAZE_POWDER : Material.GUNPOWDER,
                        "§7搬送ライン演出の表示切替")
                .addButtonAt(MENU_FRAME_FILTER_SLOT,
                        network.enableSubFrameFilter() ? "§a額縁フィルタ: ON" : "§c額縁フィルタ: OFF",
                        network.enableSubFrameFilter() ? Material.ITEM_FRAME : Material.BARRIER,
                        "§7subに額縁のアイテムだけを入れる")
                .addButtonAt(MENU_CLOSE_SLOT, "§7閉じる", Material.BARRIER, "§7UIを閉じます")
                .then((result, p) -> {
                    if (!result.success || result.slot == null)
                        return;
                    if (result.slot == MENU_TOGGLE_SLOT) {
                        network.setAllowSubInsert(!network.allowSubInsert());
                        saveNetworks();
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, network.allowSubInsert() ? 1.2F : 0.8F);
                        openMainMenu(p, network);
                        return;
                    }
                    if (result.slot == MENU_EXTRACT_SLOT) {
                        network.setAllowSubExtract(!network.allowSubExtract());
                        saveNetworks();
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, network.allowSubExtract() ? 1.2F : 0.8F);
                        openMainMenu(p, network);
                        return;
                    }
                    if (result.slot == MENU_MAIN_INSERT_SLOT) {
                        network.setAllowMainInsert(!network.allowMainInsert());
                        saveNetworks();
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, network.allowMainInsert() ? 1.2F : 0.8F);
                        openMainMenu(p, network);
                        return;
                    }
                    if (result.slot == MENU_MAIN_EXTRACT_SLOT) {
                        network.setAllowMainExtract(!network.allowMainExtract());
                        saveNetworks();
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, network.allowMainExtract() ? 1.2F : 0.8F);
                        openMainMenu(p, network);
                        return;
                    }
                    if (result.slot == MENU_SUB_HOPPER_INSERT_SLOT) {
                        network.setAllowSubHopperInsert(!network.allowSubHopperInsert());
                        saveNetworks();
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, network.allowSubHopperInsert() ? 1.2F : 0.8F);
                        openMainMenu(p, network);
                        return;
                    }
                    if (result.slot == MENU_SUB_HOPPER_EXTRACT_SLOT) {
                        network.setAllowSubHopperExtract(!network.allowSubHopperExtract());
                        saveNetworks();
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, network.allowSubHopperExtract() ? 1.2F : 0.8F);
                        openMainMenu(p, network);
                        return;
                    }
                    if (result.slot == MENU_PARTICLE_SLOT) {
                        network.setEnableTransferParticles(!network.enableTransferParticles());
                        saveNetworks();
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, network.enableTransferParticles() ? 1.2F : 0.8F);
                        openMainMenu(p, network);
                        return;
                    }
                    if (result.slot == MENU_FRAME_FILTER_SLOT) {
                        network.setEnableSubFrameFilter(!network.enableSubFrameFilter());
                        saveNetworks();
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, network.enableSubFrameFilter() ? 1.2F : 0.8F);
                        openMainMenu(p, network);
                        return;
                    }
                    if (result.slot == MENU_RESET_SLOT) {
                        redistributeNetwork(network, p, true);
                        openMainMenu(p, network);
                        return;
                    }
                    if (result.slot == MENU_CLOSE_SLOT)
                        ChestUI.closeMenu(p);
                }).show(player);
    }

    private int countResolvedSubs(SharedNetwork network) {
        int count = 0;
        for (Location subAnchor : network.subs()) {
            if (!resolveContainerLocations(subAnchor).isEmpty())
                count++;
        }
        return count;
    }

    private void craftSharedChest(ItemCombineModule.CombineMatch match, String role) {
        if (!toggle.getGlobal(FEATURE_KEY))
            return;
        StorageNameTag tag = getStorageNameTag(match.second().getItemStack(), role);
        if (tag == null)
            return;
        match.consumeMatchedItems(1, 1);
        Location center = match.center();
        center.getWorld().dropItemNaturally(center, createSharedChestItem(tag.id(), role));
        center.getWorld().playSound(center, Sound.BLOCK_ENDER_CHEST_OPEN, 0.8F, ROLE_MAIN.equals(role) ? 1.0F : 1.3F);
    }

    private PlacementConflict findPlacementConflict(List<Location> footprint, StorageItemData itemData) {
        for (Location location : footprint) {
            Placement existing = placements.get(SharedStorageStore.toKey(location));
            if (existing == null)
                continue;
            if (!existing.id().equals(itemData.id()) || !existing.role().equals(itemData.role()))
                return PlacementConflict.OTHER_NETWORK;
        }
        return PlacementConflict.NONE;
    }

    private PlacementResult registerPlacement(StorageItemData itemData, List<Location> footprint) {
        SharedNetwork network = networks.computeIfAbsent(itemData.id(), SharedNetwork::new);
        Location anchor = canonicalAnchor(footprint);

        if (ROLE_MAIN.equals(itemData.role())) {
            if (network.main() != null) {
                List<Location> existing = resolveContainerLocations(network.main());
                if (!overlaps(existing, footprint))
                    return PlacementResult.MAIN_ALREADY_EXISTS;
            }
            network.setMain(anchor);
            reindexAllPlacements();
            return PlacementResult.SUCCESS;
        }

        if (network.main() == null)
            return PlacementResult.MAIN_NOT_FOUND;
        if (containerDistance(resolveContainerLocations(network.main()), footprint) > MAX_SUB_DISTANCE)
            return PlacementResult.TOO_FAR;

        Location matchedSub = findMatchingSub(network, footprint);
        if (matchedSub != null)
            network.removeSub(matchedSub);
        network.addSub(anchor);
        reindexAllPlacements();
        return PlacementResult.SUCCESS;
    }

    private Location findMatchingSub(SharedNetwork network, List<Location> footprint) {
        for (Location subAnchor : network.subs()) {
            if (overlaps(resolveContainerLocations(subAnchor), footprint))
                return subAnchor;
        }
        return null;
    }

    private void loadNetworks() {
        networks.clear();
        for (SharedStorageStore.StoredNetwork stored : store.loadAll().values()) {
            SharedNetwork network = new SharedNetwork(stored.getId());
            network.setMain(stored.getMain());
            network.setAllowSubInsert(stored.isAllowSubInsert());
            network.setAllowSubExtract(stored.isAllowSubExtract());
            network.setAllowSubHopperInsert(stored.isAllowSubHopperInsert());
            network.setAllowSubHopperExtract(stored.isAllowSubHopperExtract());
            network.setAllowMainInsert(stored.isAllowMainInsert());
            network.setAllowMainExtract(stored.isAllowMainExtract());
            network.setEnableTransferParticles(stored.isEnableTransferParticles());
            network.setEnableSubFrameFilter(stored.isEnableSubFrameFilter());
            for (Location sub : stored.getSubs())
                network.addSub(sub);
            networks.put(network.id(), network);
        }
        reindexAllPlacements();
    }

    private void saveNetworks() {
        Map<String, SharedStorageStore.StoredNetwork> data = new LinkedHashMap<>();
        for (SharedNetwork network : networks.values()) {
            data.put(network.id(), new SharedStorageStore.StoredNetwork(
                    network.id(),
                    network.main(),
                    new ArrayList<>(network.subs()),
                    network.allowSubInsert(),
                    network.allowSubExtract(),
                    network.allowSubHopperInsert(),
                    network.allowSubHopperExtract(),
                    network.allowMainInsert(),
                    network.allowMainExtract(),
                    network.enableTransferParticles(),
                    network.enableSubFrameFilter()));
        }
        store.saveAll(data);
    }

    private void reindexAllPlacements() {
        placements.clear();
        List<String> removedNetworks = new ArrayList<>();
        for (SharedNetwork network : networks.values()) {
            if (network.main() != null) {
                List<Location> mainFootprint = resolveContainerLocations(network.main());
                if (mainFootprint.isEmpty()) {
                    network.setMain(null);
                } else {
                    Location canonicalMain = canonicalAnchor(mainFootprint);
                    network.setMain(canonicalMain);
                    indexPlacement(network.id(), ROLE_MAIN, canonicalMain, mainFootprint);
                }
            }

            List<Location> rewrittenSubs = new ArrayList<>();
            for (Location subAnchor : new ArrayList<>(network.subs())) {
                List<Location> subFootprint = resolveContainerLocations(subAnchor);
                if (subFootprint.isEmpty())
                    continue;
                Location canonicalSub = canonicalAnchor(subFootprint);
                if (!containsBlock(rewrittenSubs, canonicalSub))
                    rewrittenSubs.add(canonicalSub);
                indexPlacement(network.id(), ROLE_SUB, canonicalSub, subFootprint);
            }
            network.replaceSubs(rewrittenSubs);
            if (network.main() == null && network.subs().isEmpty())
                removedNetworks.add(network.id());
        }
        for (String removedId : removedNetworks)
            networks.remove(removedId);
    }

    private void indexPlacement(String id, String role, Location anchor, List<Location> footprint) {
        Placement placement = new Placement(id, role, anchor);
        for (Location location : footprint)
            placements.put(SharedStorageStore.toKey(location), placement);
    }

    private void redistributeNetwork(SharedNetwork network, Player actor, boolean announce) {
        redistributeNetwork(network, actor, announce, null, null);
    }

    private void redistributeNetwork(SharedNetwork network, Player actor, boolean announce, List<ItemStack> trackedMainItems) {
        redistributeNetwork(network, actor, announce, trackedMainItems, null);
    }

    private void redistributeNetwork(SharedNetwork network, Player actor, boolean announce, List<ItemStack> trackedMainItems,
                                     List<ItemStack> injectedItems) {
        reindexAllPlacements();
        if (network.main() == null)
            return;
        ResolvedInventory main = resolveInventory(network.main());
        if (main == null)
            return;

        List<ResolvedInventory> subs = new ArrayList<>();
        for (Location subLocation : network.subs()) {
            ResolvedInventory sub = resolveInventory(subLocation);
            if (sub != null)
                subs.add(sub);
        }
        if (subs.isEmpty()) {
            if (announce && actor != null)
                actor.sendMessage("§esubチェストが無いため main のみです");
            return;
        }

        List<TrackedItemStack> items = new ArrayList<>();
        collectItems(items, main.inventory(), true, trackedMainItems);
        for (ResolvedInventory sub : subs)
            collectItems(items, sub.inventory(), false, null);
        if (injectedItems != null) {
            for (ItemStack injected : injectedItems) {
                if (injected == null || injected.getType() == Material.AIR)
                    continue;
                items.add(new TrackedItemStack(injected.clone(), injected.getAmount()));
            }
        }
        items = sortAndMerge(items);

        main.inventory().clear();
        for (ResolvedInventory sub : subs)
            sub.inventory().clear();

        Set<Location> transferredSubs = new LinkedHashSet<>();
        List<TrackedItemStack> remainingItems = new ArrayList<>(items);

        if (network.enableSubFrameFilter()) {
            Map<ResolvedInventory, ItemStack> frameFilters = new LinkedHashMap<>();
            List<ResolvedInventory> filteredSubs = new ArrayList<>();
            List<ResolvedInventory> regularSubs = new ArrayList<>();
            for (ResolvedInventory sub : subs) {
                ItemStack filter = resolveSubFrameFilter(sub);
                if (filter != null) {
                    frameFilters.put(sub, filter);
                    filteredSubs.add(sub);
                } else {
                    regularSubs.add(sub);
                }
            }

            List<TrackedItemStack> filteredItems = new ArrayList<>();
            List<TrackedItemStack> generalItems = new ArrayList<>();
            for (TrackedItemStack tracked : remainingItems) {
                if (matchesAnyFilter(tracked.stack(), frameFilters.values())) {
                    filteredItems.add(tracked);
                } else {
                    generalItems.add(tracked);
                }
            }

            for (ResolvedInventory sub : filteredSubs) {
                ItemStack filter = frameFilters.get(sub);
                fillMatchingInventory(sub.inventory(), filteredItems, filter, transferredSubs, sub.anchor());
            }
            for (ResolvedInventory sub : regularSubs) {
                fillAnyInventory(sub.inventory(), generalItems, transferredSubs, sub.anchor());
            }

            remainingItems = new ArrayList<>();
            remainingItems.addAll(generalItems);
            remainingItems.addAll(filteredItems);
            remainingItems = sortAndMerge(remainingItems);
        } else {
            for (ResolvedInventory sub : subs)
                fillAnyInventory(sub.inventory(), remainingItems, transferredSubs, sub.anchor());
        }

        for (int slot = 0; slot < main.inventory().getSize() && !remainingItems.isEmpty(); slot++) {
            TrackedItemStack tracked = remainingItems.remove(0);
            main.inventory().setItem(slot, tracked.stack());
            if (tracked.mainContribution() > 0)
                transferredSubs.add(main.anchor());
        }

        if (!remainingItems.isEmpty()) {
            for (ResolvedInventory sub : subs) {
                fillAnyIntoEmptySlots(sub.inventory(), remainingItems, transferredSubs, sub.anchor());
                if (remainingItems.isEmpty())
                    break;
            }
        }

        if (network.enableTransferParticles()) {
            for (Location subLocation : transferredSubs) {
                if (subLocation.equals(main.anchor())) {
                    spawnGlowEffect(main.anchor());
                } else {
                    spawnTransferEffect(main.anchor(), subLocation);
                }
            }
        }

        if (announce && actor != null) {
            actor.playSound(actor.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8F, 1.2F);
            actor.sendMessage("§a共有ストレージ " + network.id() + " を再仕分けしました");
        }
    }

    private void handleDestroyedContainers(Collection<Location> destroyedLocations) {
        if (destroyedLocations == null || destroyedLocations.isEmpty())
            return;
        LinkedHashSet<String> destroyedKeys = new LinkedHashSet<>();
        for (Location location : destroyedLocations) {
            if (location != null && location.getWorld() != null)
                destroyedKeys.add(SharedStorageStore.toKey(location));
        }
        if (destroyedKeys.isEmpty())
            return;

        boolean changed = false;
        List<String> removedNetworks = new ArrayList<>();
        for (SharedNetwork network : new ArrayList<>(networks.values())) {
            boolean networkChanged = false;

            if (network.main() != null) {
                List<Location> mainFootprint = resolveContainerLocations(network.main());
                if (intersectsDestroyed(mainFootprint, destroyedKeys)) {
                    List<Location> remainingMain = subtractDestroyed(mainFootprint, destroyedKeys);
                    network.setMain(remainingMain.isEmpty() ? null : canonicalAnchor(remainingMain));
                    networkChanged = true;
                }
            }

            List<Location> rewrittenSubs = new ArrayList<>();
            for (Location subAnchor : network.subs()) {
                List<Location> subFootprint = resolveContainerLocations(subAnchor);
                if (subFootprint.isEmpty()) {
                    networkChanged = true;
                    continue;
                }
                List<Location> remainingSub = intersectsDestroyed(subFootprint, destroyedKeys)
                        ? subtractDestroyed(subFootprint, destroyedKeys)
                        : subFootprint;
                if (!intersectsDestroyed(subFootprint, destroyedKeys) || !remainingSub.isEmpty()) {
                    Location canonicalSub = canonicalAnchor(remainingSub);
                    if (!containsBlock(rewrittenSubs, canonicalSub))
                        rewrittenSubs.add(canonicalSub);
                }
                if (intersectsDestroyed(subFootprint, destroyedKeys))
                    networkChanged = true;
            }

            if (networkChanged) {
                network.replaceSubs(rewrittenSubs);
                changed = true;
            }
            if (network.main() == null && network.subs().isEmpty())
                removedNetworks.add(network.id());
        }

        for (String removedId : removedNetworks)
            networks.remove(removedId);
        if (changed) {
            reindexAllPlacements();
            saveNetworks();
        }
    }

    private boolean intersectsDestroyed(List<Location> footprint, Collection<String> destroyedKeys) {
        for (Location location : footprint) {
            if (location != null && location.getWorld() != null && destroyedKeys.contains(SharedStorageStore.toKey(location)))
                return true;
        }
        return false;
    }

    private List<Location> subtractDestroyed(List<Location> footprint, Collection<String> destroyedKeys) {
        List<Location> remaining = new ArrayList<>();
        for (Location location : footprint) {
            if (location == null || location.getWorld() == null)
                continue;
            if (!destroyedKeys.contains(SharedStorageStore.toKey(location)))
                remaining.add(location);
        }
        return remaining;
    }

    private boolean containsSharedStoragePlacement(List<Block> blocks) {
        for (Block block : blocks) {
            if (resolvePlacement(block.getLocation()) != null)
                return true;
        }
        return false;
    }

    private ResolvedInventory resolveInventory(Location anchor) {
        if (anchor == null || anchor.getWorld() == null)
            return null;
        BlockState state = anchor.getBlock().getState();
        if (!(state instanceof InventoryHolder holder))
            return null;
        Inventory inventory = holder.getInventory();
        List<Location> footprint = resolveContainerLocations(anchor);
        return new ResolvedInventory(canonicalAnchor(footprint.isEmpty() ? List.of(anchor) : footprint), inventory, footprint);
    }

    private List<Location> resolveContainerLocations(Location anchor) {
        if (anchor == null || anchor.getWorld() == null)
            return List.of();
        Block block = anchor.getBlock();
        Material type = block.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST)
            return List.of();
        BlockState state = block.getState();
        if (!(state instanceof Chest chest))
            return List.of(block.getLocation());
        Inventory inventory = chest.getInventory();
        if (inventory instanceof DoubleChestInventory doubleChestInventory && doubleChestInventory.getHolder() != null) {
            LinkedHashSet<Location> locations = new LinkedHashSet<>();
            collectHolderLocation(locations, doubleChestInventory.getLeftSide().getHolder());
            collectHolderLocation(locations, doubleChestInventory.getRightSide().getHolder());
            if (!locations.isEmpty())
                return new ArrayList<>(locations);
        }
        return List.of(block.getLocation());
    }

    private void collectHolderLocation(Set<Location> locations, InventoryHolder holder) {
        if (holder instanceof BlockState state) {
            locations.add(state.getLocation());
            return;
        }
        if (holder instanceof BlockInventoryHolder blockInventoryHolder) {
            locations.add(blockInventoryHolder.getBlock().getLocation());
            return;
        }
        if (holder instanceof DoubleChest doubleChest) {
            collectHolderLocation(locations, doubleChest.getLeftSide(true));
            collectHolderLocation(locations, doubleChest.getRightSide(true));
        }
    }

    private Placement resolvePlacement(Location location) {
        return placements.get(SharedStorageStore.toKey(location));
    }

    private Placement resolvePlacement(Inventory inventory) {
        for (Location location : resolveInventoryHolderLocations(inventory.getHolder())) {
            Placement placement = resolvePlacement(location);
            if (placement != null)
                return placement;
        }
        return null;
    }

    private List<Location> resolveInventoryHolderLocations(InventoryHolder holder) {
        LinkedHashSet<Location> locations = new LinkedHashSet<>();
        collectHolderLocation(locations, holder);
        return new ArrayList<>(locations);
    }

    private void collectItems(List<TrackedItemStack> items, Inventory inventory, boolean fromMain, List<ItemStack> trackedMainItems) {
        List<ItemStack> remainingTrackedItems = trackedMainItems == null ? null : cloneItemStacks(trackedMainItems);
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType() == Material.AIR)
                continue;
            ItemStack clone = item.clone();
            items.add(new TrackedItemStack(clone, fromMain ? consumeTrackedContribution(remainingTrackedItems, clone) : 0));
        }
    }

    private int consumeTrackedContribution(List<ItemStack> trackedItems, ItemStack item) {
        if (trackedItems == null || trackedItems.isEmpty())
            return item.getAmount();
        int remaining = item.getAmount();
        for (int i = 0; i < trackedItems.size() && remaining > 0; ) {
            ItemStack tracked = trackedItems.get(i);
            if (!canMerge(tracked, item)) {
                i++;
                continue;
            }
            int moved = Math.min(tracked.getAmount(), remaining);
            tracked.setAmount(tracked.getAmount() - moved);
            remaining -= moved;
            if (tracked.getAmount() <= 0) {
                trackedItems.remove(i);
            } else {
                i++;
            }
        }
        return item.getAmount() - remaining;
    }

    private List<ItemStack> cloneItemStacks(List<ItemStack> items) {
        List<ItemStack> clones = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR)
                clones.add(item.clone());
        }
        return clones;
    }

    private void fillAnyInventory(Inventory inventory, List<TrackedItemStack> items, Set<Location> transferredSubs, Location subAnchor) {
        for (int slot = 0; slot < inventory.getSize() && !items.isEmpty(); slot++) {
            TrackedItemStack tracked = items.remove(0);
            inventory.setItem(slot, tracked.stack());
            if (tracked.mainContribution() > 0)
                transferredSubs.add(subAnchor);
        }
    }

    private void fillAnyIntoEmptySlots(Inventory inventory, List<TrackedItemStack> items,
                                       Set<Location> transferredSubs, Location subAnchor) {
        for (int slot = 0; slot < inventory.getSize() && !items.isEmpty(); slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing != null && existing.getType() != Material.AIR)
                continue;
            TrackedItemStack tracked = items.remove(0);
            inventory.setItem(slot, tracked.stack());
            if (tracked.mainContribution() > 0)
                transferredSubs.add(subAnchor);
        }
    }

    private void fillMatchingInventory(Inventory inventory, List<TrackedItemStack> items, ItemStack filter,
                                       Set<Location> transferredSubs, Location subAnchor) {
        if (filter == null)
            return;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            TrackedItemStack tracked = removeFirstMatching(items, filter);
            if (tracked == null)
                break;
            inventory.setItem(slot, tracked.stack());
            if (tracked.mainContribution() > 0)
                transferredSubs.add(subAnchor);
        }
    }

    private TrackedItemStack removeFirstMatching(List<TrackedItemStack> items, ItemStack filter) {
        for (int i = 0; i < items.size(); i++) {
            TrackedItemStack tracked = items.get(i);
            if (!matchesFilter(tracked.stack(), filter))
                continue;
            items.remove(i);
            return tracked;
        }
        return null;
    }

    private boolean matchesAnyFilter(ItemStack item, java.util.Collection<ItemStack> filters) {
        for (ItemStack filter : filters) {
            if (matchesFilter(item, filter))
                return true;
        }
        return false;
    }

    private boolean matchesFilter(ItemStack item, ItemStack filter) {
        return item != null && filter != null && item.getType() != Material.AIR && filter.getType() != Material.AIR && canMerge(item, filter);
    }

    private ItemStack resolveSubFrameFilter(ResolvedInventory sub) {
        World world = sub.anchor().getWorld();
        if (world == null)
            return null;
        Location center = sub.anchor().clone().add(0.5D, 0.5D, 0.5D);
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(center, 1.5D, 1.5D, 1.5D)) {
            if (!(entity instanceof ItemFrame frame))
                continue;
            if (!isFrameAttachedToFootprint(frame, sub.footprint()))
                continue;
            ItemStack filter = frame.getItem();
            if (filter != null && filter.getType() != Material.AIR)
                return filter.clone();
        }
        return null;
    }

    private ItemStack resolveSubFrameFilter(Placement placement) {
        if (placement == null || placement.isMain())
            return null;
        SharedNetwork network = networks.get(placement.id());
        if (network == null || !network.enableSubFrameFilter())
            return null;
        ResolvedInventory sub = resolveInventory(placement.anchor());
        return sub == null ? null : resolveSubFrameFilter(sub);
    }

    private boolean isFrameAttachedToFootprint(ItemFrame frame, List<Location> footprint) {
        BlockFace face = frame.getFacing();
        if (face == null)
            return false;
        Block frameBlock = frame.getLocation().getBlock();
        if (containsBlock(footprint, frameBlock.getRelative(face).getLocation()))
            return true;
        return containsBlock(footprint, frameBlock.getRelative(face.getOppositeFace()).getLocation());
    }

    private List<TrackedItemStack> sortAndMerge(List<TrackedItemStack> items) {
        items.sort((a, b) -> {
            ItemStack left = a.stack();
            ItemStack right = b.stack();
            int priorityCompare = Integer.compare(determineCategoryPriority(left.getType()), determineCategoryPriority(right.getType()));
            if (priorityCompare != 0)
                return priorityCompare;
            int materialCompare = left.getType().name().compareTo(right.getType().name());
            if (materialCompare != 0)
                return materialCompare;
            String aName = left.hasItemMeta() && left.getItemMeta().hasDisplayName() ? left.getItemMeta().getDisplayName() : null;
            String bName = right.hasItemMeta() && right.getItemMeta().hasDisplayName() ? right.getItemMeta().getDisplayName() : null;
            if (aName == null && bName != null)
                return 1;
            if (aName != null && bName == null)
                return -1;
            if (aName != null) {
                int nameCompare = aName.compareTo(bName);
                if (nameCompare != 0)
                    return nameCompare;
            }
            int damageCompare = Integer.compare(damage(left), damage(right));
            if (damageCompare != 0)
                return damageCompare;
            return Integer.compare(right.getEnchantments().size(), left.getEnchantments().size());
        });

        List<TrackedItemStack> merged = new ArrayList<>();
        for (TrackedItemStack tracked : items) {
            ItemStack item = tracked.stack().clone();
            int remainingMainContribution = tracked.mainContribution();
            boolean mergedFlag = false;
            for (TrackedItemStack existing : merged) {
                if (!canMerge(existing.stack(), item))
                    continue;
                int space = existing.stack().getMaxStackSize() - existing.stack().getAmount();
                if (space <= 0)
                    continue;
                int moveAmount = Math.min(space, item.getAmount());
                existing.stack().setAmount(existing.stack().getAmount() + moveAmount);
                item.setAmount(item.getAmount() - moveAmount);
                int movedMainContribution = Math.min(moveAmount, remainingMainContribution);
                existing.addMainContribution(movedMainContribution);
                remainingMainContribution -= movedMainContribution;
                if (item.getAmount() <= 0) {
                    mergedFlag = true;
                    break;
                }
            }
            if (!mergedFlag) {
                merged.add(new TrackedItemStack(item.clone(), remainingMainContribution));
            }
        }

        List<TrackedItemStack> result = new ArrayList<>();
        for (TrackedItemStack tracked : merged) {
            if (tracked.stack().getAmount() > 0)
                result.add(tracked);
        }
        return result;
    }

    private static final class TrackedItemStack {
        private final ItemStack stack;
        private int mainContribution;

        private TrackedItemStack(ItemStack stack, int mainContribution) {
            this.stack = stack;
            this.mainContribution = mainContribution;
        }

        private ItemStack stack() {
            return stack;
        }

        private int mainContribution() {
            return mainContribution;
        }

        private void addMainContribution(int amount) {
            this.mainContribution += amount;
        }
    }

    private static final class MainInventorySnapshot {
        private final String networkId;
        private final List<ItemStack> contents;

        private MainInventorySnapshot(String networkId, List<ItemStack> contents) {
            this.networkId = networkId;
            this.contents = contents;
        }

        private String networkId() {
            return networkId;
        }

        private List<ItemStack> contents() {
            return contents;
        }
    }

    private List<ItemStack> snapshotInventory(Inventory inventory) {
        List<ItemStack> snapshot = new ArrayList<>();
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                snapshot.add(null);
            } else {
                snapshot.add(item.clone());
            }
        }
        return snapshot;
    }

    private boolean inventoryMatchesSnapshot(Inventory inventory, List<ItemStack> snapshot) {
        if (snapshot == null)
            return false;
        ItemStack[] contents = inventory.getContents();
        if (contents.length != snapshot.size())
            return false;
        for (int i = 0; i < contents.length; i++) {
            if (!sameItem(contents[i], snapshot.get(i)))
                return false;
        }
        return true;
    }

    private boolean sameItem(ItemStack left, ItemStack right) {
        if (left == null || left.getType() == Material.AIR)
            return right == null || right.getType() == Material.AIR;
        if (right == null || right.getType() == Material.AIR)
            return false;
        return canMerge(left, right) && left.getAmount() == right.getAmount();
    }

    private boolean canAcceptItem(Inventory inventory, ItemStack item) {
        if (inventory == null || item == null || item.getType() == Material.AIR)
            return false;
        int remaining = item.getAmount();
        for (ItemStack slot : inventory.getContents()) {
            if (slot == null || slot.getType() == Material.AIR)
                return true;
            if (!canMerge(slot, item))
                continue;
            int space = slot.getMaxStackSize() - slot.getAmount();
            if (space <= 0)
                continue;
            remaining -= space;
            if (remaining <= 0)
                return true;
        }
        return false;
    }

    private boolean canAcceptSubForHopper(SharedNetwork network, ItemStack item) {
        if (network == null || item == null || item.getType() == Material.AIR)
            return false;
        for (Location subLocation : network.subs()) {
            ResolvedInventory sub = resolveInventory(subLocation);
            if (sub == null)
                continue;
            ItemStack filter = network.enableSubFrameFilter() ? resolveSubFrameFilter(sub) : null;
            if (filter != null && !matchesFilter(item, filter))
                continue;
            if (canAcceptItem(sub.inventory(), item))
                return true;
        }
        return false;
    }

    private boolean removeFromInventory(Inventory inventory, ItemStack item) {
        if (inventory == null || item == null || item.getType() == Material.AIR)
            return false;
        return inventory.removeItem(item).isEmpty();
    }

    private void scheduleRedistribution(SharedNetwork network) {
        String networkId = network.id();
        if (scheduledRedistributions.contains(networkId))
            return;
        scheduledRedistributions.add(networkId);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> {
                    List<ItemStack> trackedItems = pendingMainContributions.remove(networkId);
                    List<ItemStack> injectedItems = pendingInjectedItems.remove(networkId);
                    try {
                        if ((trackedItems == null || trackedItems.isEmpty())
                                && (injectedItems == null || injectedItems.isEmpty())) {
                            redistributeNetwork(network, null, false);
                        } else {
                            redistributeNetwork(network, null, false,
                                    trackedItems == null ? null : cloneItemStacks(trackedItems),
                                    injectedItems == null ? null : cloneItemStacks(injectedItems));
                        }
                    } finally {
                        scheduledRedistributions.remove(networkId);
                    }
                },
                1L);
    }

    private void spawnTransferEffect(Location from, Location to) {
        World world = from.getWorld();
        if (world == null || !Objects.equals(world, to.getWorld()))
            return;
        Location start = from.clone().add(0.5D, 0.7D, 0.5D);
        Location end = to.clone().add(0.5D, 0.7D, 0.5D);
        double dx = end.getX() - start.getX();
        double dy = end.getY() - start.getY();
        double dz = end.getZ() - start.getZ();
        int points = 18;
        for (int i = 0; i <= points; i++) {
            double progress = i / (double) points;
            world.spawnParticle(Particle.END_ROD,
                    start.getX() + dx * progress,
                    start.getY() + dy * progress,
                    start.getZ() + dz * progress,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        world.spawnParticle(Particle.WAX_ON, end, 10, 0.2D, 0.2D, 0.2D, 0.01D);
        world.playSound(start, Sound.BLOCK_ENDER_CHEST_OPEN, 0.5F, 1.5F);
        world.playSound(end, Sound.ENTITY_ITEM_PICKUP, 0.5F, 1.3F);
    }

    private void spawnGlowEffect(Location location) {
        World world = location.getWorld();
        if (world == null)
            return;
        Location center = location.clone().add(0.5D, 0.5D, 0.5D);
        world.spawnParticle(Particle.GLOW, center, 15, 0.3D, 0.3D, 0.3D, 0.1D);
        world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.5F);
    }

    private SubAccess getSubAccess(Inventory topInventory) {
        Placement placement = resolvePlacement(topInventory);
        if (placement == null || placement.isMain())
            return null;
        SharedNetwork network = networks.get(placement.id());
        if (network == null)
            return null;
        return new SubAccess(network.allowSubInsert(), network.allowSubExtract());
    }

    private boolean canUseContainer(Player player, Location location) {
        if (chestShop != null && chestShop.isShopChest(location)) {
            player.sendMessage("§cこのチェストはショップに紐づいているため使えません");
            return false;
        }
        if (chestLock != null && !chestLock.canAccess(player, location)) {
            player.sendMessage("§cこのチェストは保護されているため使えません");
            return false;
        }
        return true;
    }

    private boolean isPlainChestItem(ItemStack stack) {
        return stack != null && stack.getType() == Material.CHEST && !isCustomSharedChest(stack);
    }

    private boolean hasAdjacentChest(Block block) {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Material type = block.getRelative(face).getType();
            if (type == Material.CHEST || type == Material.TRAPPED_CHEST)
                return true;
        }
        return false;
    }

    private boolean isAdjacentToMainChest(Block block) {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Placement placement = resolvePlacement(block.getRelative(face).getLocation());
            if (placement != null && placement.isMain())
                return true;
        }
        return false;
    }

    private boolean isMainNameTag(ItemStack stack) {
        return getStorageNameTag(stack, ROLE_MAIN) != null;
    }

    private boolean isSubNameTag(ItemStack stack) {
        return getStorageNameTag(stack, ROLE_SUB) != null;
    }

    private java.util.Optional<Location> resolveMainLockLocation(Location location) {
        Placement placement = resolvePlacement(location);
        if (placement == null || placement.isMain())
            return java.util.Optional.empty();
        SharedNetwork network = networks.get(placement.id());
        if (network == null || network.main() == null)
            return java.util.Optional.empty();
        return java.util.Optional.of(network.main());
    }

    private Optional<Boolean> resolveChestLockProtection(ChestLockModule.ProtectionContext context) {
        if (context == null || context.location() == null)
            return Optional.empty();
        Placement placement = resolvePlacement(context.location());
        if (placement == null)
            return Optional.empty();
        SharedNetwork network = networks.get(placement.id());
        if (network == null)
            return Optional.empty();
        return switch (context.action()) {
            case INVENTORY_MOVE_SOURCE -> Optional.of(placement.isMain() ? network.allowMainExtract() : network.allowSubHopperExtract());
            case INVENTORY_MOVE_DESTINATION -> Optional.of(placement.isMain() ? network.allowMainInsert() : network.allowSubHopperInsert());
            default -> Optional.empty();
        };
    }

    private StorageNameTag getStorageNameTag(ItemStack stack, String role) {
        if (stack == null || stack.getType() != Material.NAME_TAG || !stack.hasItemMeta())
            return null;
        ItemMeta meta = stack.getItemMeta();
        if (!meta.hasDisplayName())
            return null;
        String raw = meta.getDisplayName();
        if (raw == null)
            return null;
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        String prefix = ROLE_MAIN.equals(role) ? MAIN_PREFIX : SUB_PREFIX;
        if (!normalized.startsWith(prefix))
            return null;
        String id = raw.trim().substring(prefix.length()).trim();
        if (id.isEmpty())
            return null;
        return new StorageNameTag(id);
    }

    private ItemStack createSharedChestItem(String id, String role) {
        ItemStack stack = new ItemStack(Material.CHEST);
        ItemMeta meta = stack.getItemMeta();
        String roleLabel = ROLE_MAIN.equals(role) ? "主" : "sub";
        ComponentUtils.setDisplayName(meta, "§b共有ストレージチェスト §7[" + roleLabel + ":" + id + "]");
        ComponentUtils.setLore(meta,
                "§7ID: " + id,
                ROLE_MAIN.equals(role) ? "§7主チェストとして登録されます" : "§7subチェストとして登録されます",
                "§7同一ID/同一役割ならラージチェスト化できます");
        meta.getPersistentDataContainer().set(roleKey, PersistentDataType.STRING, role);
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isCustomSharedChest(ItemStack stack) {
        return getStorageItemData(stack) != null;
    }

    private StorageItemData getStorageItemData(ItemStack stack) {
        if (stack == null || stack.getType() != Material.CHEST || !stack.hasItemMeta())
            return null;
        ItemMeta meta = stack.getItemMeta();
        String role = meta.getPersistentDataContainer().get(roleKey, PersistentDataType.STRING);
        String id = meta.getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (role == null || id == null)
            return null;
        return new StorageItemData(id, role);
    }

    private static int determineCategoryPriority(Material material) {
        if (material == null)
            return Integer.MAX_VALUE / 2;
        if (material.isBlock())
            return 0;
        if (material.isEdible())
            return 4;
        EquipmentSlot slot = material.getEquipmentSlot();
        if (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET)
            return 2;
        String name = material.name();
        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE") || name.equals("BOW") || name.equals("CROSSBOW") || name.equals("TRIDENT")
                || name.equals("SHEARS") || name.equals("FISHING_ROD"))
            return 3;
        return 1;
    }

    private boolean canMerge(ItemStack first, ItemStack second) {
        if (first == null || second == null)
            return false;
        if (first.getType() != second.getType())
            return false;
        if (damage(first) != damage(second))
            return false;
        if (first.getEnchantments().size() != second.getEnchantments().size())
            return false;
        if (first.hasItemMeta() && second.hasItemMeta()) {
            if (first.getItemMeta().hasDisplayName() || second.getItemMeta().hasDisplayName()) {
                String firstName = first.getItemMeta().hasDisplayName() ? first.getItemMeta().getDisplayName() : null;
                String secondName = second.getItemMeta().hasDisplayName() ? second.getItemMeta().getDisplayName() : null;
                if (!Objects.equals(firstName, secondName))
                    return false;
            }
        }
        return first.getEnchantments().equals(second.getEnchantments());
    }

    private static int damage(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable)
            return damageable.getDamage();
        return 0;
    }

    private boolean overlaps(List<Location> first, List<Location> second) {
        for (Location left : first) {
            for (Location right : second) {
                if (sameBlock(left, right))
                    return true;
            }
        }
        return false;
    }

    private double containerDistance(List<Location> first, List<Location> second) {
        double best = Double.MAX_VALUE;
        for (Location left : first) {
            for (Location right : second) {
                if (!Objects.equals(left.getWorld(), right.getWorld()))
                    continue;
                best = Math.min(best, left.distance(right));
            }
        }
        return best;
    }

    private Location canonicalAnchor(List<Location> locations) {
        List<Location> sorted = new ArrayList<>(locations);
        sorted.sort(Comparator
                .comparing((Location location) -> location.getWorld().getName())
                .thenComparingInt(Location::getBlockX)
                .thenComparingInt(Location::getBlockY)
                .thenComparingInt(Location::getBlockZ));
        return sorted.get(0);
    }

    private boolean containsBlock(List<Location> locations, Location target) {
        for (Location location : locations) {
            if (sameBlock(location, target))
                return true;
        }
        return false;
    }

    private boolean sameBlock(Location first, Location second) {
        return Objects.equals(first.getWorld(), second.getWorld())
                && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private enum PlacementResult {
        SUCCESS,
        MAIN_ALREADY_EXISTS,
        MAIN_NOT_FOUND,
        TOO_FAR;

        private String message(String id) {
            return switch (this) {
                case MAIN_ALREADY_EXISTS -> "§c共有ストレージ " + id + " には既に別の主チェストがあります";
                case MAIN_NOT_FOUND -> "§c共有ストレージ " + id + " の主チェストが見つかりません";
                case TOO_FAR -> "§csubチェストは主チェストから15ブロック以内に設置してください";
                case SUCCESS -> "";
            };
        }
    }

    private enum PlacementConflict {
        NONE,
        OTHER_NETWORK;

        private String message(String id) {
            return switch (this) {
                case OTHER_NETWORK -> "§cこの接続先には別の共有ストレージが存在します";
                case NONE -> "";
            };
        }
    }

    private record StorageNameTag(String id) {
    }

    private record StorageItemData(String id, String role) {
    }

    private record Placement(String id, String role, Location anchor) {
        private boolean isMain() {
            return ROLE_MAIN.equals(role);
        }
    }

    private record ResolvedInventory(Location anchor, Inventory inventory, List<Location> footprint) {
    }

    private static class SharedNetwork {
        private final String id;
        private Location main;
        private final List<Location> subs = new ArrayList<>();
        private boolean allowSubInsert = DEFAULT_ALLOW_SUB_INSERT;
        private boolean allowSubExtract = DEFAULT_ALLOW_SUB_EXTRACT;
        private boolean allowSubHopperInsert = DEFAULT_ALLOW_SUB_HOPPER_INSERT;
        private boolean allowSubHopperExtract = DEFAULT_ALLOW_SUB_HOPPER_EXTRACT;
        private boolean allowMainInsert = DEFAULT_ALLOW_MAIN_INSERT;
        private boolean allowMainExtract = DEFAULT_ALLOW_MAIN_EXTRACT;
        private boolean enableTransferParticles = DEFAULT_ENABLE_TRANSFER_PARTICLES;
        private boolean enableSubFrameFilter = DEFAULT_ENABLE_SUB_FRAME_FILTER;

        private SharedNetwork(String id) {
            this.id = id;
        }

        private String id() {
            return id;
        }

        private Location main() {
            return main;
        }

        private void setMain(Location main) {
            this.main = main;
        }

        private List<Location> subs() {
            return subs;
        }

        private void addSub(Location location) {
            if (!hasSub(location))
                subs.add(location);
        }

        private void removeSub(Location location) {
            subs.removeIf(existing -> Objects.equals(existing.getWorld(), location.getWorld())
                    && existing.getBlockX() == location.getBlockX()
                    && existing.getBlockY() == location.getBlockY()
                    && existing.getBlockZ() == location.getBlockZ());
        }

        private void replaceSubs(List<Location> newSubs) {
            subs.clear();
            subs.addAll(newSubs);
        }

        private boolean hasSub(Location location) {
            for (Location existing : subs) {
                if (Objects.equals(existing.getWorld(), location.getWorld())
                        && existing.getBlockX() == location.getBlockX()
                        && existing.getBlockY() == location.getBlockY()
                        && existing.getBlockZ() == location.getBlockZ())
                    return true;
            }
            return false;
        }

        private boolean allowSubInsert() {
            return allowSubInsert;
        }

        private void setAllowSubInsert(boolean allowSubInsert) {
            this.allowSubInsert = allowSubInsert;
        }

        private boolean allowSubExtract() {
            return allowSubExtract;
        }

        private void setAllowSubExtract(boolean allowSubExtract) {
            this.allowSubExtract = allowSubExtract;
        }

        private boolean allowSubHopperInsert() {
            return allowSubHopperInsert;
        }

        private void setAllowSubHopperInsert(boolean allowSubHopperInsert) {
            this.allowSubHopperInsert = allowSubHopperInsert;
        }

        private boolean allowSubHopperExtract() {
            return allowSubHopperExtract;
        }

        private void setAllowSubHopperExtract(boolean allowSubHopperExtract) {
            this.allowSubHopperExtract = allowSubHopperExtract;
        }

        private boolean allowMainInsert() {
            return allowMainInsert;
        }

        private void setAllowMainInsert(boolean allowMainInsert) {
            this.allowMainInsert = allowMainInsert;
        }

        private boolean allowMainExtract() {
            return allowMainExtract;
        }

        private void setAllowMainExtract(boolean allowMainExtract) {
            this.allowMainExtract = allowMainExtract;
        }

        private boolean enableTransferParticles() {
            return enableTransferParticles;
        }

        private void setEnableTransferParticles(boolean enableTransferParticles) {
            this.enableTransferParticles = enableTransferParticles;
        }

        private boolean enableSubFrameFilter() {
            return enableSubFrameFilter;
        }

        private void setEnableSubFrameFilter(boolean enableSubFrameFilter) {
            this.enableSubFrameFilter = enableSubFrameFilter;
        }
    }

    private record SubAccess(boolean allowInsert, boolean allowExtract) {
    }
}
