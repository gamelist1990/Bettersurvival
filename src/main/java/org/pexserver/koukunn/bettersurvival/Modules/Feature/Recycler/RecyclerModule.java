package org.pexserver.koukunn.bettersurvival.Modules.Feature.Recycler;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Display;
import org.bukkit.entity.ExperienceOrb;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.ItemCombineModule;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * リサイクラー (Rust のリサイクラー風の分解装置)。
 *
 * 砥石×鉄ブロックのアイテム合成で専用アイテムを作り、設置して右クリックすると
 * 分解装置 UI が開く。投入口に入れたアイテムはクラフトレシピを逆算して
 * 素材の約50%へ分解される。全アイテム・全ブロック対応:
 *
 * - クラフトレシピがある物 → 素材の約50%を回収口へ (端数は確率で切り上げ)
 * - クラフトレシピが無い物 → 精錬レシピの逆算 (ガラス→砂 など)
 * - どちらも無い物 → 完全処分 (消滅し、少量の経験値オーブへ変換)
 * - 耐久が減った道具は残り耐久に応じて還元量が減る
 * - 中身入りシュルカーボックスは分解せずそのまま回収口へ (誤消滅防止)
 * - 上ホッパー=投入 / 下ホッパー=回収 の自動搬入出に対応
 */
public class RecyclerModule implements Listener {

    public static final String FEATURE_KEY = "recycler";
    public static final String ITEM_NAME = "§2リサイクラー";
    /** 1サイクル(1秒)で処理する最大アイテム数 */
    public static final int ITEMS_PER_CYCLE = 8;

    private static final String PREFIX = "§2[リサイクラー]§r ";
    private static final int PROCESS_PERIOD_TICKS = 20;
    private static final int UI_ANIMATION_PERIOD_TICKS = 8;
    private static final int SAVE_PERIOD_TICKS = 600;
    private static final int HOPPER_MOVE_PER_CYCLE = 8;
    /** 素材の還元率 (Rust のリサイクラーと同じ50%) */
    private static final double RECYCLE_RATE = 0.5D;
    /** 処分1個あたりのXP */
    private static final double XP_PER_DESTROYED = 0.25D;
    private static final int DISPLAY_RADIUS_SQUARED = 16 * 16;

    private final Loader plugin;
    private final ToggleModule toggle;
    private final RecyclerStore store;
    private final NamespacedKey itemKey;
    private final NamespacedKey displayKey;
    private final Random random = new Random();

    private final Map<String, RecyclerData> recyclers = new LinkedHashMap<>();
    private final Map<String, UUID> displayIds = new LinkedHashMap<>();
    /** リサイクラー1台につき1つの共有UIセッション */
    private final Map<String, RecyclerUI> sessionsByKey = new HashMap<>();
    /** 分解レシピのキャッシュ。空リスト = 分解不可(完全処分) */
    private final Map<Material, List<Yield>> yieldCache = new HashMap<>();

    private final BukkitTask processTask;
    private final BukkitTask animationTask;
    private final BukkitTask saveTask;

    private int animationFrame;
    private boolean dirty;

    public RecyclerModule(Loader plugin, ToggleModule toggle, ItemCombineModule itemCombineModule) {
        this.plugin = plugin;
        this.toggle = toggle;
        this.store = new RecyclerStore(plugin.getConfigManager());
        this.itemKey = new NamespacedKey(plugin, "recycler");
        this.displayKey = new NamespacedKey(plugin, "recycler_display");

        itemCombineModule.recipe("recycler")
                .first(this::isPlainGrindstone)
                .second(this::isIronBlock)
                .groundRadius(0.5D)
                .airRadius(1.5D)
                .verticalRadius(2.0D)
                .allowAirCombine(true)
                .then(this::craftRecycler);

        restoreRecyclers();
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
        store.saveAll(recyclers.values());
    }

    // ================= クラフト =================

    private boolean isPlainGrindstone(ItemStack stack) {
        if (stack == null || stack.getType() != Material.GRINDSTONE) {
            return false;
        }
        return !isRecyclerItem(stack);
    }

    private boolean isIronBlock(ItemStack stack) {
        return stack != null && stack.getType() == Material.IRON_BLOCK;
    }

    private boolean isRecyclerItem(ItemStack stack) {
        if (stack == null || stack.getType() != Material.GRINDSTONE || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.STRING);
    }

