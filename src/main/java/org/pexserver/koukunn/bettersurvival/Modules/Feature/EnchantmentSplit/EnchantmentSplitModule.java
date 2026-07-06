package org.pexserver.koukunn.bettersurvival.Modules.Feature.EnchantmentSplit;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
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
import org.pexserver.koukunn.bettersurvival.Modules.ItemCombineModule;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 複数エンチャント付きの本を分離するための専用砥石。
 *
 * grindstone と複数エンチャント付きのエンチャント本を同じ位置に投げると
 * エンチャント分離砥石アイテムへ変換される。
 *
 * 設置後は専用 UI から
 * 1. 複数エンチャント付きの本を入れる
 * 2. 分離したいエンチャントを選ぶ
 * 3. 素材の本 1 冊と必要経験レベルを消費して単体本へ分離する
 *
 * 必要経験レベルは Enchantment#getAnvilCost を基準に
 * 本ベースのため ceil(cost * level / 2) としている。
 */
public class EnchantmentSplitModule implements Listener {

    private static final String FEATURE_KEY = "enchantsplit";
    private static final String DISPLAY_NAME = "§bエンチャント分離砥石";
    private static final String UI_TITLE = "§8エンチャント分離";
    private static final String DISPLAY_LABEL = "§bエンチャント分離";
    private static final int DISPLAY_RADIUS_SQUARED = 16 * 16;
    private static final int SOURCE_SLOT = 10;
    private static final int MATERIAL_SLOT = 16;
    private static final int INFO_SLOT = 13;
    private static final int COST_SLOT = 22;
    private static final int CONFIRM_SLOT = 49;
    private static final int CLOSE_SLOT = 53;
    private static final int[] ENCHANT_SLOTS = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final Loader plugin;
    private final ToggleModule toggle;
    private final EnchantmentSplitStore store;
    private final NamespacedKey itemKey;
    private final NamespacedKey displayKey;
    private final Map<String, Location> grindstones = new LinkedHashMap<>();
    private final Map<String, UUID> displayIds = new LinkedHashMap<>();
    private final BukkitTask displayTask;

