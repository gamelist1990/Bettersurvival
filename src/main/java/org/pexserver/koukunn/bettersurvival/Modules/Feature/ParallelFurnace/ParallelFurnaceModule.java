package org.pexserver.koukunn.bettersurvival.Modules.Feature.ParallelFurnace;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.Furnace;
import org.bukkit.block.Hopper;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Core.Util.ItemNameUtil;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock.ChestLockModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ClaimRegion;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.LandProtectionModule;
import org.pexserver.koukunn.bettersurvival.Modules.ItemCombineModule;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 並列かまど。
 *
 * かまど×石炭ブロックのアイテム合成で専用アイテムを作り、設置して右クリックすると
 * ChestUI の「作業コア」が開く。UI にかまどを追加した数だけ焼成ラインが増え、
 * 素材・燃料を共有しながら並列で精錬する。
 *
 * - 焼けるかどうかは Bukkit のレシピ API (FurnaceRecipe) で動的に判定
 * - 焼けない素材はスルー、または指定した搬出チェスト(30ブロック以内)へ自動転送
 * - 上ホッパー=素材 / 横ホッパー=燃料 / 下ホッパー=完成品 の搬入出に対応
 * - 稼働中はブロックの点火・炎パーティクル・UI 内アニメーションで表現
 */
public class ParallelFurnaceModule implements Listener {

    public static final String FEATURE_KEY = "parallelfurnace";
    public static final String ITEM_NAME = "§6並列かまど";
    public static final int OVERFLOW_MAX_DISTANCE = 30;

    private static final String PREFIX = "§6[並列かまど]§r ";
    private static final int PROCESS_PERIOD_TICKS = 20;
    private static final int UI_ANIMATION_PERIOD_TICKS = 8;
    private static final int SAVE_PERIOD_TICKS = 600;
    private static final int HOPPER_OUTPUT_PER_CYCLE = 4;
    private static final long SELECTION_TIMEOUT_MS = 30_000L;
    private static final int DISPLAY_RADIUS_SQUARED = 16 * 16;

    private final Loader plugin;
    private final ToggleModule toggle;
    private final ChestLockModule chestLockModule;
    private final LandProtectionModule landProtectionModule;
    private final ParallelFurnaceStore store;
    private final NamespacedKey itemKey;
    private final NamespacedKey displayKey;

    private final Map<String, ParallelFurnaceData> furnaces = new LinkedHashMap<>();
    private final Map<String, UUID> displayIds = new LinkedHashMap<>();
    /** かまど1台につき1つの共有UIセッション（複数ビューアーが同じ Inventory を見る） */
    private final Map<String, ParallelFurnaceUI> sessionsByKey = new HashMap<>();
    private final Map<UUID, PendingSelection> pendingSelections = new HashMap<>();
    private final Map<Material, CookingInfo> recipeCache = new HashMap<>();
    private final Set<Material> nonSmeltableCache = new HashSet<>();
    private final Map<Material, Integer> fuelTicksCache = new HashMap<>();

    private Object nmsFuelValues;
    private Method nmsBurnDuration;
    private Method nmsAsCopy;
    private boolean nmsFuelUnavailable;

    private final BukkitTask processTask;
    private final BukkitTask animationTask;
    private final BukkitTask saveTask;

    private int animationFrame;
    private boolean dirty;