    private ItemStack createRecyclerItem() {
        ItemStack stack = new ItemStack(Material.GRINDSTONE);
        ItemMeta meta = stack.getItemMeta();
        ComponentUtils.setDisplayName(meta, ITEM_NAME);
        ComponentUtils.setLore(meta,
                "§7砥石と鉄ブロックを合成した分解装置",
                "§7設置して右クリックで分解装置を開きます",
                "§7不要なアイテムを素材の約50%へ分解",
                "§7分解できない物は消滅処分 (少量のXPに変換)",
                "§7ホッパー搬入出にも対応");
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "true");
        stack.setItemMeta(meta);
        return stack;
    }

    private void craftRecycler(ItemCombineModule.CombineMatch match) {
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
        world.dropItemNaturally(center, createRecyclerItem());
        world.playSound(center, Sound.BLOCK_GRINDSTONE_USE, 1.0F, 0.8F);
        world.playSound(center, Sound.BLOCK_ANVIL_USE, 0.5F, 1.4F);
        world.spawnParticle(Particle.CRIT, center, 20, 0.4D, 0.4D, 0.4D, 0.05D);
    }

    // ================= 設置 / 破壊 =================

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isRecyclerItem(event.getItemInHand())) {
            return;
        }
        if (!isFeatureEnabled()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(PREFIX + "§cリサイクラー機能は現在無効です");
            return;
        }
        Player player = event.getPlayer();
        Location location = event.getBlockPlaced().getLocation();
        String key = RecyclerStore.toKey(location);
        recyclers.put(key, new RecyclerData(player.getUniqueId(), location));
        markDirty();
        flushIfDirty();
        player.sendMessage(PREFIX + "§a設置しました。右クリックで分解装置を開きます");
        location.getWorld().playSound(location, Sound.BLOCK_GRINDSTONE_USE, 0.8F, 0.8F);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String key = RecyclerStore.toKey(block.getLocation());
        RecyclerData data = recyclers.get(key);
        if (data == null) {
            return;
        }
        event.setDropItems(false);
        boolean dropItem = event.getPlayer().getGameMode() != GameMode.CREATIVE;
        removeRecycler(key, data, block.getLocation(), dropItem, true);
        event.getPlayer().sendMessage(PREFIX + "§eリサイクラーを回収しました");
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
            String key = RecyclerStore.toKey(block.getLocation());
            RecyclerData data = recyclers.get(key);
            if (data != null) {
                removeRecycler(key, data, block.getLocation(), true, true);
            }
        }
    }

    /** 登録解除 + 中身と本体アイテムのドロップ + セッションの後始末 */
    private void removeRecycler(String key, RecyclerData data, Location dropAt, boolean dropItem, boolean dropContents) {
        RecyclerUI ui = sessionsByKey.remove(key);
        if (ui != null) {
            ui.syncFromInventory();
            for (org.bukkit.entity.HumanEntity viewer : new ArrayList<>(ui.getInventory().getViewers())) {
                viewer.sendMessage(PREFIX + "§cこのリサイクラーは撤去されました");
            }
            ui.closeAll();
        }
        recyclers.remove(key);
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
                world.dropItemNaturally(center, createRecyclerItem());
            }
        }
        markDirty();
        flushIfDirty();
    }

    // ================= UI を開く =================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || block == null) {
            return;
        }
        String key = RecyclerStore.toKey(block.getLocation());
        RecyclerData data = recyclers.get(key);
        if (data == null) {
            return;
        }
        Player player = event.getPlayer();
        // スニーク+アイテム所持中は通常のブロック設置を優先
        if (player.isSneaking() && !player.getInventory().getItemInMainHand().getType().isAir()) {
            return;
        }
        event.setCancelled(true);
        if (!isFeatureEnabled()) {
            player.sendMessage(PREFIX + "§cリサイクラー機能は現在無効です");
            return;
        }
        openUi(player, key, data);
    }

    private void openUi(Player player, String key, RecyclerData data) {
        RecyclerUI ui = sessionsByKey.get(key);
        if (ui == null || ui.data() != data) {
            ui = new RecyclerUI(this, data, key);
            sessionsByKey.put(key, ui);
        }
        ui.open(player);
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.5F, 1.2F);
    }

    // ================= UI セッション管理 / クリック制御 =================

    /** ビューアーが1人でも見ている生きたセッションを返す。無人セッションはデータへ同期して破棄 */
    private RecyclerUI liveUi(String key) {
        RecyclerUI ui = sessionsByKey.get(key);
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
        if (!(event.getView().getTopInventory().getHolder() instanceof RecyclerUI ui)) {
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
        if (rawSlot >= 0 && rawSlot < RecyclerUI.SIZE) {
            if (ui.isEditableSlot(rawSlot)) {
                event.setCancelled(false);
                return;
            }
            event.setCancelled(true);
            return;
        }
        // プレイヤーインベントリ側: シフトクリックは投入口へ
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
            Inventory clicked = event.getClickedInventory();
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
        if (!(event.getView().getTopInventory().getHolder() instanceof RecyclerUI ui)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < RecyclerUI.SIZE && !ui.isEditableSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof RecyclerUI ui)) {
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

    // ================= メイン処理 =================

    private void tickProcess() {
        if (!isFeatureEnabled()) {
            return;
        }
        for (Map.Entry<String, RecyclerData> entry : new ArrayList<>(recyclers.entrySet())) {
            String key = entry.getKey();
            RecyclerData data = entry.getValue();
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
            if (block.getType() != Material.GRINDSTONE) {
                // イベントを介さずブロックが消えた場合も中身は失わない
                removeRecycler(key, data, location, true, true);
                continue;
            }
            RecyclerUI ui = liveUi(key);
            if (ui != null) {
                ui.syncFromInventory();
            }
            boolean changed = pullFromHopperAbove(data, block);
            if (processCycle(data, block)) {
                changed = true;
            }
            if (pushOutputToHopperBelow(data, block)) {
                changed = true;
            }
            if (changed) {
                markDirty();
            }
            if (ui != null) {
                ui.syncToInventory();
                ui.renderDynamic(animationFrame);
            }
            updateDisplay(key, data);
        }
    }

    /** 1サイクル分の分解処理。状態が変化したら true */
    private boolean processCycle(RecyclerData data, Block block) {
        int budget = ITEMS_PER_CYCLE;
        boolean changed = false;
        int recycled = 0;
        int destroyed = 0;
        ItemStack[] input = data.input();
        for (int s = 0; s < input.length && budget > 0; s++) {
            ItemStack stack = input[s];
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            // 中身入りシュルカーボックスは誤消滅防止のためそのまま回収口へ
            if (isNonEmptyShulker(stack)) {
                if (RecyclerData.addToStorage(data.output(), stack.clone()) == 0) {
                    input[s] = null;
                    changed = true;
                }
                continue;
            }
            while (budget > 0 && stack.getAmount() > 0) {
                List<Yield> yields = lookupYields(stack.getType());
                if (yields.isEmpty()) {
                    // 分解不可 → 完全処分 (少量XP)
                    data.setXpBank(data.getXpBank() + XP_PER_DESTROYED);
                    data.addDestroyed(1L);
                    destroyed++;
                } else {
                    List<ItemStack> results = rollResults(yields, durabilityFactor(stack));
                    if (!commitResults(data, results)) {
                        // 回収口に空きが無いので中断 (アイテムは消費しない)
                        budget = 0;
                        break;
                    }
                    data.addRecycled(1L);
                    recycled++;
                }
                budget--;
                changed = true;
                if (stack.getAmount() <= 1) {
                    input[s] = null;
                    break;
                }
                stack.setAmount(stack.getAmount() - 1);
            }
        }
        if (recycled + destroyed > 0) {
            playWorkEffects(block);
        }
        if (data.getXpBank() >= 1.0D) {
            int xp = (int) Math.floor(data.getXpBank());
            data.setXpBank(data.getXpBank() - xp);
            World world = block.getWorld();
            world.spawn(block.getLocation().add(0.5D, 1.1D, 0.5D), ExperienceOrb.class, orb -> orb.setExperience(xp));
            changed = true;
        }
        return changed;
    }

    /** 還元率と耐久係数を適用して実際のドロップを確率的に決める */
    private List<ItemStack> rollResults(List<Yield> yields, double durabilityFactor) {
        List<ItemStack> results = new ArrayList<>();
        for (Yield yield : yields) {
            double expected = yield.expectedPerItem() * durabilityFactor;
            int count = (int) Math.floor(expected);
            if (random.nextDouble() < expected - count) {
                count++;
            }
            if (count <= 0) {
                continue;
            }
            ItemStack out = yield.proto().clone();
            out.setAmount(count);
            results.add(out);
        }
        return results;
    }

    /** 全ての結果が回収口へ収まる場合のみ反映する。収まらなければ false */
    private boolean commitResults(RecyclerData data, List<ItemStack> results) {
        if (results.isEmpty()) {
            return true;
        }
        ItemStack[] tentative = RecyclerData.copyOf(data.output());
        for (ItemStack result : results) {
            if (RecyclerData.addToStorage(tentative, result.clone()) > 0) {
                return false;
            }
        }
        ItemStack[] output = data.output();
        System.arraycopy(tentative, 0, output, 0, output.length);
        return true;
    }

    /** 耐久が減った道具の還元係数 (残り耐久の割合)。耐久の無い物は 1.0 */
    private static double durabilityFactor(ItemStack stack) {
        short max = stack.getType().getMaxDurability();
        if (max <= 0) {
            return 1.0D;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable) || damageable.getDamage() <= 0) {
            return 1.0D;
        }
        return Math.max(0.0D, 1.0D - (double) damageable.getDamage() / (double) max);
    }

    private static boolean isNonEmptyShulker(ItemStack stack) {
        if (!(stack.getItemMeta() instanceof BlockStateMeta blockStateMeta)) {
            return false;
        }
        if (!blockStateMeta.hasBlockState() || !(blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return false;
        }
        return !shulkerBox.getInventory().isEmpty();
    }

    private void playWorkEffects(Block block) {
        World world = block.getWorld();
        Location center = block.getLocation().add(0.5D, 0.8D, 0.5D);
        world.spawnParticle(Particle.CRIT, center, 6, 0.25D, 0.2D, 0.25D, 0.02D);
        if (Math.random() < 0.3D) {
            world.playSound(center, Sound.BLOCK_GRINDSTONE_USE, 0.35F, 1.1F);
        }
    }

    // ================= ホッパー連携 =================

    /** 真上のホッパーから投入口へ引き込む */
    private boolean pullFromHopperAbove(RecyclerData data, Block block) {
        Block above = block.getRelative(BlockFace.UP);
        if (above.getType() != Material.HOPPER || !(above.getState() instanceof Hopper hopper)) {
            return false;
        }
        Inventory source = hopper.getInventory();
        int budget = HOPPER_MOVE_PER_CYCLE;
        boolean changed = false;
        for (int s = 0; s < source.getSize() && budget > 0; s++) {
            ItemStack stack = source.getItem(s);
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
                continue;
            }
            ItemStack single = stack.clone();
            single.setAmount(1);
            while (budget > 0 && stack.getAmount() > 0 && RecyclerData.hasRoomFor(data.input(), single)) {
                if (RecyclerData.addToStorage(data.input(), single.clone()) > 0) {
                    break;
                }
                budget--;
                changed = true;
                if (stack.getAmount() <= 1) {
                    source.setItem(s, null);
                    break;
                }
                stack.setAmount(stack.getAmount() - 1);
                source.setItem(s, stack);
            }
        }
        return changed;
    }

    /** 直下のホッパーへ分解済み素材を払い出す */
    private boolean pushOutputToHopperBelow(RecyclerData data, Block block) {
        Block below = block.getRelative(BlockFace.DOWN);
        if (below.getType() != Material.HOPPER || !(below.getState() instanceof Hopper hopper)) {
            return false;
        }
        Inventory dest = hopper.getInventory();
        int budget = HOPPER_MOVE_PER_CYCLE;
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

    // ================= 分解レシピの逆算 =================

    /**
     * 1個あたりの分解結果 (期待値) を求める。結果はキャッシュされる。
     *
     * クラフトレシピ(形あり/形なし)を優先し、無ければ精錬レシピの逆算、
     * それも無ければ空リスト (= 分解不可、完全処分)。
     */
    private List<Yield> lookupYields(Material type) {
        List<Yield> cached = yieldCache.get(type);
        if (cached != null) {
            return cached;
        }
        List<Yield> yields = lookupCraftingYields(type);
        if (yields.isEmpty()) {
            yields = lookupSmeltingYields(type);
        }
        yieldCache.put(type, yields);
        return yields;
    }

    private List<Yield> lookupCraftingYields(Material type) {
        for (Recipe recipe : Bukkit.getRecipesFor(new ItemStack(type))) {
            List<RecipeChoice> choices;
            if (recipe instanceof ShapedRecipe shaped) {
                choices = new ArrayList<>();
                Map<Character, RecipeChoice> choiceMap = shaped.getChoiceMap();
                for (String row : shaped.getShape()) {
                    for (char c : row.toCharArray()) {
                        RecipeChoice choice = choiceMap.get(c);
                        if (choice != null) {
                            choices.add(choice);
                        }
                    }
                }
            } else if (recipe instanceof ShapelessRecipe shapeless) {
                choices = shapeless.getChoiceList();
            } else {
                continue;
            }
            if (recipe.getResult().getType() != type) {
                continue;
            }
            int resultAmount = Math.max(1, recipe.getResult().getAmount());
            List<ItemStack> protos = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            for (RecipeChoice choice : choices) {
                ItemStack proto = representative(choice);
                if (proto == null) {
                    continue;
                }
                int index = indexOfSimilar(protos, proto);
                if (index >= 0) {
                    counts.set(index, counts.get(index) + 1);
                } else {
                    protos.add(proto);
                    counts.add(1);
                }
            }
            if (protos.isEmpty()) {
                continue;
            }
            List<Yield> yields = new ArrayList<>();
            for (int i = 0; i < protos.size(); i++) {
                double expected = (double) counts.get(i) / resultAmount * RECYCLE_RATE;
                yields.add(new Yield(protos.get(i), expected));
            }
            return yields;
        }
        return List.of();
    }

    /** 精錬レシピの逆算 (例: ガラス → 砂)。対象が精錬結果になっているレシピを探す */
    private List<Yield> lookupSmeltingYields(Material type) {
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe;
            try {
                recipe = iterator.next();
            } catch (Throwable ignored) {
                continue;
            }
            if (!(recipe instanceof FurnaceRecipe furnaceRecipe) || furnaceRecipe.getResult().getType() != type) {
                continue;
            }
            ItemStack proto = representative(furnaceRecipe.getInputChoice());
            if (proto == null) {
                continue;
            }
            int resultAmount = Math.max(1, furnaceRecipe.getResult().getAmount());
            return List.of(new Yield(proto, RECYCLE_RATE / resultAmount));
        }
        return List.of();
    }

    /** RecipeChoice の代表アイテムを返す (無ければ null) */
    private static ItemStack representative(RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
            for (Material material : materialChoice.getChoices()) {
                if (material != null && !material.isAir()) {
                    return new ItemStack(material);
                }
            }
        } else if (choice instanceof RecipeChoice.ExactChoice exactChoice) {
            for (ItemStack stack : exactChoice.getChoices()) {
                if (stack != null && !stack.getType().isAir()) {
                    ItemStack proto = stack.clone();
                    proto.setAmount(1);
                    return proto;
                }
            }
        }
        return null;
    }

    private static int indexOfSimilar(List<ItemStack> protos, ItemStack stack) {
        for (int i = 0; i < protos.size(); i++) {
            if (protos.get(i).isSimilar(stack)) {
                return i;
            }
        }
        return -1;
    }

    // ================= 見た目 (浮遊ラベル) =================

    private void updateDisplay(String key, RecyclerData data) {
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
        String status;
        if (data.hasPendingInput()) {
            status = "§a⚙ 稼働中 §7(分解 " + data.getRecycledCount() + " / 処分 " + data.getDestroyedCount() + ")";
        } else {
            status = "§7待機中 §8(分解 " + data.getRecycledCount() + " / 処分 " + data.getDestroyedCount() + ")";
        }
        display.text(ComponentUtils.legacy("§2♻ リサイクラー ♻\n" + status));
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
        for (RecyclerUI ui : new ArrayList<>(sessionsByKey.values())) {
            if (ui.hasViewers()) {
                ui.renderDynamic(animationFrame);
            }
        }
    }

    // ================= 起動 / 終了 =================

    private void restoreRecyclers() {
        for (Map.Entry<String, RecyclerData> entry : store.loadAll().entrySet()) {
            RecyclerData data = entry.getValue();
            Location location = data.location();
            World world = location.getWorld();
            if (world == null) {
                continue;
            }
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            if (world.isChunkLoaded(chunkX, chunkZ) && location.getBlock().getType() != Material.GRINDSTONE) {
                dirty = true;
                continue;
            }
            recyclers.put(entry.getKey(), data);
        }
        flushIfDirty();
    }

    public void shutdown() {
        processTask.cancel();
        animationTask.cancel();
        saveTask.cancel();
        for (RecyclerUI ui : new ArrayList<>(sessionsByKey.values())) {
            ui.syncFromInventory();
            ui.closeAll();
        }
        sessionsByKey.clear();
        for (String key : new ArrayList<>(displayIds.keySet())) {
            removeDisplay(key);
        }
        store.saveAll(recyclers.values());
    }

    /** 1個あたりの分解結果の期待値 */
    private record Yield(ItemStack proto, double expectedPerItem) {
    }
}