    public EnchantmentSplitModule(Loader plugin, ToggleModule toggle, ItemCombineModule itemCombineModule) {
        this.plugin = plugin;
        this.toggle = toggle;
        this.store = new EnchantmentSplitStore(plugin.getConfigManager());
        this.itemKey = new NamespacedKey(plugin, "enchantment_split_grindstone");
        this.displayKey = new NamespacedKey(plugin, "enchantment_split_display");
        for (Location location : store.loadAll()) {
            grindstones.put(EnchantmentSplitStore.toKey(location), location);
        }
        this.displayTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickDisplays, 40L, 40L);
        itemCombineModule.recipe("enchantment_split_grindstone")
                .first(this::isPlainGrindstoneItem)
                .second(this::isValidSourceBook)
                .groundRadius(0.5D)
                .airRadius(1.5D)
                .verticalRadius(2.0D)
                .allowAirCombine(true)
                .then(this::craftCustomGrindstone);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack stack = event.getItemInHand();
        if (!isCustomGrindstoneItem(stack))
            return;
        Location location = event.getBlockPlaced().getLocation();
        String key = EnchantmentSplitStore.toKey(location);
        grindstones.put(key, location);
        store.save(location);
        ensureDisplayState(location);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String key = EnchantmentSplitStore.toKey(block.getLocation());
        Location location = grindstones.remove(key);
        if (location == null)
            return;
        store.remove(location);
        removeDisplay(key);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 0.1D, 0.5D), createCustomGrindstoneItem());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || clicked.getType() != Material.GRINDSTONE)
            return;
        Location location = clicked.getLocation();
        if (!grindstones.containsKey(EnchantmentSplitStore.toKey(location)))
            return;
        event.setCancelled(true);
        if (!toggle.getGlobal(FEATURE_KEY)) {
            event.getPlayer().sendMessage("§cエンチャント分離機能は現在無効です");
            return;
        }
        openUI(event.getPlayer(), location);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;
        if (!(event.getView().getTopInventory().getHolder() instanceof SplitHolder holder))
            return;

        Inventory top = event.getView().getTopInventory();
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0)
            return;

        if (rawSlot < top.getSize()) {
            if (rawSlot == SOURCE_SLOT || rawSlot == MATERIAL_SLOT) {
                if (!canUseInputSlot(event, holder, rawSlot)) {
                    event.setCancelled(true);
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> refreshUI(player, holder));
                return;
            }

            event.setCancelled(true);
            if (rawSlot == CLOSE_SLOT) {
                player.closeInventory();
                return;
            }
            if (rawSlot == CONFIRM_SLOT) {
                handleConfirm(player, holder);
                return;
            }
            if (isEnchantChoiceSlot(rawSlot)) {
                String selected = holder.slotSelections.get(rawSlot);
                if (selected != null) {
                    holder.selectedEnchantKey = selected;
                    refreshUI(player, holder);
                }
            }
            return;
        }

        if (event.isShiftClick()) {
            ItemStack current = event.getCurrentItem();
            if (current == null || current.getType() == Material.AIR)
                return;
            if (isValidSourceBook(current) && isSlotEmpty(top, SOURCE_SLOT)) {
                event.setCancelled(true);
                top.setItem(SOURCE_SLOT, current.clone());
                event.getClickedInventory().setItem(event.getSlot(), null);
                refreshUI(player, holder);
                return;
            }
            if (isMaterialBook(current) && isSlotEmpty(top, MATERIAL_SLOT)) {
                event.setCancelled(true);
                ItemStack moved = current.clone();
                int amount = moved.getAmount();
                top.setItem(MATERIAL_SLOT, moved);
                event.getClickedInventory().setItem(event.getSlot(), null);
                if (amount <= 0)
                    top.setItem(MATERIAL_SLOT, null);
                refreshUI(player, holder);
            }
        }
    }

    @EventHandler(ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SplitHolder))
            return;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player))
            return;
        if (!(event.getInventory().getHolder() instanceof SplitHolder))
            return;
        returnInputItem(player, event.getInventory().getItem(SOURCE_SLOT));
        returnInputItem(player, event.getInventory().getItem(MATERIAL_SLOT));
        event.getInventory().setItem(SOURCE_SLOT, null);
        event.getInventory().setItem(MATERIAL_SLOT, null);
    }

    private void craftCustomGrindstone(ItemCombineModule.CombineMatch match) {
        if (!toggle.getGlobal(FEATURE_KEY))
            return;
        if (!match.first().isValid() || !match.second().isValid())
            return;
        Location center = match.center();
        if (center.getWorld() == null)
            return;
        match.consumeMatchedItems(1, 1);
        center.getWorld().dropItemNaturally(center, createCustomGrindstoneItem());
        center.getWorld().playSound(center, Sound.BLOCK_GRINDSTONE_USE, 1.0F, 0.8F);
    }

    private ItemStack createCustomGrindstoneItem() {
        ItemStack stack = new ItemStack(Material.GRINDSTONE);
        ItemMeta meta = stack.getItemMeta();
        ComponentUtils.setDisplayName(meta, DISPLAY_NAME);
        ComponentUtils.setLore(meta,
                "§7複数エンチャント本を分離できる砥石",
                "§7右クリックで専用UIを開く");
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "true");
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isCustomGrindstoneItem(ItemStack stack) {
        if (stack == null || stack.getType() != Material.GRINDSTONE || !stack.hasItemMeta())
            return false;
        return stack.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.STRING);
    }

    private boolean isPlainGrindstoneItem(ItemStack stack) {
        return stack != null && stack.getType() == Material.GRINDSTONE && !isCustomGrindstoneItem(stack);
    }

    private boolean isValidSourceBook(ItemStack stack) {
        if (stack == null || stack.getType() != Material.ENCHANTED_BOOK || !(stack.getItemMeta() instanceof EnchantmentStorageMeta meta))
            return false;
        return meta.getStoredEnchants().size() > 1;
    }

    private boolean isMaterialBook(ItemStack stack) {
        return stack != null && stack.getType() == Material.BOOK;
    }

    private void openUI(Player player, Location location) {
        SplitHolder holder = new SplitHolder(location);
        Inventory inventory = ComponentUtils.createInventory(holder, 54, UI_TITLE);
        holder.inventory = inventory;
        refreshUI(player, holder);
        player.openInventory(inventory);
    }

    private void refreshUI(Player player, SplitHolder holder) {
        Inventory inventory = holder.inventory;
        ItemStack source = inventory.getItem(SOURCE_SLOT);
        ItemStack material = inventory.getItem(MATERIAL_SLOT);
        inventory.clear();
        fillBackground(inventory);
        inventory.setItem(SOURCE_SLOT, source);
        inventory.setItem(MATERIAL_SLOT, material);
        inventory.setItem(9, createInfoItem(Material.PAPER, "§bStep 1", "§7複数エンチャント付きの本を入れる"));
        inventory.setItem(17, createInfoItem(Material.PAPER, "§bStep 2", "§7素材の本を入れる"));
        inventory.setItem(CLOSE_SLOT, createInfoItem(Material.BARRIER, "§c閉じる", "§7中のアイテムは返却されます"));

        if (!isValidSourceBook(source)) {
            holder.selectedEnchantKey = null;
            holder.slotSelections.clear();
            inventory.setItem(INFO_SLOT, createInfoItem(Material.BARRIER, "§c複数エンチャント本を入れてください",
                    "§7エンチャントが2個以上付いた本だけ分離できます"));
            inventory.setItem(CONFIRM_SLOT, createInfoItem(Material.GRAY_DYE, "§7分離待機中", ""));
            return;
        }

        EnchantmentStorageMeta sourceMeta = (EnchantmentStorageMeta) source.getItemMeta();
        List<Map.Entry<Enchantment, Integer>> enchants = new ArrayList<>(sourceMeta.getStoredEnchants().entrySet());
        enchants.sort(Comparator.comparing(entry -> entry.getKey().getKey().toString()));
        if (holder.selectedEnchantKey == null)
            holder.selectedEnchantKey = enchants.get(0).getKey().getKey().toString();
        Set<String> validKeys = new LinkedHashSet<>();
        holder.slotSelections.clear();

        int index = 0;
        for (Map.Entry<Enchantment, Integer> entry : enchants) {
            if (index >= ENCHANT_SLOTS.length)
                break;
            Enchantment enchantment = entry.getKey();
            int level = entry.getValue();
            String key = enchantment.getKey().toString();
            validKeys.add(key);
            int slot = ENCHANT_SLOTS[index++];
            holder.slotSelections.put(slot, key);
            inventory.setItem(slot, createEnchantOption(enchantment, level, key.equals(holder.selectedEnchantKey), getRequiredLevels(enchantment, level)));
        }

        if (!validKeys.contains(holder.selectedEnchantKey))
            holder.selectedEnchantKey = enchants.get(0).getKey().getKey().toString();

        SelectionContext selection = getSelectionContext(sourceMeta, holder.selectedEnchantKey);
        if (selection == null) {
            inventory.setItem(INFO_SLOT, createInfoItem(Material.BARRIER, "§c分離対象を選択してください", ""));
            inventory.setItem(CONFIRM_SLOT, createInfoItem(Material.GRAY_DYE, "§7分離待機中", ""));
            return;
        }

        int cost = getRequiredLevels(selection.enchantment, selection.level);
        boolean hasMaterial = isMaterialBook(material);
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        boolean hasLevels = creative || player.getLevel() >= cost;

        inventory.setItem(INFO_SLOT, createInfoItem(Material.PAPER, "§b使い方",
                "§7下の候補から分離したいエンチャントを選ぶ",
                "§7素材の本と経験レベルを消費して分離"));
        inventory.setItem(COST_SLOT, createInfoItem(hasLevels ? Material.EXPERIENCE_BOTTLE : Material.BARRIER,
                creative ? "§e必要レベル: 0 (Creative)" : "§e必要レベル: " + cost,
                creative ? "§7Creativeでは経験値消費なし" : "§7現在のレベル: " + player.getLevel()));
        if (!hasMaterial) {
            inventory.setItem(CONFIRM_SLOT, createInfoItem(Material.BOOK, "§c素材の本が必要です", "§7Step 2 に通常の本を入れてください"));
            return;
        }
        if (!hasLevels) {
            inventory.setItem(CONFIRM_SLOT, createInfoItem(Material.BARRIER, "§c経験レベルが足りません",
                    "§7現在選択しているもの:",
                    "§f- " + getEnchantDisplayText(player, selection.enchantment, selection.level),
                    "§7必要レベル: " + cost,
                    "§7分離後に残る本:",
                    "§f- " + getRemainingEnchantSummary(player, source, selection.enchantment)));
            return;
        }
        inventory.setItem(CONFIRM_SLOT, createInfoItem(Material.LIME_WOOL, "§a分離する",
                "§7現在選択しているもの:",
                "§f- " + getEnchantDisplayText(player, selection.enchantment, selection.level),
                creative ? "§7Creativeではコスト消費なし" : "§7本 1 冊 と レベル " + cost + " を消費",
                "§7分離後に残る本:",
                "§f- " + getRemainingEnchantSummary(player, source, selection.enchantment)));
    }

    private void fillBackground(Inventory inventory) {
        ItemStack pane = createInfoItem(Material.GRAY_STAINED_GLASS_PANE, " ", "");
        for (int slot = 0; slot < inventory.getSize(); slot++)
            inventory.setItem(slot, pane);
        for (int slot : ENCHANT_SLOTS)
            inventory.setItem(slot, null);
        inventory.setItem(SOURCE_SLOT, null);
        inventory.setItem(MATERIAL_SLOT, null);
    }

    private ItemStack createInfoItem(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        ComponentUtils.setDisplayName(meta, name);
        if (lore != null && lore.length > 0)
            ComponentUtils.setLore(meta, Arrays.asList(lore));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createEnchantOption(Enchantment enchantment, int level, boolean selected, int cost) {
        ItemStack stack = createSingleEnchantBook(enchantment, level);
        ItemMeta meta = stack.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add(selected ? "§a現在選択中" : "§7クリックで選択");
        lore.add("§7必要レベル: §e" + cost);
        ComponentUtils.setLore(meta, lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack createSingleEnchantBook(Enchantment enchantment, int level) {
        ItemStack stack = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) stack.getItemMeta();
        meta.addStoredEnchant(enchantment, level, true);
        stack.setItemMeta(meta);
        return stack;
    }

    private String getRemainingEnchantSummary(Player player, ItemStack source, Enchantment removed) {
        if (!(source.getItemMeta() instanceof EnchantmentStorageMeta meta))
            return "なし";
        List<String> names = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : meta.getStoredEnchants().entrySet()) {
            if (entry.getKey().equals(removed))
                continue;
            names.add(getEnchantDisplayText(player, entry.getKey(), entry.getValue()));
        }
        if (names.isEmpty())
            return "なし";
        return String.join(" / ", names);
    }

    private String getEnchantDisplayText(Player player, Enchantment enchantment, int level) {
        return ItemNameUtil.localizedPlainText(enchantment.displayName(level), player.locale());
    }

    private boolean canUseInputSlot(InventoryClickEvent event, SplitHolder holder, int rawSlot) {
        Inventory top = holder.inventory;
        ItemStack current = top.getItem(rawSlot);
        ItemStack cursor = event.getCursor();
        ItemStack hotbarItem = event.getClick().isKeyboardClick() ? event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) : null;

        if (event.isShiftClick())
            return true;
        if (cursor != null && cursor.getType() != Material.AIR)
            return rawSlot == SOURCE_SLOT ? isValidSourceBook(cursor) : isMaterialBook(cursor);
        if (hotbarItem != null && hotbarItem.getType() != Material.AIR)
            return rawSlot == SOURCE_SLOT ? isValidSourceBook(hotbarItem) : isMaterialBook(hotbarItem);
        return current != null || cursor == null || cursor.getType() == Material.AIR;
    }

    private boolean isSlotEmpty(Inventory inventory, int slot) {
        ItemStack stack = inventory.getItem(slot);
        return stack == null || stack.getType() == Material.AIR;
    }

    private boolean isEnchantChoiceSlot(int slot) {
        for (int choiceSlot : ENCHANT_SLOTS) {
            if (choiceSlot == slot)
                return true;
        }
        return false;
    }

    private void handleConfirm(Player player, SplitHolder holder) {
        Inventory inventory = holder.inventory;
        ItemStack source = inventory.getItem(SOURCE_SLOT);
        ItemStack material = inventory.getItem(MATERIAL_SLOT);
        if (!isValidSourceBook(source)) {
            refreshUI(player, holder);
            return;
        }
        if (!isMaterialBook(material)) {
            refreshUI(player, holder);
            return;
        }
        EnchantmentStorageMeta sourceMeta = (EnchantmentStorageMeta) source.getItemMeta();
        SelectionContext selection = getSelectionContext(sourceMeta, holder.selectedEnchantKey);
        if (selection == null) {
            refreshUI(player, holder);
            return;
        }
        boolean creative = player.getGameMode() == GameMode.CREATIVE;
        int cost = getRequiredLevels(selection.enchantment, selection.level);
        if (!creative && player.getLevel() < cost) {
            refreshUI(player, holder);
            return;
        }

        EnchantmentStorageMeta updatedMeta = (EnchantmentStorageMeta) source.getItemMeta();
        updatedMeta.removeStoredEnchant(selection.enchantment);
        source.setItemMeta(updatedMeta);

        if (material.getAmount() <= 1)
            inventory.setItem(MATERIAL_SLOT, null);
        else
            material.setAmount(material.getAmount() - 1);

        if (!creative)
            player.giveExpLevels(-cost);
        giveOrDrop(player, createSingleEnchantBook(selection.enchantment, selection.level));
        player.playSound(player.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0F, 1.2F);
        player.sendMessage("§a" + getEnchantDisplayText(player, selection.enchantment, selection.level) + " を分離しました");

        if (!(source.getItemMeta() instanceof EnchantmentStorageMeta afterMeta) || afterMeta.getStoredEnchants().size() <= 1)
            holder.selectedEnchantKey = null;
        refreshUI(player, holder);
    }

    private SelectionContext getSelectionContext(EnchantmentStorageMeta meta, String selectedKey) {
        if (selectedKey == null)
            return null;
        for (Map.Entry<Enchantment, Integer> entry : meta.getStoredEnchants().entrySet()) {
            if (entry.getKey().getKey().toString().equals(selectedKey))
                return new SelectionContext(entry.getKey(), entry.getValue());
        }
        return null;
    }

    private int getRequiredLevels(Enchantment enchantment, int level) {
        return Math.max(1, (int) Math.ceil(enchantment.getAnvilCost() * level / 2.0D));
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack);
        for (ItemStack leftover : leftovers.values())
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
    }

    private void returnInputItem(Player player, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR)
            return;
        giveOrDrop(player, stack);
    }

    private void tickDisplays() {
        List<String> removed = new ArrayList<>();
        for (Map.Entry<String, Location> entry : grindstones.entrySet()) {
            String key = entry.getKey();
            Location location = entry.getValue();
            if (location.getWorld() == null || location.getBlock().getType() != Material.GRINDSTONE) {
                removeDisplay(key);
                store.remove(location);
                removed.add(key);
                continue;
            }
            ensureDisplayState(location);
        }
        for (String key : removed)
            grindstones.remove(key);
    }

    private void ensureDisplayState(Location location) {
        String key = EnchantmentSplitStore.toKey(location);
        if (!hasNearbyPlayer(location)) {
            removeDisplay(key);
            return;
        }
        TextDisplay display = getTrackedDisplay(key);
        if (display == null) {
            display = findNearbyDisplay(location, key);
            if (display == null)
                display = spawnDisplay(location, key);
            displayIds.put(key, display.getUniqueId());
        }
    }

    private boolean hasNearbyPlayer(Location location) {
        if (location.getWorld() == null)
            return false;
        for (Player player : location.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= DISPLAY_RADIUS_SQUARED)
                return true;
        }
        return false;
    }

    private TextDisplay getTrackedDisplay(String key) {
        UUID uuid = displayIds.get(key);
        if (uuid == null)
            return null;
        if (!(Bukkit.getEntity(uuid) instanceof TextDisplay display) || !display.isValid()) {
            displayIds.remove(key);
            return null;
        }
        return display;
    }

    private TextDisplay findNearbyDisplay(Location location, String key) {
        for (org.bukkit.entity.Entity entity : location.getWorld().getNearbyEntities(location.clone().add(0.5D, 1.1D, 0.5D), 0.8D, 1.2D, 0.8D)) {
            if (!(entity instanceof TextDisplay display))
                continue;
            PersistentDataContainer container = display.getPersistentDataContainer();
            String stored = container.get(displayKey, PersistentDataType.STRING);
            if (key.equals(stored))
                return display;
        }
        return null;
    }

    private TextDisplay spawnDisplay(Location location, String key) {
        Location spawn = location.clone().add(0.5D, 1.15D, 0.5D);
        return location.getWorld().spawn(spawn, TextDisplay.class, display -> {
            display.text(ComponentUtils.legacy(DISPLAY_LABEL));
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(false);
            display.setShadowed(false);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.9F, 0.9F, 0.9F), new AxisAngle4f()));
            display.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, key);
        });
    }

    private void removeDisplay(String key) {
        TextDisplay display = getTrackedDisplay(key);
        if (display != null)
            display.remove();
        displayIds.remove(key);
    }

    public void shutdown() {
        displayTask.cancel();
        for (String key : new ArrayList<>(displayIds.keySet()))
            removeDisplay(key);
    }

    private static class SplitHolder implements InventoryHolder {
        private final Map<Integer, String> slotSelections = new LinkedHashMap<>();
        private Inventory inventory;
        private String selectedEnchantKey;

        private SplitHolder(Location location) {
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static class SelectionContext {
        private final Enchantment enchantment;
        private final int level;

        private SelectionContext(Enchantment enchantment, int level) {
            this.enchantment = enchantment;
            this.level = level;
        }
    }
}