    public ParallelFurnaceModule(Loader plugin, ToggleModule toggle, ItemCombineModule itemCombineModule,
                                 ChestLockModule chestLockModule, LandProtectionModule landProtectionModule) {
        this.plugin = plugin;
        this.toggle = toggle;
        this.chestLockModule = chestLockModule;
        this.landProtectionModule = landProtectionModule;
        this.store = new ParallelFurnaceStore(plugin.getConfigManager());
        this.itemKey = new NamespacedKey(plugin, "parallel_furnace");
        this.displayKey = new NamespacedKey(plugin, "parallel_furnace_display");

        itemCombineModule.recipe("parallel_furnace")
                .first(this::isPlainFurnace)
                .second(this::isCoalBlock)
                .groundRadius(0.5D)
                .airRadius(1.5D)
                .verticalRadius(2.0D)
                .allowAirCombine(true)
                .then(this::craftParallelFurnace);

        restoreFurnaces();
        processTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickProcess, PROCESS_PERIOD_TICKS, PROCESS_PERIOD_TICKS);
        animationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickUiAnimation, UI_ANIMATION_PERIOD_TICKS, UI_ANIMATION_PERIOD_TICKS);
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushIfDirty, SAVE_PERIOD_TICKS, SAVE_PERIOD_TICKS);
    }

    private boolean isFeatureEnabled() {
        return toggle.getGlobal(FEATURE_KEY);
    }

    void markDirty() {
        dirty = true;
    }

    private void flushIfDirty() {
        if (!dirty) {
            return;
        }
        dirty = false;
        store.saveAll(furnaces.values());
    }

    // ================= クラフト =================

    boolean isPlainFurnace(ItemStack stack) {
        if (stack == null || stack.getType() != Material.FURNACE) {
            return false;
        }
        return !isParallelFurnaceItem(stack);
    }

    private boolean isCoalBlock(ItemStack stack) {
        return stack != null && stack.getType() == Material.COAL_BLOCK;
    }

    private boolean isParallelFurnaceItem(ItemStack stack) {
        if (stack == null || stack.getType() != Material.FURNACE || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.STRING);
    }

    private ItemStack createParallelFurnaceItem() {
        ItemStack stack = new ItemStack(Material.FURNACE);
        ItemMeta meta = stack.getItemMeta();
        ComponentUtils.setDisplayName(meta, ITEM_NAME);
        ComponentUtils.setLore(meta,
                "§7かまどと石炭ブロックを合成した高性能かまど",
                "§7設置して右クリックで作業コアを開きます",
                "§7かまどを追加すると最大" + ParallelFurnaceData.MAX_LINES + "並列で焼成",
                "§7ホッパー搬入出 / 搬出チェスト指定にも対応");
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "true");
        stack.setItemMeta(meta);
        return stack;
    }

    private void craftParallelFurnace(ItemCombineModule.CombineMatch match) {
        if (!isFeatureEnabled()) {
            return;
        }
        if (!match.first().isValid() || !match.second().isValid()) {
            return;
        }
        Location center = match.center();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        match.consumeMatchedItems(1, 1);
        world.dropItemNaturally(center, createParallelFurnaceItem());
        world.playSound(center, Sound.BLOCK_FURNACE_FIRE_CRACKLE, 1.0F, 1.0F);
        world.playSound(center, Sound.BLOCK_ANVIL_USE, 0.5F, 1.4F);
        world.spawnParticle(Particle.FLAME, center, 20, 0.4D, 0.4D, 0.4D, 0.02D);
    }

    // ================= 設置 / 破壊 =================

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isParallelFurnaceItem(event.getItemInHand())) {
            return;
        }
        if (!isFeatureEnabled()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(PREFIX + "§c並列かまど機能は現在無効です");
            return;
        }
        Player player = event.getPlayer();
        Location location = event.getBlockPlaced().getLocation();
        String key = ParallelFurnaceStore.toKey(location);
        furnaces.put(key, new ParallelFurnaceData(player.getUniqueId(), location));
        markDirty();
        flushIfDirty();
        player.sendMessage(PREFIX + "§a設置しました。右クリックで作業コアを開きます");
        location.getWorld().playSound(location, Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.8F, 1.0F);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String key = ParallelFurnaceStore.toKey(block.getLocation());
        ParallelFurnaceData data = furnaces.get(key);
        if (data == null) {
            return;
        }
        event.setDropItems(false);
        boolean dropItem = event.getPlayer().getGameMode() != GameMode.CREATIVE;
        removeFurnace(key, data, block.getLocation(), dropItem, true);
        event.getPlayer().sendMessage(PREFIX + "§e並列かまどを回収しました");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeExploded(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeExploded(event.blockList());
    }

    private void removeExploded(List<Block> blocks) {
        for (Block block : new ArrayList<>(blocks)) {
            String key = ParallelFurnaceStore.toKey(block.getLocation());
            ParallelFurnaceData data = furnaces.get(key);
            if (data != null) {
                removeFurnace(key, data, block.getLocation(), true, true);
            }
        }
    }

    /** 登録解除 + 中身と本体アイテムのドロップ + 表示/セッションの後始末 */
    private void removeFurnace(String key, ParallelFurnaceData data, Location dropAt, boolean dropItem, boolean dropContents) {
        ParallelFurnaceUI ui = sessionsByKey.remove(key);
        if (ui != null) {
            ui.syncFromInventory();
            for (org.bukkit.entity.HumanEntity viewer : new ArrayList<>(ui.getInventory().getViewers())) {
                viewer.sendMessage(PREFIX + "§cこの並列かまどは撤去されました");
            }
            ui.closeAll();
        }
        furnaces.remove(key);
        removeDisplay(key);
        World world = dropAt.getWorld();
        if (world != null) {
            Location center = dropAt.clone().add(0.5D, 0.3D, 0.5D);
            if (dropContents) {
                for (ItemStack stack : data.collectAllItems()) {
                    world.dropItemNaturally(center, stack);
                }
            }
            if (dropItem) {
                world.dropItemNaturally(center, createParallelFurnaceItem());
            }
        }
        markDirty();
        flushIfDirty();
    }

    // ================= UI を開く / 搬出チェスト指定 =================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        PendingSelection pending = pendingSelections.get(player.getUniqueId());
        if (pending != null && block != null
                && (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_BLOCK)) {
            event.setCancelled(true);
            handleOverflowSelection(player, pending, block, event.getAction());
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null) {
            return;
        }
        String key = ParallelFurnaceStore.toKey(block.getLocation());
        ParallelFurnaceData data = furnaces.get(key);
        if (data == null) {
            return;
        }
        // スニーク+アイテム所持中は通常のブロック設置を優先
        if (player.isSneaking() && !player.getInventory().getItemInMainHand().getType().isAir()) {
            return;
        }
        event.setCancelled(true);
        if (!isFeatureEnabled()) {
            player.sendMessage(PREFIX + "§c並列かまど機能は現在無効です");
            return;
        }
        openUi(player, key, data);
    }

    private void openUi(Player player, String key, ParallelFurnaceData data) {
        ParallelFurnaceUI ui = sessionsByKey.get(key);
        if (ui == null || ui.data() != data) {
            ui = new ParallelFurnaceUI(this, data, key);
            sessionsByKey.put(key, ui);
        }
        ui.open(player);
        player.playSound(player.getLocation(), Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.5F, 1.2F);
    }

    void beginOverflowSelection(Player player, String key) {
        pendingSelections.put(player.getUniqueId(), new PendingSelection(key, System.currentTimeMillis() + SELECTION_TIMEOUT_MS));
        player.closeInventory();
        player.sendMessage(PREFIX + "§e" + OVERFLOW_MAX_DISTANCE + "ブロック以内のチェスト/樽を§f右クリック§eしてください (30秒以内)");
        player.sendMessage(PREFIX + "§7他人の土地保護内や、他人がロックしたチェストは指定できません");
    }

    private void handleOverflowSelection(Player player, PendingSelection pending, Block block, Action action) {
        if (System.currentTimeMillis() > pending.expireAt()) {
            pendingSelections.remove(player.getUniqueId());
            player.sendMessage(PREFIX + "§c搬出チェストの指定が時間切れになりました");
            return;
        }
        if (action == Action.LEFT_CLICK_BLOCK) {
            pendingSelections.remove(player.getUniqueId());
            player.sendMessage(PREFIX + "§e搬出チェストの指定をキャンセルしました");
            return;
        }
        ParallelFurnaceData data = furnaces.get(pending.furnaceKey());
        if (data == null) {
            pendingSelections.remove(player.getUniqueId());
            player.sendMessage(PREFIX + "§c対象の並列かまどが見つかりません（撤去された可能性）");
            return;
        }
        Material type = block.getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST && type != Material.BARREL) {
            player.sendMessage(PREFIX + "§cチェスト/トラップチェスト/樽を右クリックしてください");
            return;
        }
        Location chestLoc = block.getLocation();
        Location furnaceLoc = data.location();
        if (furnaceLoc.getWorld() == null || !furnaceLoc.getWorld().equals(chestLoc.getWorld())
                || chestLoc.distanceSquared(furnaceLoc) > (double) OVERFLOW_MAX_DISTANCE * OVERFLOW_MAX_DISTANCE) {
            player.sendMessage(PREFIX + "§cかまどから" + OVERFLOW_MAX_DISTANCE + "ブロック以内のチェストを指定してください");
            return;
        }
        if (chestLockModule != null && !chestLockModule.canAccess(player, chestLoc)) {
            player.sendMessage(PREFIX + "§cそのチェストはロックされているため指定できません");
            return;
        }
        if (landProtectionModule != null) {
            ClaimRegion claim = landProtectionModule.getActiveClaimAt(chestLoc);
            if (claim != null && !landProtectionModule.canBypass(player, claim)) {
                player.sendMessage(PREFIX + "§cそのチェストは他人の土地保護内にあるため指定できません");
                return;
            }
        }
        pendingSelections.remove(player.getUniqueId());
        data.setOverflowChest(chestLoc);
        markDirty();
        player.sendMessage(PREFIX + "§a搬出チェストを設定しました §7("
                + chestLoc.getBlockX() + ", " + chestLoc.getBlockY() + ", " + chestLoc.getBlockZ() + ")");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.2F);
    }

    // ================= UI セッション管理 / クリック制御 =================

    /** ビューアーが1人でも見ている生きたセッションを返す。無人セッションはデータへ同期して破棄 */
    private ParallelFurnaceUI liveUi(String key) {
        ParallelFurnaceUI ui = sessionsByKey.get(key);
        if (ui == null) {
            return null;
        }
        if (!ui.hasViewers()) {
            ui.syncFromInventory();
            sessionsByKey.remove(key);
            markDirty();
            return null;
        }
        return ui;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ParallelFurnaceUI ui)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // ダブルクリック収集はボタンアイテムまで吸い込むため全面禁止
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < ParallelFurnaceUI.SIZE) {
            if (ui.isEditableSlot(rawSlot)) {
                event.setCancelled(false);
                return;
            }
            event.setCancelled(true);
            ui.handleButtonClick(player, rawSlot);
            return;
        }
        // プレイヤーインベントリ側: シフトクリックは素材/燃料へ自動振り分け
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
            ItemStack source = event.getCurrentItem();
            if (source == null || source.getType().isAir() || source.getAmount() <= 0) {
                return;
            }
            int moved = ui.acceptShiftClick(source);
            if (moved <= 0) {
                return;
            }
            int remain = source.getAmount() - moved;
            org.bukkit.inventory.Inventory clicked = event.getClickedInventory();
            if (clicked != null) {
                if (remain <= 0) {
                    clicked.setItem(event.getSlot(), null);
                } else {
                    source.setAmount(remain);
                    clicked.setItem(event.getSlot(), source);
                }
            }
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ParallelFurnaceUI ui)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < ParallelFurnaceUI.SIZE && !ui.isEditableSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ParallelFurnaceUI ui)) {
            return;
        }
        // 閉じた本人はまだ getViewers() に残っていることがあるため次tickで判定
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (ui.hasViewers()) {
                return;
            }
            ui.syncFromInventory();
            sessionsByKey.remove(ui.key(), ui);
            markDirty();
            flushIfDirty();
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // UI を開いたまま切断してもサーバーが InventoryCloseEvent を発行するため、
        // ここでは搬出チェスト指定の後始末だけ行う
        pendingSelections.remove(event.getPlayer().getUniqueId());
    }

    // ================= ホッパー連携 =================

    @EventHandler(ignoreCancelled = true)
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (!(event.getDestination().getHolder() instanceof Furnace furnaceState)) {
            return;
        }
        String key = ParallelFurnaceStore.toKey(furnaceState.getLocation());
        ParallelFurnaceData data = furnaces.get(key);
        if (data == null) {
            return;
        }
        // 実ブロックのかまどインベントリには絶対に入れない
        event.setCancelled(true);
        if (!isFeatureEnabled()) {
            return;
        }
        if (!(event.getSource().getHolder() instanceof Hopper sourceHopper)) {
            return;
        }
        boolean intoFuel = sourceHopper.getLocation().getBlockY() <= furnaceState.getLocation().getBlockY();
        ItemStack single = event.getItem().clone();
        single.setAmount(1);
        if (intoFuel && burnTicksOf(single.getType()) <= 0) {
            return; // 横ホッパーは燃料のみ受け入れ
        }
        Inventory source = event.getSource();
        Bukkit.getScheduler().runTask(plugin, () -> acceptHopperItem(key, intoFuel, single, source));
    }

    private void acceptHopperItem(String key, boolean intoFuel, ItemStack single, Inventory source) {
        ParallelFurnaceData data = furnaces.get(key);
        if (data == null) {
            return;
        }
        ParallelFurnaceUI ui = liveUi(key);
        boolean hasRoom = ui != null
                ? ui.hasRoomLive(intoFuel, single)
                : ParallelFurnaceData.hasRoomFor(intoFuel ? data.fuel() : data.input(), single);
        if (!hasRoom) {
            return;
        }
        if (!source.removeItem(single.clone()).isEmpty()) {
            return; // 元アイテムが既に無い
        }
        if (ui != null) {
            ui.insertLive(intoFuel, single);
        } else {
            ParallelFurnaceData.addToStorage(intoFuel ? data.fuel() : data.input(), single.clone());
        }
        markDirty();
    }

    // ================= メイン処理 =================

    private void tickProcess() {
        if (!isFeatureEnabled()) {
            return;
        }
        for (Map.Entry<String, ParallelFurnaceData> entry : new ArrayList<>(furnaces.entrySet())) {
            String key = entry.getKey();
            ParallelFurnaceData data = entry.getValue();
            Location location = data.location();
            World world = location.getWorld();
            if (world == null) {
                continue;
            }
            if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                removeDisplay(key);
                continue;
            }
            Block block = location.getBlock();
            if (block.getType() != Material.FURNACE) {
                // イベントを介さずブロックが消えた場合も中身は失わない
                removeFurnace(key, data, location, true, true);
                continue;
            }
            ParallelFurnaceUI ui = liveUi(key);
            if (ui != null) {
                ui.syncFromInventory();
            }
            if (processData(data)) {
                markDirty();
            }
            if (ui != null) {
                ui.syncToInventory();
                ui.renderDynamic(animationFrame);
            }
            updateBlockVisuals(key, data, block);
        }
    }

    /** 1サイクル分の焼成処理。状態が変化したら true */
    private boolean processData(ParallelFurnaceData data) {
        boolean changed = false;
        ParallelFurnaceData.SmeltJob[] jobs = data.jobs();
        int lines = data.lines();

        // コアが外されて縮んだラインのジョブは素材へ戻す
        for (int i = lines; i < ParallelFurnaceData.MAX_LINES; i++) {
            ParallelFurnaceData.SmeltJob job = jobs[i];
            if (job == null) {
                continue;
            }
            if (job.source != null && ParallelFurnaceData.addToStorage(data.input(), job.source.clone()) > 0) {
                ParallelFurnaceData.addToStorage(data.output(), job.source.clone());
            }
            jobs[i] = null;
            changed = true;
        }

        // 空きラインへ素材を割り当て（焼けるかはレシピAPIで動的判定）
        for (int line = 0; line < lines; line++) {
            if (jobs[line] != null) {
                continue;
            }
            ItemStack[] input = data.input();
            for (int s = 0; s < input.length; s++) {
                ItemStack stack = input[s];
                if (stack == null || stack.getType().isAir()) {
                    continue;
                }
                CookingInfo info = lookupRecipe(stack);
                if (info == null) {
                    continue; // 焼けない物はスルー
                }
                ItemStack source = stack.clone();
                source.setAmount(1);
                jobs[line] = new ParallelFurnaceData.SmeltJob(source, info.result().clone(), info.cookTimeTicks());
                if (stack.getAmount() <= 1) {
                    input[s] = null;
                } else {
                    stack.setAmount(stack.getAmount() - 1);
                }
                changed = true;
                break;
            }
        }

        // 燃料消費と進捗（完成待ちラインは燃料を食わない）
        int burningLines = 0;
        for (int i = 0; i < lines; i++) {
            if (jobs[i] != null && !jobs[i].isDone()) {
                burningLines++;
            }
        }
        if (burningLines > 0) {
            while (data.getFuelBankTicks() <= 0) {
                if (!consumeOneFuel(data)) {
                    break;
                }
                changed = true;
            }
            if (data.getFuelBankTicks() > 0) {
                data.setFuelBankTicks(data.getFuelBankTicks() - (long) PROCESS_PERIOD_TICKS * burningLines);
                for (int i = 0; i < lines; i++) {
                    if (jobs[i] != null && !jobs[i].isDone()) {
                        jobs[i].progressTicks += PROCESS_PERIOD_TICKS;
                    }
                }
                changed = true;
            }
        }

        // 完成品の払い出し
        for (int i = 0; i < lines; i++) {
            ParallelFurnaceData.SmeltJob job = jobs[i];
            if (job == null || !job.isDone()) {
                continue;
            }
            int leftover = ParallelFurnaceData.addToStorage(data.output(), job.result.clone());
            if (leftover <= 0) {
                jobs[i] = null;
                changed = true;
            } else if (leftover != job.result.getAmount()) {
                job.result.setAmount(leftover);
                changed = true;
            }
        }

        if (routeNonSmeltables(data)) {
            changed = true;
        }
        if (pushOutputToHopperBelow(data)) {
            changed = true;
        }
        return changed;
    }

    private boolean consumeOneFuel(ParallelFurnaceData data) {
        ItemStack[] fuel = data.fuel();
        for (int s = 0; s < fuel.length; s++) {
            ItemStack stack = fuel[s];
            if (stack == null) {
                continue;
            }
            int ticks = burnTicksOf(stack.getType());
            if (ticks <= 0) {
                continue;
            }
            boolean lavaBucket = stack.getType() == Material.LAVA_BUCKET;
            if (stack.getAmount() <= 1) {
                fuel[s] = lavaBucket ? new ItemStack(Material.BUCKET) : null;
            } else {
                stack.setAmount(stack.getAmount() - 1);
                if (lavaBucket) {
                    ParallelFurnaceData.addToStorage(data.output(), new ItemStack(Material.BUCKET));
                }
            }
            data.setFuelBankTicks(data.getFuelBankTicks() + ticks);
            data.setFuelBankMaxTicks(ticks);
            return true;
        }
        return false;
    }

    /** 焼けない素材を搬出チェストへ転送する */
    private boolean routeNonSmeltables(ParallelFurnaceData data) {
        Location chestLoc = data.getOverflowChest();
        if (chestLoc == null || chestLoc.getWorld() == null) {
            return false;
        }
        if (!chestLoc.getWorld().isChunkLoaded(chestLoc.getBlockX() >> 4, chestLoc.getBlockZ() >> 4)) {
            return false;
        }
        if (!(chestLoc.getBlock().getState() instanceof Container container)) {
            data.setOverflowChest(null);
            return true;
        }
        Inventory dest = container.getInventory();
        boolean changed = false;
        ItemStack[] input = data.input();
        for (int s = 0; s < input.length; s++) {
            ItemStack stack = input[s];
            if (stack == null || stack.getType().isAir() || lookupRecipe(stack) != null) {
                continue;
            }
            Map<Integer, ItemStack> leftover = dest.addItem(stack.clone());
            if (leftover.isEmpty()) {
                input[s] = null;
                changed = true;
            } else {
                ItemStack rest = leftover.values().iterator().next();
                if (rest.getAmount() != stack.getAmount()) {
                    stack.setAmount(rest.getAmount());
                    changed = true;
                }
            }
        }
        return changed;
    }

    /** 直下のホッパーへ完成品を払い出す */
    private boolean pushOutputToHopperBelow(ParallelFurnaceData data) {
        Block below = data.location().getBlock().getRelative(BlockFace.DOWN);
        if (below.getType() != Material.HOPPER || !(below.getState() instanceof Hopper hopper)) {
            return false;
        }
        Inventory dest = hopper.getInventory();
        int budget = HOPPER_OUTPUT_PER_CYCLE;
        boolean changed = false;
        ItemStack[] output = data.output();
        for (int s = 0; s < output.length && budget > 0; s++) {
            ItemStack stack = output[s];
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            ItemStack moving = stack.clone();
            moving.setAmount(Math.min(budget, stack.getAmount()));
            Map<Integer, ItemStack> leftover = dest.addItem(moving);
            int notMoved = leftover.isEmpty() ? 0 : leftover.values().iterator().next().getAmount();
            int moved = moving.getAmount() - notMoved;
            if (moved <= 0) {
                continue;
            }
            budget -= moved;
            changed = true;
            if (stack.getAmount() <= moved) {
                output[s] = null;
            } else {
                stack.setAmount(stack.getAmount() - moved);
            }
        }
        return changed;
    }

    // ================= レシピ / 燃料判定 =================

    /** かまどで焼けるかを Bukkit レシピ API で動的判定（結果はキャッシュ） */
    CookingInfo lookupRecipe(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        Material type = stack.getType();
        CookingInfo cached = recipeCache.get(type);
        if (cached != null) {
            return cached;
        }
        if (nonSmeltableCache.contains(type)) {
            return null;
        }
        ItemStack probe = new ItemStack(type);
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe;
            try {
                recipe = iterator.next();
            } catch (Throwable ignored) {
                continue;
            }
            if (!(recipe instanceof FurnaceRecipe furnaceRecipe)) {
                continue;
            }
            try {
                if (furnaceRecipe.getInputChoice().test(probe)) {
                    CookingInfo info = new CookingInfo(furnaceRecipe.getResult().clone(), furnaceRecipe.getCookingTime());
                    recipeCache.put(type, info);
                    return info;
                }
            } catch (Throwable ignored) {
            }
        }
        nonSmeltableCache.add(type);
        return null;
    }

    /**
     * 通常かまどと同一の燃焼時間 (tick)。燃料でなければ 0。
     *
     * まずサーバー内部の燃料テーブル (NMS FuelValues) をリフレクションで参照し、
     * Vanilla (+データパック改変) の正確な値を返す。参照できない環境では
     * Vanilla の燃料表を再現した内蔵テーブルへフォールバックする。
     */
    public int burnTicksOf(Material material) {
        if (material == null || !material.isFuel()) {
            return 0;
        }
        Integer cached = fuelTicksCache.get(material);
        if (cached != null) {
            return cached;
        }
        int ticks = lookupServerBurnTicks(material);
        if (ticks < 0) {
            ticks = vanillaFallbackBurnTicks(material);
        }
        fuelTicksCache.put(material, ticks);
        return ticks;
    }

    /** サーバー実装 (FuelValues#burnDuration) から燃焼時間を取得。使えない場合は -1 */
    private int lookupServerBurnTicks(Material material) {
        if (nmsFuelUnavailable) {
            return -1;
        }
        try {
            if (nmsBurnDuration == null) {
                Object craftServer = Bukkit.getServer();
                Object mcServer = craftServer.getClass().getMethod("getServer").invoke(craftServer);
                Object fuelValues = null;
                for (Method method : mcServer.getClass().getMethods()) {
                    if (method.getName().equals("fuelValues") && method.getParameterCount() == 0) {
                        fuelValues = method.invoke(mcServer);
                        break;
                    }
                }
                if (fuelValues == null) {
                    throw new NoSuchMethodException("MinecraftServer#fuelValues");
                }
                Method burn = null;
                for (Method method : fuelValues.getClass().getMethods()) {
                    if (method.getName().equals("burnDuration") && method.getParameterCount() == 1) {
                        burn = method;
                        break;
                    }
                }
                if (burn == null) {
                    throw new NoSuchMethodException("FuelValues#burnDuration");
                }
                Class<?> craftItemStack = Class.forName(craftServer.getClass().getPackageName() + ".inventory.CraftItemStack");
                nmsAsCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
                nmsFuelValues = fuelValues;
                nmsBurnDuration = burn;
            }
            Object nmsStack = nmsAsCopy.invoke(null, new ItemStack(material));
            Object result = nmsBurnDuration.invoke(nmsFuelValues, nmsStack);
            return result instanceof Number number ? Math.max(0, number.intValue()) : -1;
        } catch (Throwable t) {
            nmsFuelUnavailable = true;
            plugin.getLogger().info("[並列かまど] サーバー内部の燃料テーブルに接続できないため、内蔵のVanilla燃料表を使用します: " + t);
            return -1;
        }
    }

    /** Vanilla の燃料表 (FuelValues.vanillaBurnTimes 相当) を再現したフォールバック */
    private int vanillaFallbackBurnTicks(Material material) {
        switch (material) {
            case LAVA_BUCKET:
                return 20000;
            case COAL_BLOCK:
                return 16000;
            case DRIED_KELP_BLOCK:
                return 4001;
            case BLAZE_ROD:
                return 2400;
            case COAL:
            case CHARCOAL:
                return 1600;
            case BAMBOO_MOSAIC:
            case NOTE_BLOCK:
            case BOOKSHELF:
            case CHISELED_BOOKSHELF:
            case LECTERN:
            case JUKEBOX:
            case CHEST:
            case TRAPPED_CHEST:
            case CRAFTING_TABLE:
            case DAYLIGHT_DETECTOR:
            case BOW:
            case FISHING_ROD:
            case LADDER:
            case CROSSBOW:
            case LOOM:
            case BARREL:
            case CARTOGRAPHY_TABLE:
            case FLETCHING_TABLE:
            case SMITHING_TABLE:
            case COMPOSTER:
                return 300;
            case BAMBOO_MOSAIC_SLAB:
                return 150;
            case WOODEN_SWORD:
            case WOODEN_SHOVEL:
            case WOODEN_PICKAXE:
            case WOODEN_AXE:
            case WOODEN_HOE:
                return 200;
            case STICK:
            case BOWL:
            case DEAD_BUSH:
            case AZALEA:
            case FLOWERING_AZALEA:
                return 100;
            case BAMBOO:
            case SCAFFOLDING:
                return 50;
            default:
                break;
        }
        if (isTagged(material, Tag.LOGS_THAT_BURN) || isTagged(material, Tag.BAMBOO_BLOCKS)
                || isTagged(material, Tag.PLANKS) || isTagged(material, Tag.WOODEN_STAIRS)
                || isTagged(material, Tag.WOODEN_TRAPDOORS) || isTagged(material, Tag.WOODEN_PRESSURE_PLATES)
                || isTagged(material, Tag.WOODEN_FENCES) || isTagged(material, Tag.FENCE_GATES)
                || isTagged(material, Tag.BANNERS)) {
            return 300;
        }
        if (isTagged(material, Tag.WOODEN_SLABS)) {
            return 150;
        }
        if (isTagged(material, Tag.WOODEN_DOORS)) {
            return 200;
        }
        if (isTagged(material, Tag.WOOL) || isTagged(material, Tag.WOODEN_BUTTONS) || isTagged(material, Tag.SAPLINGS)) {
            return 100;
        }
        if (isTagged(material, Tag.WOOL_CARPETS)) {
            return 67;
        }
        String name = material.name();
        if (name.endsWith("_HANGING_SIGN")) {
            return 800;
        }
        if (name.endsWith("_SIGN")) {
            return 200;
        }
        if (name.endsWith("_BOAT") || name.endsWith("_RAFT")) {
            return 1200; // チェスト付きボート/竹イカダ含む
        }
        // isFuel()=true だが表に無い新規アイテム: 木材系の標準値
        return 300;
    }

    private static boolean isTagged(Material material, Tag<Material> tag) {
        try {
            return tag != null && tag.isTagged(material);
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ================= 見た目 (点火 / パーティクル / ラベル) =================

    private void updateBlockVisuals(String key, ParallelFurnaceData data, Block block) {
        boolean active = false;
        for (int i = 0; i < data.lines(); i++) {
            ParallelFurnaceData.SmeltJob job = data.jobs()[i];
            if (job != null && !job.isDone()) {
                active = true;
                break;
            }
        }
        if (block.getBlockData() instanceof Lightable lightable && lightable.isLit() != active) {
            lightable.setLit(active);
            block.setBlockData(lightable);
        }
        World world = block.getWorld();
        Location center = block.getLocation().add(0.5D, 1.05D, 0.5D);
        if (active) {
            world.spawnParticle(Particle.FLAME, center, 4, 0.25D, 0.15D, 0.25D, 0.01D);
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center.clone().add(0.0D, 0.4D, 0.0D), 1, 0.1D, 0.05D, 0.1D, 0.005D);
            if (Math.random() < 0.15D) {
                world.playSound(center, Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.4F, 1.0F);
            }
        }
        updateDisplay(key, data, active);
    }

    private void updateDisplay(String key, ParallelFurnaceData data, boolean active) {
        Location location = data.location();
        if (!hasNearbyPlayer(location)) {
            removeDisplay(key);
            return;
        }
        TextDisplay display = getTrackedDisplay(key);
        if (display == null) {
            display = findNearbyDisplay(location, key);
            if (display == null) {
                display = spawnDisplay(location, key);
            }
            if (display == null) {
                return;
            }
            displayIds.put(key, display.getUniqueId());
        }
        String status = active
                ? "§c✦ 稼働中 §7(" + data.activeJobCount() + "/" + data.lines() + "並列)"
                : "§7待機中 §8(" + data.lines() + "並列)";
        display.text(ComponentUtils.legacy("§6⚒ 並列かまど ⚒\n" + status));
    }

    private boolean hasNearbyPlayer(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= DISPLAY_RADIUS_SQUARED) {
                return true;
            }
        }
        return false;
    }

    private TextDisplay getTrackedDisplay(String key) {
        UUID uuid = displayIds.get(key);
        if (uuid == null) {
            return null;
        }
        if (!(Bukkit.getEntity(uuid) instanceof TextDisplay display) || !display.isValid()) {
            displayIds.remove(key);
            return null;
        }
        return display;
    }

    private TextDisplay findNearbyDisplay(Location location, String key) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(location.clone().add(0.5D, 1.2D, 0.5D), 0.8D, 1.2D, 0.8D)) {
            if (!(entity instanceof TextDisplay display)) {
                continue;
            }
            PersistentDataContainer container = display.getPersistentDataContainer();
            if (key.equals(container.get(displayKey, PersistentDataType.STRING))) {
                return display;
            }
        }
        return null;
    }

    private TextDisplay spawnDisplay(Location location, String key) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        Location spawn = location.clone().add(0.5D, 1.25D, 0.5D);
        return world.spawn(spawn, TextDisplay.class, display -> {
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(false);
            display.setShadowed(false);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.85F, 0.85F, 0.85F), new AxisAngle4f()));
            display.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, key);
        });
    }

    private void removeDisplay(String key) {
        TextDisplay display = getTrackedDisplay(key);
        if (display != null) {
            display.remove();
        }
        displayIds.remove(key);
    }

    // ================= UI アニメーション =================

    private void tickUiAnimation() {
        animationFrame++;
        for (ParallelFurnaceUI ui : new ArrayList<>(sessionsByKey.values())) {
            if (ui.hasViewers()) {
                ui.renderDynamic(animationFrame);
            }
        }
    }

    // ================= 起動 / 終了 =================

    private void restoreFurnaces() {
        for (Map.Entry<String, ParallelFurnaceData> entry : store.loadAll().entrySet()) {
            ParallelFurnaceData data = entry.getValue();
            Location location = data.location();
            World world = location.getWorld();
            if (world == null) {
                continue;
            }
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            if (world.isChunkLoaded(chunkX, chunkZ) && location.getBlock().getType() != Material.FURNACE) {
                dirty = true;
                continue;
            }
            furnaces.put(entry.getKey(), data);
        }
        flushIfDirty();
    }

    public void shutdown() {
        processTask.cancel();
        animationTask.cancel();
        saveTask.cancel();
        for (ParallelFurnaceUI ui : new ArrayList<>(sessionsByKey.values())) {
            ui.syncFromInventory();
            ui.closeAll();
        }
        sessionsByKey.clear();
        for (String key : new ArrayList<>(displayIds.keySet())) {
            removeDisplay(key);
        }
        store.saveAll(furnaces.values());
    }

    // ================= ヘルパー =================

    static String materialName(ItemStack stack, Player player) {
        if (stack == null || stack.getType().isAir()) {
            return "不明";
        }
        ItemMeta meta = stack.getItemMeta();
        String custom = meta == null ? null : ComponentUtils.getDisplayName(meta);
        if (custom != null && !custom.isBlank()) {
            return custom;
        }
        return ItemNameUtil.localizedPlainText(stack, player);
    }

    record CookingInfo(ItemStack result, int cookTimeTicks) {
    }

    private record PendingSelection(String furnaceKey, long expireAt) {
    }
}
