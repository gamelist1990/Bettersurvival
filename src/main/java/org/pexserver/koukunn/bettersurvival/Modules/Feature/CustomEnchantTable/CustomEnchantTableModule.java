package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchantRegistry;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.AreaBreakEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.AutoCollectEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.AutoSmeltEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.BattleRepairEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.MagnetEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.MomentumEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.SedimentBreakEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.XpBoostEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.NightVisionEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.HasteEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.WisdomEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.LeafFortuneEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.AccelerateGrowthEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.FireproofBootsEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.HandOfThiefEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.VoidProtectionEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.VolleyShotEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.LongShotEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.VoidRescueEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.ChainResurrectEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.EnderChestEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.SniperEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.AqualungEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.HungerSaverEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.ThornlessEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants.JumpBoostBootsEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.ui.CustomEnchantTableUI;
import org.pexserver.koukunn.bettersurvival.Modules.ItemCombineModule;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * カスタムエンチャントテーブル。
 *
 * エンチャントテーブル×ラピスラズリのアイテム合成で専用アイテムを作り、
 * 設置して右クリックすると専用UIが開く。道具を入れて素材を払うと
 * カスタムエンチャント (採掘加速 / 範囲採掘 / 自動回収 …) をレベル制で付与できる。
 *
 * エンチャント本体は enchants/ 配下にモジュラー実装されており、
 * {@link CustomEnchantRegistry} に登録するだけで UI と効果の両方が有効になる。
 */
public class CustomEnchantTableModule implements Listener {

    public static final String FEATURE_KEY = "customenchant";
    public static final String ITEM_NAME = "§dカスタムエンチャントテーブル";

    private static final String PREFIX = "§d[エンチャント]§r ";

    private static final String DISPLAY_LABEL = "§d✦ カスタムエンチャントテーブル ✦\n§7道具を持って右クリック";
    private static final int DISPLAY_RADIUS_SQUARED = 16 * 16;

    private final Loader plugin;
    private final ToggleModule toggle;
    private final CustomEnchantTableStore store;
    private final CustomEnchantRegistry registry;
    private final NamespacedKey itemKey;
    private final NamespacedKey displayKey;

    /** 設置済みテーブル: locationKey → 設置者 */
    private final Map<String, UUID> tables = new LinkedHashMap<>();
    /** 開いているUI: プレイヤー → UIインスタンス */
    private final Map<UUID, CustomEnchantTableUI> openUis = new HashMap<>();
    /** 設置テーブルの頭上ネームプレート */
    private final Map<String, UUID> displayIds = new LinkedHashMap<>();
    private final BukkitTask displayTask;

