package org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.entity.minecart.StorageMinecart;
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
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
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
 * chestsub-id はチェスト付きトロッコとの合成にも対応する。
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
    private static final int DEFAULT_SUB_DISTANCE = 15;
    private static final int MAX_SUB_DISTANCE_LIMIT = 50;
    private static final boolean DEFAULT_ALLOW_SUB_INSERT = false;
    private static final boolean DEFAULT_ALLOW_SUB_EXTRACT = true;
    private static final boolean DEFAULT_ALLOW_SUB_HOPPER_INSERT = false;
    private static final boolean DEFAULT_ALLOW_SUB_HOPPER_EXTRACT = true;
    private static final boolean DEFAULT_ALLOW_MAIN_INSERT = true;
    private static final boolean DEFAULT_ALLOW_MAIN_EXTRACT = true;
    private static final boolean DEFAULT_ENABLE_TRANSFER_PARTICLES = true;
    private static final boolean DEFAULT_ENABLE_SUB_FRAME_FILTER = false;
    private static final boolean DEFAULT_ENABLE_CHEST_PAGE = false;
    private static final String CLEAR_FILTER_NAME = "chest-clear";
    private static final int MENU_TOGGLE_SLOT = 10;
    private static final int MENU_EXTRACT_SLOT = 11;
    private static final int MENU_SUB_HOPPER_INSERT_SLOT = 12;
    private static final int MENU_SUB_HOPPER_EXTRACT_SLOT = 13;
    private static final int MENU_RESET_SLOT = 8;
    private static final int MENU_MAIN_INSERT_SLOT = 19;
    private static final int MENU_MAIN_EXTRACT_SLOT = 20;
    private static final int MENU_CHEST_PAGE_SLOT = 22;
    private static final int MENU_FRAME_FILTER_SLOT = 23;
    private static final int MENU_FRAME_FILTER_MODE_SLOT = 24;
    private static final int MENU_PARTICLE_SLOT = 29;
    private static final int MENU_CLOSE_SLOT = 35;
    private static final int MENU_RANGE_SLOT = 5;

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
        itemCombineModule.recipe("shared_storage_sub_minecart")
                .first(this::isPlainChestMinecartItem)
                .second(this::isSubNameTag)
                .groundRadius(0.5D)
                .airRadius(1.5D)
                .verticalRadius(2.0D)
                .allowAirCombine(true)
                .then(this::craftSharedSubMinecart);
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

        if (event.getAction().isLeftClick() && network.enableChestPage()) {
            event.setCancelled(true);
            openChestPageCategoryMenu(event.getPlayer(), network, 0);
            return;
        }

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
        List<ItemStack> subFilters = resolveSubFrameFilters(placement);
        SubFrameFilterMode filterMode = resolveSubFrameFilterMode(placement);
        boolean hasSubFilters = !subFilters.isEmpty();
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
            if (!access.allowInsert() || (hasSubFilters && current != null && current.getType() != Material.AIR && !matchesAnyFilter(current, subFilters, filterMode)))
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
        if (hasSubFilters && insertItem != null && insertItem.getType() != Material.AIR && !matchesAnyFilter(insertItem, subFilters, filterMode))
            event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        SubAccess access = getSubAccess(event.getView().getTopInventory());
        if (access == null)
            return;
        Placement placement = resolvePlacement(event.getView().getTopInventory());
        List<ItemStack> subFilters = resolveSubFrameFilters(placement);
        SubFrameFilterMode filterMode = resolveSubFrameFilterMode(placement);
        if (access.allowInsert() && subFilters.isEmpty())
            return;
        ItemStack draggedItem = event.getOldCursor();
        if (draggedItem == null || draggedItem.getType() == Material.AIR)
            return;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < event.getView().getTopInventory().getSize()) {
                if (!access.allowInsert() || (!subFilters.isEmpty() && !matchesAnyFilter(draggedItem, subFilters, filterMode)))
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
                    List<ItemStack> filters = resolveSubFrameFilters(destinationPlacement);
                    ItemStack moved = event.getItem();
                    if (!filters.isEmpty() && moved != null && moved.getType() != Material.AIR && !matchesAnyFilter(moved, filters, network.subFrameFilterMode())) {
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
                .addButtonAt(4, "§b共有ストレージ", Material.CHEST,
                        "§7ID: " + network.id() + "\n§7sub数: " + countResolvedSubs(network))
                .addButtonAt(MENU_RANGE_SLOT, "§esub接続範囲", Material.SPYGLASS,
                        "§7現在: " + network.subRange() + "ブロック\n§7設定可能: 1 〜 " + MAX_SUB_DISTANCE_LIMIT)
                .addButtonAt(0, "§6sub設定", Material.BOOK, "§7直接操作 / ホッパー設定")
                .addButtonAt(18, "§6main設定", Material.CHEST, "§7ホッパー搬入 / 搬出設定")
                .addButtonAt(21, "§6額縁フィルタ", Material.ITEM_FRAME, "§7ON/OFF と一致ルール")
                .addButtonAt(MENU_CHEST_PAGE_SLOT,
                        network.enableChestPage() ? "§aChestPage: ON" : "§cChestPage: OFF",
                        network.enableChestPage() ? Material.BOOKSHELF : Material.BARRIER,
                        "§7スニーク左クリックでsub一覧UIを開く")
                .addButtonAt(27, "§6操作 / 演出", Material.BELL, "§7再仕分け / 演出 / 終了")
                .addButtonAt(MENU_TOGGLE_SLOT,
                        network.allowSubInsert() ? "§asub直接投入: 許可" : "§csub直接投入: 禁止",
                        network.allowSubInsert() ? Material.LIME_DYE : Material.RED_DYE,
                        "§7プレイヤー操作で sub に入れる")
                .addButtonAt(MENU_EXTRACT_SLOT,
                        network.allowSubExtract() ? "§asub取出し: 許可" : "§csub取出し: 禁止",
                        network.allowSubExtract() ? Material.LIME_CANDLE : Material.RED_CANDLE,
                        "§7プレイヤー操作で sub から出せる")
                .addButtonAt(MENU_SUB_HOPPER_INSERT_SLOT,
                        network.allowSubHopperInsert() ? "§asubホッパー搬入: 許可" : "§csubホッパー搬入: 禁止",
                        network.allowSubHopperInsert() ? Material.HOPPER : Material.BARRIER,
                        "§7ホッパー等から sub に入れる")
                .addButtonAt(MENU_SUB_HOPPER_EXTRACT_SLOT,
                        network.allowSubHopperExtract() ? "§asubホッパー搬出: 許可" : "§csubホッパー搬出: 禁止",
                        network.allowSubHopperExtract() ? Material.DROPPER : Material.BARRIER,
                        "§7ホッパー等で sub から吸い出す")
                .addButtonAt(MENU_MAIN_INSERT_SLOT,
                        network.allowMainInsert() ? "§amainホッパー搬入: 許可" : "§cmainホッパー搬入: 禁止",
                        network.allowMainInsert() ? Material.HOPPER : Material.BARRIER,
                        "§7ホッパー等から main に入れる")
                .addButtonAt(MENU_MAIN_EXTRACT_SLOT,
                        network.allowMainExtract() ? "§amainホッパー搬出: 許可" : "§cmainホッパー搬出: 禁止",
                        network.allowMainExtract() ? Material.DROPPER : Material.BARRIER,
                        "§7ホッパー等で main から吸い出す")
                .addButtonAt(MENU_FRAME_FILTER_SLOT,
                        network.enableSubFrameFilter() ? "§a額縁フィルタ: ON" : "§c額縁フィルタ: OFF",
                        network.enableSubFrameFilter() ? Material.ITEM_FRAME : Material.BARRIER,
                        "§7subに額縁のアイテムだけを入れる")
                .addButtonAt(MENU_FRAME_FILTER_MODE_SLOT,
                        "§b額縁一致モード: " + network.subFrameFilterMode().label(),
                        network.enableSubFrameFilter() ? Material.COMPARATOR : Material.GRAY_DYE,
                        "§7クリックで切り替え\n§7EXACT / MATERIAL / ENCHANT_STATE")
                .addButtonAt(MENU_PARTICLE_SLOT,
                        network.enableTransferParticles() ? "§aParticle演出: ON" : "§cParticle演出: OFF",
                        network.enableTransferParticles() ? Material.BLAZE_POWDER : Material.GUNPOWDER,
                        "§7搬送ライン演出の表示切替")
                .addButtonAt(MENU_RESET_SLOT, "§b再仕分け実行", Material.HOPPER,
                        "§7main / sub 全体を再仕分けします")
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
                    if (result.slot == MENU_CHEST_PAGE_SLOT) {
                        network.setEnableChestPage(!network.enableChestPage());
                        saveNetworks();
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, network.enableChestPage() ? 1.2F : 0.8F);
                        openMainMenu(p, network);
                        return;
                    }
                    if (result.slot == MENU_FRAME_FILTER_MODE_SLOT) {
                        network.setSubFrameFilterMode(network.subFrameFilterMode().next());
                        saveNetworks();
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.1F);
                        openMainMenu(p, network);
                        return;
                    }
                    if (result.slot == MENU_RESET_SLOT) {
                        redistributeNetwork(network, p, true);
                        openMainMenu(p, network);
                        return;
                    }
                    if (result.slot == MENU_RANGE_SLOT) {
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.2F);
                        openSubRangeMenu(p, network);
                        return;
                    }
                    if (result.slot == MENU_CLOSE_SLOT)
                        ChestUI.closeMenu(p);
                }).show(player);
    }

    private void openSubRangeMenu(Player player, SharedNetwork network) {
        String status = "§7現在値: §f" + network.subRange() + " / " + MAX_SUB_DISTANCE_LIMIT;
        ChestUI.builder()
                .title("sub接続範囲設定: " + network.id())
                .size(27)
                .addButtonAt(10, "§c-10", Material.RED_STAINED_GLASS_PANE, status)
                .addButtonAt(11, "§c-5", Material.ORANGE_STAINED_GLASS_PANE, status)
                .addButtonAt(12, "§c-1", Material.PINK_STAINED_GLASS_PANE, status)
                .addButtonAt(13, "§6現在値", Material.SPYGLASS, status)
                .addButtonAt(14, "§a+1", Material.LIME_STAINED_GLASS_PANE, status)
                .addButtonAt(15, "§a+5", Material.GREEN_STAINED_GLASS_PANE, status)
                .addButtonAt(16, "§a+10", Material.EMERALD_BLOCK, status)
                .addButtonAt(18, "§b10に設定", Material.WHEAT_SEEDS, status)
                .addButtonAt(19, "§b15に設定", Material.CARROT, status)
                .addButtonAt(20, "§b20に設定", Material.POTATO, status)
                .addButtonAt(21, "§b30に設定", Material.BEETROOT_SEEDS, status)
                .addButtonAt(22, "§b50に設定", Material.NETHER_WART, status)
                .addButtonAt(23, "§b最大値に設定", Material.COMPASS, status)
                .addButtonAt(26, "§e戻る", Material.ARROW, "")
                .then((result, p) -> {
                    if (!result.success || result.slot == null)
                        return;
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.2F);
                    int value = network.subRange();
                    switch (result.slot) {
                        case 10 -> value -= 10;
                        case 11 -> value -= 5;
                        case 12 -> value -= 1;
                        case 14 -> value += 1;
                        case 15 -> value += 5;
                        case 16 -> value += 10;
                        case 18 -> value = 10;
                        case 19 -> value = 15;
                        case 20 -> value = 20;
                        case 21 -> value = 30;
                        case 22, 23 -> value = MAX_SUB_DISTANCE_LIMIT;
                        case 26 -> {
                            openMainMenu(p, network);
                            return;
                        }
                        default -> {
                            openSubRangeMenu(p, network);
                            return;
                        }
                    }
                    network.setSubRange(value);
                    saveNetworks();
                    openSubRangeMenu(p, network);
                })
                .show(player);
    }

    private void openChestPageCategoryMenu(Player player, SharedNetwork network, int page) {
        List<SubCategoryEntry> categories = resolveSubCategoryEntries(network);
        if (categories.isEmpty()) {
            player.sendMessage("§esubコンテナが見つかりません");
            return;
        }
        int pageSize = 45;
        int maxPage = Math.max(0, (categories.size() - 1) / pageSize);
        int currentPage = Math.max(0, Math.min(maxPage, page));
        int start = currentPage * pageSize;
        ChestUI.Builder builder = ChestUI.builder()
                .title("ChestPage: " + network.id() + " §7(" + (currentPage + 1) + "/" + (maxPage + 1) + ")")
                .size(54);
        for (int slot = 0; slot < pageSize; slot++) {
            int index = start + slot;
            if (index >= categories.size())
                break;
            SubCategoryEntry category = categories.get(index);
            int subCount = category.subs().size();
            String lore = "§7カテゴリ内sub: §f" + subCount
                    + "\n§7カテゴリ合計: §f" + formatAmount(category.totalAmount()) + "個"
                    + "\n§7クリック: " + (subCount == 1 ? "直接開く" : "カテゴリを開く");
            builder.addCustomItemAt(slot, category.displayName(), category.icon(), lore);
        }
        if (currentPage > 0)
            builder.addButtonAt(45, "§e前のページ", Material.ARROW, "§7カテゴリ一覧を戻る");
        builder.addButtonAt(49, "§c戻る", Material.BARRIER, "§7共有ストレージ設定へ戻る");
        if (currentPage < maxPage)
            builder.addButtonAt(53, "§e次のページ", Material.ARROW, "§7カテゴリ一覧を進む");
        builder.then((result, p) -> {
            if (!result.success || result.slot == null)
                return;
            if (result.slot == 45) {
                openChestPageCategoryMenu(p, network, currentPage - 1);
                return;
            }
            if (result.slot == 49) {
                openMainMenu(p, network);
                return;
            }
            if (result.slot == 53) {
                openChestPageCategoryMenu(p, network, currentPage + 1);
                return;
            }
            if (result.slot < 0 || result.slot >= pageSize)
                return;
            int index = start + result.slot;
            if (index < 0 || index >= categories.size())
                return;
            SubCategoryEntry category = categories.get(index);
            if (category.subs().size() == 1) {
                ResolvedInventory sub = category.subs().get(0);
                if (!canUseSubInventory(p, sub))
                    return;
                p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7F, 1.1F);
                p.openInventory(sub.inventory());
                return;
            }
            openChestPageSubMenu(p, network, category.key(), currentPage, 0);
        }).show(player);
    }

    private void openChestPageSubMenu(Player player, SharedNetwork network, String categoryKey, int categoryPage, int subPage) {
        List<SubCategoryEntry> categories = resolveSubCategoryEntries(network);
        SubCategoryEntry category = null;
        for (SubCategoryEntry entry : categories) {
            if (entry.key().equals(categoryKey)) {
                category = entry;
                break;
            }
        }
        if (category == null || category.subs().isEmpty()) {
            openChestPageCategoryMenu(player, network, categoryPage);
            return;
        }
        List<ResolvedInventory> subs = category.subs();
        int pageSize = 45;
        int maxPage = Math.max(0, (subs.size() - 1) / pageSize);
        int currentPage = Math.max(0, Math.min(maxPage, subPage));
        int start = currentPage * pageSize;
        ChestUI.Builder builder = ChestUI.builder()
                .title("ChestPage: " + ChatColor.stripColor(category.displayName()) + " §7(" + (currentPage + 1) + "/" + (maxPage + 1) + ")")
                .size(54);
        for (int slot = 0; slot < pageSize; slot++) {
            int index = start + slot;
            if (index >= subs.size())
                break;
            ResolvedInventory sub = subs.get(index);
            ItemStack icon = createSubEntryIcon(category, sub);
            String label = category.noFilter()
                    ? "§bsub #" + (index + 1)
                    : category.displayName() + " §7#" + (index + 1);
            int subAmount = countCategoryAmountInInventory(category.key(), sub.inventory());
            String lore = "§7位置: " + formatLocation(sub.anchor())
                    + "\n§7このチェスト内: §f" + formatAmount(subAmount) + "個"
                    + "\n§7カテゴリ合計: §f" + formatAmount(category.totalAmount()) + "個"
                    + "\n§7クリックで開く";
            builder.addCustomItemAt(slot, label, icon, lore);
        }
        if (currentPage > 0)
            builder.addButtonAt(45, "§e前のページ", Material.ARROW, "§7sub一覧を戻る");
        builder.addButtonAt(49, "§c戻る", Material.BARRIER, "§7カテゴリ一覧へ戻る");
        if (currentPage < maxPage)
            builder.addButtonAt(53, "§e次のページ", Material.ARROW, "§7sub一覧を進む");
        builder.then((result, p) -> {
            if (!result.success || result.slot == null)
                return;
            if (result.slot == 45) {
                openChestPageSubMenu(p, network, categoryKey, categoryPage, currentPage - 1);
                return;
            }
            if (result.slot == 49) {
                openChestPageCategoryMenu(p, network, categoryPage);
                return;
            }
            if (result.slot == 53) {
                openChestPageSubMenu(p, network, categoryKey, categoryPage, currentPage + 1);
                return;
            }
            if (result.slot < 0 || result.slot >= pageSize)
                return;
            int index = start + result.slot;
            if (index < 0 || index >= subs.size())
                return;
            ResolvedInventory sub = subs.get(index);
            if (!canUseSubInventory(p, sub))
                return;
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7F, 1.1F);
            p.openInventory(sub.inventory());
        }).show(player);
    }

    private List<SubCategoryEntry> resolveSubCategoryEntries(SharedNetwork network) {
        List<ResolvedInventory> resolvedSubs = resolveAllSubInventories(network);
        if (resolvedSubs.isEmpty())
            return List.of();
        Map<String, MutableSubCategory> categories = new LinkedHashMap<>();
        for (ResolvedInventory sub : resolvedSubs) {
            List<ItemStack> filters = resolveSubFrameFilters(sub);
            if (filters.isEmpty()) {
                String key = resolveCategoryKey(null);
                MutableSubCategory category = categories.get(key);
                if (category == null) {
                    category = new MutableSubCategory(key, null);
                    categories.put(key, category);
                }
                category.subs.add(sub);
                continue;
            }
            Set<String> usedKeys = new LinkedHashSet<>();
            for (ItemStack filter : filters) {
                String key = resolveCategoryKey(filter);
                if (!usedKeys.add(key))
                    continue;
                MutableSubCategory category = categories.get(key);
                if (category == null) {
                    category = new MutableSubCategory(key, filter);
                    categories.put(key, category);
                }
                category.subs.add(sub);
            }
        }
        List<SubCategoryEntry> entries = new ArrayList<>();
        for (MutableSubCategory category : categories.values()) {
            int totalAmount = 0;
            for (ResolvedInventory sub : category.subs) {
                totalAmount += countCategoryAmountInInventory(category.key, sub.inventory());
            }
            entries.add(category.toEntry(totalAmount));
        }
        entries.sort((left, right) -> {
            if (left.noFilter() != right.noFilter())
                return left.noFilter() ? 1 : -1;
            int priority = Integer.compare(determineCategoryPriority(left.icon().getType()), determineCategoryPriority(right.icon().getType()));
            if (priority != 0)
                return priority;
            int material = left.icon().getType().name().compareTo(right.icon().getType().name());
            if (material != 0)
                return material;
            return ChatColor.stripColor(left.displayName()).compareToIgnoreCase(ChatColor.stripColor(right.displayName()));
        });
        return entries;
    }

    private List<ResolvedInventory> resolveAllSubInventories(SharedNetwork network) {
        List<ResolvedInventory> resolved = new ArrayList<>();
        for (Location subLocation : network.subs()) {
            ResolvedInventory sub = resolveInventory(subLocation);
            if (sub != null)
                resolved.add(sub);
        }
        resolved.addAll(resolveMinecartSubs(network));
        return resolved;
    }

    private String resolveCategoryKey(ItemStack filter) {
        if (filter == null || filter.getType() == Material.AIR)
            return "nofilter";
        if (isClearFrameFilter(filter))
            return "clear:" + filter.getType().name();
        return "filter:" + filter.getType().name();
    }

    private ItemStack createSubEntryIcon(SubCategoryEntry category, ResolvedInventory sub) {
        ItemStack icon = category.icon().clone();
        icon.setAmount(1);
        if (sub.inventory().getHolder() instanceof StorageMinecart) {
            icon = icon.getType() == Material.AIR ? new ItemStack(Material.CHEST_MINECART) : icon;
        }
        return icon;
    }

    private int countCategoryAmountInInventory(String categoryKey, Inventory inventory) {
        if (inventory == null)
            return 0;
        Material material = resolveCategoryMaterial(categoryKey);
        int total = 0;
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType() == Material.AIR)
                continue;
            if (material != null && item.getType() != material)
                continue;
            total += item.getAmount();
        }
        return total;
    }

    private Material resolveCategoryMaterial(String categoryKey) {
        if (categoryKey == null || categoryKey.equals("nofilter"))
            return null;
        int separator = categoryKey.indexOf(':');
        if (separator < 0 || separator >= categoryKey.length() - 1)
            return null;
        String materialName = categoryKey.substring(separator + 1);
        try {
            return Material.valueOf(materialName);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String formatAmount(int amount) {
        return String.format(Locale.US, "%,d", amount);
    }

    private boolean canUseSubInventory(Player player, ResolvedInventory sub) {
        if (sub == null || sub.inventory() == null)
            return false;
        if (sub.inventory().getHolder() instanceof StorageMinecart)
            return true;
        return canUseContainer(player, sub.anchor());
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null)
            return "unknown";
        return location.getWorld().getName() + " "
                + location.getBlockX() + ","
                + location.getBlockY() + ","
                + location.getBlockZ();
    }

    private String resolveItemDisplayName(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return "UNKNOWN";
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String displayName = ChatColor.stripColor(meta.getDisplayName());
                if (displayName != null && !displayName.isBlank())
                    return displayName;
            }
        }
        String i18nName = item.getI18NDisplayName();
        if (i18nName != null && !i18nName.isBlank())
            return i18nName;
        return formatMaterialEnumName(item.getType());
    }

    private String formatMaterialEnumName(Material material) {
        if (material == null)
            return "UNKNOWN";
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty())
                continue;
            if (!builder.isEmpty())
                builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1)
                builder.append(part.substring(1));
        }
        return builder.isEmpty() ? material.name() : builder.toString();
    }

    private int countResolvedSubs(SharedNetwork network) {
        Set<String> keys = new LinkedHashSet<>();
        for (Location subAnchor : network.subs()) {
            if (!resolveContainerLocations(subAnchor).isEmpty())
                keys.add(SharedStorageStore.toKey(subAnchor));
        }
        for (ResolvedInventory sub : resolveMinecartSubs(network)) {
            keys.add("minecart:" + SharedStorageStore.toKey(sub.anchor()));
        }
        return keys.size();
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

    private void craftSharedSubMinecart(ItemCombineModule.CombineMatch match) {
        if (!toggle.getGlobal(FEATURE_KEY))
            return;
        StorageNameTag tag = getStorageNameTag(match.second().getItemStack(), ROLE_SUB);
        if (tag == null)
            return;
        match.consumeMatchedItems(1, 1);
        Location center = match.center();
        center.getWorld().dropItemNaturally(center, createSharedSubMinecartItem(tag.id()));
        center.getWorld().playSound(center, Sound.BLOCK_ENDER_CHEST_OPEN, 0.8F, 1.3F);
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
        if (containerDistance(resolveContainerLocations(network.main()), footprint) > network.subRange())
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
            network.setSubRange(stored.getSubRange());
            network.setEnableSubFrameFilter(stored.isEnableSubFrameFilter());
            network.setSubFrameFilterMode(SubFrameFilterMode.fromStored(stored.getSubFrameFilterMode()));
            network.setEnableChestPage(stored.isEnableChestPage());
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
                    network.subRange(),
                    network.enableSubFrameFilter(),
                    network.subFrameFilterMode().name(),
                    network.enableChestPage()));
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
        subs.addAll(resolveMinecartSubs(network));
        if (subs.isEmpty()) {
            if (announce && actor != null)
                actor.sendMessage("§esubコンテナが無いため main のみです");
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

        int disposedAmount = 0;
        if (network.enableSubFrameFilter()) {
            Map<ResolvedInventory, List<ItemStack>> frameFilters = new LinkedHashMap<>();
            List<ItemStack> allFrameFilters = new ArrayList<>();
            List<ItemStack> allClearFilters = new ArrayList<>();
            List<ResolvedInventory> filteredSubs = new ArrayList<>();
            List<ResolvedInventory> regularSubs = new ArrayList<>();
            for (ResolvedInventory sub : subs) {
                List<ItemStack> filters = resolveSubFrameFilters(sub);
                if (!filters.isEmpty()) {
                    List<ItemStack> positiveFilters = new ArrayList<>();
                    for (ItemStack filter : filters) {
                        if (isClearFrameFilter(filter)) {
                            allClearFilters.add(filter);
                        } else {
                            positiveFilters.add(filter);
                        }
                    }
                    if (!positiveFilters.isEmpty()) {
                        frameFilters.put(sub, positiveFilters);
                        allFrameFilters.addAll(positiveFilters);
                        filteredSubs.add(sub);
                    }
                } else {
                    regularSubs.add(sub);
                }
            }

            List<TrackedItemStack> filteredItems = new ArrayList<>();
            List<TrackedItemStack> generalItems = new ArrayList<>();
            for (TrackedItemStack tracked : remainingItems) {
                if (matchesAnyFilter(tracked.stack(), allClearFilters, network.subFrameFilterMode())) {
                    disposedAmount += tracked.stack().getAmount();
                } else if (matchesAnyFilter(tracked.stack(), allFrameFilters, network.subFrameFilterMode())) {
                    filteredItems.add(tracked);
                } else {
                    generalItems.add(tracked);
                }
            }

            for (ResolvedInventory sub : filteredSubs) {
                List<ItemStack> filters = frameFilters.get(sub);
                fillMatchingInventory(sub.inventory(), filteredItems, filters, network.subFrameFilterMode(), transferredSubs, sub.anchor());
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
            if (disposedAmount > 0) {
                actor.sendMessage("§a共有ストレージ " + network.id() + " を再仕分けしました §7(処分: " + disposedAmount + "個)");
            } else {
                actor.sendMessage("§a共有ストレージ " + network.id() + " を再仕分けしました");
            }
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

    public boolean isSharedStorageContainer(Location location) {
        if (location == null) {
            return false;
        }
        return resolvePlacement(location) != null;
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

    private void fillMatchingInventory(Inventory inventory, List<TrackedItemStack> items, List<ItemStack> filters, SubFrameFilterMode mode,
                                       Set<Location> transferredSubs, Location subAnchor) {
        if (filters == null || filters.isEmpty())
            return;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            TrackedItemStack tracked = removeFirstMatching(items, filters, mode);
            if (tracked == null)
                break;
            inventory.setItem(slot, tracked.stack());
            if (tracked.mainContribution() > 0)
                transferredSubs.add(subAnchor);
        }
    }

    private TrackedItemStack removeFirstMatching(List<TrackedItemStack> items, List<ItemStack> filters, SubFrameFilterMode mode) {
        for (int i = 0; i < items.size(); i++) {
            TrackedItemStack tracked = items.get(i);
            if (!matchesAnyFilter(tracked.stack(), filters, mode))
                continue;
            items.remove(i);
            return tracked;
        }
        return null;
    }

    private boolean matchesAnyFilter(ItemStack item, java.util.Collection<ItemStack> filters, SubFrameFilterMode mode) {
        if (filters == null || filters.isEmpty())
            return false;
        for (ItemStack filter : filters) {
            if (matchesFilter(item, filter, mode))
                return true;
        }
        return false;
    }

    private boolean matchesFilter(ItemStack item, ItemStack filter, SubFrameFilterMode mode) {
        if (item == null || filter == null || item.getType() == Material.AIR || filter.getType() == Material.AIR)
            return false;
        if (isClearFrameFilter(filter))
            return matchesClearFilter(item, filter, mode);
        return switch (mode) {
            case MATERIAL -> item.getType() == filter.getType();
            case ENCHANT_STATE -> item.getType() == filter.getType()
                    && hasEnchantState(item) == hasEnchantState(filter);
            case EXACT -> canMerge(item, filter);
        };
    }

    private boolean matchesClearFilter(ItemStack item, ItemStack filter, SubFrameFilterMode mode) {
        if (item.getType() != filter.getType())
            return false;
        if (mode == SubFrameFilterMode.ENCHANT_STATE)
            return hasEnchantState(item) == hasEnchantState(filter);
        return true;
    }

    private boolean isClearFrameFilter(ItemStack filter) {
        if (filter == null || filter.getType() == Material.AIR || !filter.hasItemMeta())
            return false;
        ItemMeta meta = filter.getItemMeta();
        if (meta == null || !meta.hasDisplayName())
            return false;
        String raw = meta.getDisplayName();
        if (raw == null)
            return false;
        String normalized = ChatColor.stripColor(raw).trim().toLowerCase(Locale.ROOT);
        return CLEAR_FILTER_NAME.equals(normalized);
    }

    private boolean hasEnchantState(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return false;
        if (!item.getEnchantments().isEmpty())
            return true;
        if (!item.hasItemMeta())
            return false;
        ItemMeta meta = item.getItemMeta();
        if (meta.hasEnchants())
            return true;
        if (meta instanceof EnchantmentStorageMeta storageMeta && storageMeta.hasStoredEnchants())
            return true;
        return meta.hasEnchantmentGlintOverride() && Boolean.TRUE.equals(meta.getEnchantmentGlintOverride());
    }

    private SubFrameFilterMode resolveSubFrameFilterMode(Placement placement) {
        if (placement == null || placement.isMain())
            return SubFrameFilterMode.EXACT;
        SharedNetwork network = networks.get(placement.id());
        if (network == null)
            return SubFrameFilterMode.EXACT;
        return network.subFrameFilterMode();
    }

    private List<ItemStack> resolveSubFrameFilters(ResolvedInventory sub) {
        List<ItemStack> filters = new ArrayList<>();
        if (sub == null || sub.anchor() == null)
            return filters;
        World world = sub.anchor().getWorld();
        if (world == null)
            return filters;
        Location center = sub.anchor().clone().add(0.5D, 0.5D, 0.5D);
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(center, 1.5D, 1.5D, 1.5D)) {
            if (!(entity instanceof ItemFrame frame))
                continue;
            if (!isFrameAttachedToFootprint(frame, sub.footprint()))
                continue;
            ItemStack filter = frame.getItem();
            if (filter != null && filter.getType() != Material.AIR)
                filters.add(filter.clone());
        }
        return filters;
    }

    private List<ItemStack> resolveSubFrameFilters(Placement placement) {
        if (placement == null || placement.isMain())
            return List.of();
        SharedNetwork network = networks.get(placement.id());
        if (network == null || !network.enableSubFrameFilter())
            return List.of();
        ResolvedInventory sub = resolveInventory(placement.anchor());
        return sub == null ? List.of() : resolveSubFrameFilters(sub);
    }

    private boolean isFrameAttachedToFootprint(ItemFrame frame, List<Location> footprint) {
        BlockFace attachedFace = frame.getAttachedFace();
        if (attachedFace == null)
            return false;
        Block frameBlock = frame.getLocation().getBlock();
        return containsBlock(footprint, frameBlock.getRelative(attachedFace).getLocation());
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
        List<ResolvedInventory> subs = new ArrayList<>();
        for (Location subLocation : network.subs()) {
            ResolvedInventory sub = resolveInventory(subLocation);
            if (sub != null)
                subs.add(sub);
        }
        subs.addAll(resolveMinecartSubs(network));
        for (ResolvedInventory sub : subs) {
            List<ItemStack> filters = network.enableSubFrameFilter() ? resolveSubFrameFilters(sub) : List.of();
            if (!filters.isEmpty() && !matchesAnyFilter(item, filters, network.subFrameFilterMode()))
                continue;
            if (canAcceptItem(sub.inventory(), item))
                return true;
        }
        return false;
    }

    private List<ResolvedInventory> resolveMinecartSubs(SharedNetwork network) {
        if (network == null || network.main() == null || network.main().getWorld() == null)
            return List.of();
        World world = network.main().getWorld();
        List<ResolvedInventory> resolved = new ArrayList<>();
        Set<UUID> used = new LinkedHashSet<>();
        for (StorageMinecart minecart : world.getEntitiesByClass(StorageMinecart.class)) {
            if (minecart == null || !minecart.isValid() || !used.add(minecart.getUniqueId()))
                continue;
            String minecartId = resolveMinecartSubId(minecart);
            if (minecartId == null || !minecartId.equalsIgnoreCase(network.id()))
                continue;
            Location cartLocation = minecart.getLocation();
            if (cartLocation == null || cartLocation.getWorld() == null)
                continue;
            if (!Objects.equals(cartLocation.getWorld(), world))
                continue;
            double maxDistance = network.subRange();
            if (cartLocation.distanceSquared(network.main()) > maxDistance * maxDistance)
                continue;
            Location anchor = cartLocation.getBlock().getLocation();
            resolved.add(new ResolvedInventory(anchor, minecart.getInventory(), List.of(anchor)));
        }
        return resolved;
    }

    private String resolveMinecartSubId(StorageMinecart minecart) {
        String rawName = minecart.getCustomName();
        if (rawName == null || rawName.isBlank())
            return null;
        String normalized = ChatColor.stripColor(rawName).trim();
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith(SUB_PREFIX)) {
            String id = normalized.substring(SUB_PREFIX.length()).trim();
            return id.isEmpty() ? null : id;
        }
        String token = "[sub:";
        int tokenIndex = lower.indexOf(token);
        if (tokenIndex < 0)
            return null;
        int idStart = tokenIndex + token.length();
        int bracketEnd = normalized.indexOf(']', idStart);
        String id = bracketEnd >= 0
                ? normalized.substring(idStart, bracketEnd).trim()
                : normalized.substring(idStart).trim();
        return id.isEmpty() ? null : id;
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

    private boolean isPlainChestMinecartItem(ItemStack stack) {
        if (stack == null || stack.getType() != Material.CHEST_MINECART)
            return false;
        if (!stack.hasItemMeta())
            return true;
        ItemMeta meta = stack.getItemMeta();
        return !meta.hasDisplayName();
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

    private ItemStack createSharedSubMinecartItem(String id) {
        ItemStack stack = new ItemStack(Material.CHEST_MINECART);
        ItemMeta meta = stack.getItemMeta();
        ComponentUtils.setDisplayName(meta, "§b共有ストレージトロッコ §7[sub:" + id + "]");
        ComponentUtils.setLore(meta,
                "§7ID: " + id,
                "§7この名前で設置すると",
                "§7共有ストレージ sub として扱われます");
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
                case TOO_FAR -> "§csubチェストは主チェストから接続範囲以内に設置してください";
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

    private enum SubFrameFilterMode {
        EXACT("完全一致"),
        MATERIAL("素材一致"),
        ENCHANT_STATE("素材+エンチャ有無");

        private final String label;

        SubFrameFilterMode(String label) {
            this.label = label;
        }

        private String label() {
            return label;
        }

        private SubFrameFilterMode next() {
            SubFrameFilterMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        private static SubFrameFilterMode fromStored(String raw) {
            if (raw == null || raw.isEmpty())
                return EXACT;
            try {
                return SubFrameFilterMode.valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return EXACT;
            }
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

    private final class MutableSubCategory {
        private final String key;
        private final ItemStack icon;
        private final String displayName;
        private final boolean noFilter;
        private final List<ResolvedInventory> subs = new ArrayList<>();

        private MutableSubCategory(String key, ItemStack filter) {
            this.key = key;
            if (filter == null || filter.getType() == Material.AIR) {
                this.icon = new ItemStack(Material.CHEST);
                this.displayName = "§f未分類";
                this.noFilter = true;
                return;
            }
            ItemStack icon = filter.clone();
            icon.setAmount(1);
            this.icon = icon;
            this.noFilter = false;
            String name = resolveItemDisplayName(filter);
            if (key.startsWith("clear:")) {
                this.displayName = "§c処分: " + name;
            } else {
                this.displayName = "§a" + name;
            }
        }

        private SubCategoryEntry toEntry(int totalAmount) {
            return new SubCategoryEntry(key, icon, displayName, noFilter, new ArrayList<>(subs), totalAmount);
        }
    }

    private record SubCategoryEntry(String key, ItemStack icon, String displayName, boolean noFilter, List<ResolvedInventory> subs, int totalAmount) {
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
        private int subRange = DEFAULT_SUB_DISTANCE;
        private boolean enableSubFrameFilter = DEFAULT_ENABLE_SUB_FRAME_FILTER;
        private SubFrameFilterMode subFrameFilterMode = SubFrameFilterMode.EXACT;
        private boolean enableChestPage = DEFAULT_ENABLE_CHEST_PAGE;

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

        private int subRange() {
            return subRange;
        }

        private void setSubRange(int subRange) {
            this.subRange = clampSubRange(subRange);
        }

        private boolean enableSubFrameFilter() {
            return enableSubFrameFilter;
        }

        private void setEnableSubFrameFilter(boolean enableSubFrameFilter) {
            this.enableSubFrameFilter = enableSubFrameFilter;
        }

        private SubFrameFilterMode subFrameFilterMode() {
            return subFrameFilterMode;
        }

        private void setSubFrameFilterMode(SubFrameFilterMode subFrameFilterMode) {
            this.subFrameFilterMode = subFrameFilterMode == null ? SubFrameFilterMode.EXACT : subFrameFilterMode;
        }

        private boolean enableChestPage() {
            return enableChestPage;
        }

        private void setEnableChestPage(boolean enableChestPage) {
            this.enableChestPage = enableChestPage;
        }
    }

    private record SubAccess(boolean allowInsert, boolean allowExtract) {
    }

    private static int clampSubRange(int range) {
        return Math.max(1, Math.min(MAX_SUB_DISTANCE_LIMIT, range));
    }
}