    public CustomEnchantTableModule(Loader plugin, ToggleModule toggle, ItemCombineModule itemCombineModule) {
        this.plugin = plugin;
        this.toggle = toggle;
        this.store = new CustomEnchantTableStore(plugin.getConfigManager());
        this.itemKey = new NamespacedKey(plugin, "custom_enchant_table");
        this.displayKey = new NamespacedKey(plugin, "custom_enchant_table_display");
        this.registry = new CustomEnchantRegistry(plugin);

        // ===== エンチャント登録 (新規追加はここに1行足すだけ) =====
        AutoCollectEnchant autoCollect = new AutoCollectEnchant(plugin);
        registry.register(new MomentumEnchant(plugin));
        registry.register(new AreaBreakEnchant(plugin));
        registry.register(new SedimentBreakEnchant(plugin));
        registry.register(new BattleRepairEnchant(plugin));
        registry.register(autoCollect);
        registry.register(new AutoSmeltEnchant(plugin, autoCollect));
        registry.register(new MagnetEnchant(plugin));
        registry.register(new XpBoostEnchant(plugin));
        registry.register(new NightVisionEnchant(plugin));
        registry.register(new HasteEnchant(plugin));
        registry.register(new WisdomEnchant(plugin));
        registry.register(new LeafFortuneEnchant(plugin));
        registry.register(new AccelerateGrowthEnchant(plugin));
        registry.register(new FireproofBootsEnchant(plugin));
        registry.register(new HandOfThiefEnchant(plugin));
        registry.register(new VoidProtectionEnchant(plugin));
        registry.register(new VolleyShotEnchant(plugin));
        registry.register(new LongShotEnchant(plugin));
        registry.register(new VoidRescueEnchant(plugin));
        registry.register(new ChainResurrectEnchant(plugin, itemCombineModule));
        registry.register(new SniperEnchant(plugin));
        registry.register(new EnderChestEnchant(plugin));
        registry.register(new AqualungEnchant(plugin));
        registry.register(new HungerSaverEnchant(plugin));
        registry.register(new ThornlessEnchant(plugin));
        registry.register(new JumpBoostBootsEnchant(plugin));

        itemCombineModule.recipe("custom_enchant_table")
                .first(this::isPlainEnchantTable)
                .second(this::isLapis)
                .groundRadius(0.5D)
                .airRadius(1.5D)
                .verticalRadius(2.0D)
                .allowAirCombine(true)
                .then(this::craftTable);

        restoreTables();
        tickDisplays();
        displayTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickDisplays, 40L, 40L);
    }

    public CustomEnchantRegistry getRegistry() {
        return registry;
    }

    private boolean isFeatureEnabled() {
        return toggle.getGlobal(FEATURE_KEY);
    }

    // ================= クラフト =================

    private boolean isPlainEnchantTable(ItemStack stack) {
        return stack != null && stack.getType() == Material.ENCHANTING_TABLE && !isTableItem(stack);
    }

    private boolean isLapis(ItemStack stack) {
        return stack != null && stack.getType() == Material.LAPIS_LAZULI;
    }

    private boolean isTableItem(ItemStack stack) {
        if (stack == null || stack.getType() != Material.ENCHANTING_TABLE || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.STRING);
    }

    private ItemStack createTableItem() {
        ItemStack stack = new ItemStack(Material.ENCHANTING_TABLE);
        ItemMeta meta = stack.getItemMeta();
        ComponentUtils.setDisplayName(meta, ITEM_NAME);
        ComponentUtils.setLore(meta,
                "§7道具に特殊なエンチャントを付与できる",
                "§7設置して右クリックで使用",
                "§7採掘加速 / 範囲採掘 / 自動回収 など");
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "true");
        stack.setItemMeta(meta);
        return stack;
    }

    private void craftTable(ItemCombineModule.CombineMatch match) {
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
        world.dropItemNaturally(center, createTableItem());
        world.playSound(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0F, 0.8F);
        world.spawnParticle(Particle.ENCHANT, center.clone().add(0.0D, 0.5D, 0.0D), 40, 0.4D, 0.4D, 0.4D, 0.6D);
    }

    // ================= 設置 / 破壊 =================

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isTableItem(event.getItemInHand())) {
            return;
        }
        if (!isFeatureEnabled()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(PREFIX + "§cカスタムエンチャント機能は現在無効です");
            return;
        }
        Location location = event.getBlockPlaced().getLocation();
        tables.put(CustomEnchantTableStore.toKey(location), event.getPlayer().getUniqueId());
        store.save(location, event.getPlayer().getUniqueId());
        ensureDisplay(location);
        event.getPlayer().sendMessage(PREFIX + "§a設置しました。道具を持って右クリックで開きます");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String key = CustomEnchantTableStore.toKey(block.getLocation());
        if (!tables.containsKey(key)) {
            return;
        }
        event.setDropItems(false);
        removeTable(key, block.getLocation(), event.getPlayer().getGameMode() != GameMode.CREATIVE);
        event.getPlayer().sendMessage(PREFIX + "§eカスタムエンチャントテーブルを回収しました");
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
            String key = CustomEnchantTableStore.toKey(block.getLocation());
            if (tables.containsKey(key)) {
                removeTable(key, block.getLocation(), true);
            }
        }
    }

    private void removeTable(String key, Location location, boolean dropItem) {
        tables.remove(key);
        store.remove(location);
        removeDisplay(key);
        World world = location.getWorld();
        if (dropItem && world != null) {
            world.dropItemNaturally(location.clone().add(0.5D, 0.3D, 0.5D), createTableItem());
        }
    }

    private void restoreTables() {
        for (Map.Entry<Location, UUID> entry : store.loadAll().entrySet()) {
            Location location = entry.getKey();
            if (location.getWorld() == null) {
                continue;
            }
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            if (location.getWorld().isChunkLoaded(chunkX, chunkZ)
                    && location.getBlock().getType() != Material.ENCHANTING_TABLE) {
                store.remove(location);
                continue;
            }
            tables.put(CustomEnchantTableStore.toKey(location), entry.getValue());
        }
    }

    // ================= UI =================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !tables.containsKey(CustomEnchantTableStore.toKey(block.getLocation()))) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isSneaking() && !player.getInventory().getItemInMainHand().getType().isAir()) {
            return; // ブロック設置を優先
        }
        event.setCancelled(true);
        if (!isFeatureEnabled()) {
            player.sendMessage(PREFIX + "§cカスタムエンチャント機能は現在無効です");
            return;
        }
        CustomEnchantTableUI ui = new CustomEnchantTableUI(registry, player);
        // 手に対応する道具を持ってクリックした場合は、そのまま道具スロットへセット
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!hand.getType().isAir() && supportsAnyEnchant(hand.getType())) {
            ui.getInventory().setItem(CustomEnchantTableUI.SLOT_TOOL, hand.clone());
            player.getInventory().setItemInMainHand(null);
            ui.render();
        }
        openUis.put(player.getUniqueId(), ui);
        ui.open(player);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7F, 1.2F);
    }

    private boolean supportsAnyEnchant(Material type) {
        for (org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant enchant : registry.all()) {
            if (enchant.supports(type)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CustomEnchantTableUI ui)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < CustomEnchantTableUI.SIZE) {
            if (ui.isToolSlot(rawSlot)) {
                event.setCancelled(false);
                scheduleRender(ui);
                return;
            }
            event.setCancelled(true);
            ui.handleClick(player, rawSlot);
            return;
        }
        // 手持ち側のシフトクリック → 道具スロットへ
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
            ItemStack source = event.getCurrentItem();
            if (source == null || source.getType().isAir()) {
                return;
            }
            if (ui.toolItem() != null) {
                return; // スロット使用中
            }
            ui.getInventory().setItem(CustomEnchantTableUI.SLOT_TOOL, source.clone());
            org.bukkit.inventory.Inventory clicked = event.getClickedInventory();
            if (clicked != null) {
                clicked.setItem(event.getSlot(), null);
            }
            player.updateInventory();
            scheduleRender(ui);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CustomEnchantTableUI ui)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < CustomEnchantTableUI.SIZE && !ui.isToolSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
        scheduleRender(ui);
    }

    /** 道具スロットの変更が反映された後 (次tick) にボタンを再描画 */
    private void scheduleRender(CustomEnchantTableUI ui) {
        Bukkit.getScheduler().runTask(plugin, ui::render);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CustomEnchantTableUI ui)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        ui.returnTool(player);
        openUis.remove(player.getUniqueId(), ui);
    }

    // ================= 頭上ネームプレート =================

    private void tickDisplays() {
        for (Map.Entry<String, UUID> entry : new ArrayList<>(tables.entrySet())) {
            String key = entry.getKey();
            Location location = CustomEnchantTableStore.fromKey(key);
            if (location == null || location.getWorld() == null) {
                removeDisplay(key);
                continue;
            }
            World world = location.getWorld();
            if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                removeDisplay(key);
                continue;
            }
            if (location.getBlock().getType() != Material.ENCHANTING_TABLE) {
                // イベントを介さずブロックが消えた場合
                removeTable(key, location, false);
                continue;
            }
            ensureDisplay(location);
        }
    }

    private void ensureDisplay(Location location) {
        String key = CustomEnchantTableStore.toKey(location);
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
            if (display != null) {
                displayIds.put(key, display.getUniqueId());
            }
        }
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
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(location.clone().add(0.5D, 1.3D, 0.5D), 0.8D, 1.2D, 0.8D)) {
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
        Location spawn = location.clone().add(0.5D, 1.35D, 0.5D);
        return world.spawn(spawn, TextDisplay.class, display -> {
            display.text(ComponentUtils.legacy(DISPLAY_LABEL));
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

    // ================= 終了 =================

    public void shutdown() {
        displayTask.cancel();
        for (String key : new ArrayList<>(displayIds.keySet())) {
            removeDisplay(key);
        }
        for (Map.Entry<UUID, CustomEnchantTableUI> entry : new HashMap<>(openUis).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                entry.getValue().returnTool(player);
                player.closeInventory();
            }
        }
        openUis.clear();
        registry.shutdownAll();
    }
}
