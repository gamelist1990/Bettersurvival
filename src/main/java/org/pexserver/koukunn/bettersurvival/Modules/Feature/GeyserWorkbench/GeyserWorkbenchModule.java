package org.pexserver.koukunn.bettersurvival.Modules.Feature.GeyserWorkbench;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmithingTrimRecipe;
import org.bukkit.Registry;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;
import org.pexserver.koukunn.bettersurvival.Core.Util.FormsUtil;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class GeyserWorkbenchModule implements Listener {

    private static final String ANVIL_FEATURE_KEY = "geyseranvil";
    private static final String SMITHING_FEATURE_KEY = "geysersmithing";
    private static final String TYPE_ANVIL = "geyser_anvil";
    private static final String TYPE_SMITHING = "geyser_smithing";

    private static final String UI_TITLE_ANVIL = "§8金床";
    private static final String UI_TITLE_SMITHING = "§8鍛冶台";

    private static final int ANVIL_SLOT_INPUT_LEFT = 10;
    private static final int ANVIL_SLOT_INPUT_RIGHT = 12;
    private static final int ANVIL_SLOT_RESULT = 14;
    private static final int ANVIL_SLOT_INFO = 4;
    private static final int ANVIL_SLOT_NATIVE = 18;
    private static final int ANVIL_SLOT_RENAME = 22;
    private static final int ANVIL_SLOT_CLOSE = 26;

    private static final int SMITH_SLOT_TEMPLATE = 10;
    private static final int SMITH_SLOT_BASE = 11;
    private static final int SMITH_SLOT_ADDITION = 12;
    private static final int SMITH_SLOT_RESULT = 14;
    private static final int SMITH_SLOT_INFO = 4;
    private static final int SMITH_SLOT_NATIVE = 18;
    private static final int SMITH_SLOT_CLOSE = 26;

    private static final int ANVIL_MAX_NAME_LENGTH = 50;
    private static final int ANVIL_MAX_COST = 40;

    private static final Map<Material, TrimMaterial> TRIM_MATERIALS = new HashMap<>();

    static {
        TRIM_MATERIALS.put(Material.AMETHYST_SHARD, TrimMaterial.AMETHYST);
        TRIM_MATERIALS.put(Material.COPPER_INGOT, TrimMaterial.COPPER);
        TRIM_MATERIALS.put(Material.DIAMOND, TrimMaterial.DIAMOND);
        TRIM_MATERIALS.put(Material.EMERALD, TrimMaterial.EMERALD);
        TRIM_MATERIALS.put(Material.GOLD_INGOT, TrimMaterial.GOLD);
        TRIM_MATERIALS.put(Material.IRON_INGOT, TrimMaterial.IRON);
        TRIM_MATERIALS.put(Material.LAPIS_LAZULI, TrimMaterial.LAPIS);
        TRIM_MATERIALS.put(Material.NETHERITE_INGOT, TrimMaterial.NETHERITE);
        TRIM_MATERIALS.put(Material.QUARTZ, TrimMaterial.QUARTZ);
        TRIM_MATERIALS.put(Material.REDSTONE, TrimMaterial.REDSTONE);
        TRIM_MATERIALS.put(Material.RESIN_BRICK, TrimMaterial.RESIN);
    }

    private final Loader plugin;
    private final ToggleModule toggle;
    private final Map<UUID, AnvilSession> anvilSessions = new HashMap<>();
    private final Map<UUID, SmithingSession> smithingSessions = new HashMap<>();

    public GeyserWorkbenchModule(Loader plugin, ToggleModule toggle) {
        this.plugin = plugin;
        this.toggle = toggle;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (!FloodgateUtil.isBedrock(event.getPlayer())) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Material type = clicked.getType();
        if (isAnvil(type)) {
            if (!toggle.getGlobal(ANVIL_FEATURE_KEY)) return;
            event.setCancelled(true);
            openAnvilUI(event.getPlayer(), clicked.getLocation());
            return;
        }

        if (type == Material.SMITHING_TABLE) {
            if (!toggle.getGlobal(SMITHING_FEATURE_KEY)) return;
            event.setCancelled(true);
            openSmithingUI(event.getPlayer(), clicked.getLocation());
        }
    }

    @EventHandler(ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        String type = menuType(top);
        if (type == null) return;

        if (TYPE_ANVIL.equals(type) && event.getRawSlot() == ANVIL_SLOT_RESULT) {
            event.setCancelled(true);
            takeAnvilResult(player, top);
            return;
        }

        if (TYPE_SMITHING.equals(type) && event.getRawSlot() == SMITH_SLOT_RESULT) {
            event.setCancelled(true);
            takeSmithingResult(player, top);
            return;
        }

        scheduleRefresh(player);
    }

    @EventHandler(ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (menuType(top) == null) return;
        scheduleRefresh(player);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        String type = menuType(top);
        if (type == null) return;

        if (TYPE_ANVIL.equals(type)) {
            AnvilSession session = anvilSessions.get(player.getUniqueId());
            if (session != null && session.suppressReturnOnClose) {
                session.suppressReturnOnClose = false;
                return;
            }
            anvilSessions.remove(player.getUniqueId());
            returnInputToPlayer(player, top, ANVIL_SLOT_INPUT_LEFT, ANVIL_SLOT_INPUT_RIGHT);
            top.setItem(ANVIL_SLOT_RESULT, null);
            return;
        }

        if (TYPE_SMITHING.equals(type)) {
            smithingSessions.remove(player.getUniqueId());
            returnInputToPlayer(player, top, SMITH_SLOT_TEMPLATE, SMITH_SLOT_BASE, SMITH_SLOT_ADDITION);
            top.setItem(SMITH_SLOT_RESULT, null);
        }
    }

    private void openAnvilUI(Player player, Location sourceLocation) {
        openAnvilUI(player, sourceLocation, new AnvilSession(), null, null);
    }

    private void openAnvilUI(Player player, Location sourceLocation, AnvilSession session, ItemStack leftInput, ItemStack rightInput) {
        ChestUI.Builder builder = ChestUI.builder()
                .title(UI_TITLE_ANVIL)
                .size(27)
                .defaultIcon(Material.GRAY_STAINED_GLASS_PANE)
                .type(TYPE_ANVIL)
                .editableSlots(ANVIL_SLOT_INPUT_LEFT, ANVIL_SLOT_INPUT_RIGHT)
                .preserveInventoryOnTransition(true);

        decorateAnvilFrame(builder);
        builder.addButtonAt(ANVIL_SLOT_INFO, "§eコスト", Material.EXPERIENCE_BOTTLE, "§7必要レベル: 0");
        builder.addButtonAt(ANVIL_SLOT_RESULT, "§7結果", Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§7完成品");
        builder.addButtonAt(ANVIL_SLOT_NATIVE, "§6本家UI", Material.ANVIL, "§7バニラ金床UIを開く");
        builder.addButtonAt(ANVIL_SLOT_RENAME, "§b名前変更", Material.NAME_TAG, "§7クリックで名前を入力");
        builder.addButtonAt(ANVIL_SLOT_CLOSE, "§c閉じる", Material.BARRIER, "§7UIを閉じる");

        ChestUI.MenuHandler handler = builder.then((result, p) -> {
            if (result.slot == null) return;
            if (result.slot == ANVIL_SLOT_NATIVE) {
                openNativeAnvilUI(p);
                return;
            }
            if (result.slot == ANVIL_SLOT_RENAME) {
                openRenameInput(p);
                return;
            }
            if (result.slot == ANVIL_SLOT_CLOSE) {
                p.closeInventory();
            }
        });

        session.sourceLocation = sourceLocation;
        anvilSessions.put(player.getUniqueId(), session);
        handler.show(player);
        ChestUI menu = handler.getMenu();
        if (menu != null && (leftInput != null || rightInput != null)) {
            Inventory inventory = menu.getInventory();
            if (leftInput != null) {
                inventory.setItem(ANVIL_SLOT_INPUT_LEFT, leftInput.clone());
            }
            if (rightInput != null) {
                inventory.setItem(ANVIL_SLOT_INPUT_RIGHT, rightInput.clone());
            }
            refreshAnvil(player, inventory);
        }
        scheduleRefresh(player);
    }

    private void openSmithingUI(Player player, Location sourceLocation) {
        ChestUI.Builder builder = ChestUI.builder()
                .title(UI_TITLE_SMITHING)
                .size(27)
                .defaultIcon(Material.GRAY_STAINED_GLASS_PANE)
                .type(TYPE_SMITHING)
                .editableSlots(SMITH_SLOT_TEMPLATE, SMITH_SLOT_BASE, SMITH_SLOT_ADDITION)
                .preserveInventoryOnTransition(true);

        decorateSmithingFrame(builder);
        builder.addButtonAt(SMITH_SLOT_INFO, "§e状態", Material.PAPER, "§7鍛冶可能な組み合わせを入力");
        builder.addButtonAt(SMITH_SLOT_RESULT, "§7結果", Material.LIGHT_BLUE_STAINED_GLASS_PANE, "§7完成品");
        builder.addButtonAt(SMITH_SLOT_NATIVE, "§6本家UI", Material.SMITHING_TABLE, "§7バニラ鍛冶台UIを開く");
        builder.addButtonAt(SMITH_SLOT_CLOSE, "§c閉じる", Material.BARRIER, "§7UIを閉じる");

        ChestUI.MenuHandler handler = builder.then((result, p) -> {
            if (result.slot == null) return;
            if (result.slot == SMITH_SLOT_NATIVE) {
                openNativeSmithingUI(p);
                return;
            }
            if (result.slot == SMITH_SLOT_CLOSE) {
                p.closeInventory();
            }
        });

        SmithingSession session = new SmithingSession();
        session.sourceLocation = sourceLocation;
        smithingSessions.put(player.getUniqueId(), session);
        handler.show(player);
        scheduleRefresh(player);
    }

    private void openNativeAnvilUI(Player player) {
        AnvilSession session = anvilSessions.get(player.getUniqueId());
        Location location = session != null && session.sourceLocation != null ? session.sourceLocation : player.getLocation();
        player.openInventory(
                MenuType.ANVIL.builder()
                        .location(location)
                        .checkReachable(true)
                        .build(player)
        );
    }

    private void openNativeSmithingUI(Player player) {
        SmithingSession session = smithingSessions.get(player.getUniqueId());
        Location location = session != null && session.sourceLocation != null ? session.sourceLocation : player.getLocation();
        player.openInventory(
                MenuType.SMITHING.builder()
                        .location(location)
                        .checkReachable(true)
                        .build(player)
        );
    }

    private void openRenameInput(Player player) {
        AnvilSession session = anvilSessions.get(player.getUniqueId());
        if (session == null) return;
        Inventory top = player.getOpenInventory().getTopInventory();
        session.renameRestoreLeft = clean(top.getItem(ANVIL_SLOT_INPUT_LEFT));
        session.renameRestoreRight = clean(top.getItem(ANVIL_SLOT_INPUT_RIGHT));
        session.suppressReturnOnClose = true;
        String current = session.renameText == null ? "" : session.renameText;
        boolean opened = FormsUtil.openSingleInputForm(
                player,
                "金床: 名前変更",
                "新しい名前（空でリセット）",
                "最大50文字",
                current,
                input -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        session.suppressReturnOnClose = false;
                        if (!player.isOnline()) {
                            session.renameRestoreLeft = null;
                            session.renameRestoreRight = null;
                            return;
                        }
                        if (input != null) {
                            String filtered = filterName(input);
                            if (filtered.length() > ANVIL_MAX_NAME_LENGTH) {
                                player.sendMessage("§c名前は最大50文字です");
                                reopenAnvilAfterRename(player, session);
                                return;
                            }
                            session.renameText = filtered;
                        }
                        reopenAnvilAfterRename(player, session);
                    });
                }
        );
        if (!opened) {
            session.suppressReturnOnClose = false;
            session.renameRestoreLeft = null;
            session.renameRestoreRight = null;
        }
    }

    private void reopenAnvilAfterRename(Player player, AnvilSession session) {
        ItemStack left = session.renameRestoreLeft;
        ItemStack right = session.renameRestoreRight;
        session.renameRestoreLeft = null;
        session.renameRestoreRight = null;
        openAnvilUI(player, session.sourceLocation, session, left, right);
    }

    private void scheduleRefresh(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> refreshFor(player));
    }

    private void refreshFor(Player player) {
        if (player == null || !player.isOnline()) return;
        Inventory top = player.getOpenInventory().getTopInventory();
        String type = menuType(top);
        if (TYPE_ANVIL.equals(type)) {
            refreshAnvil(player, top);
            return;
        }
        if (TYPE_SMITHING.equals(type)) {
            refreshSmithing(top);
        }
    }

    private void refreshAnvil(Player player, Inventory inventory) {
        AnvilSession session = anvilSessions.computeIfAbsent(player.getUniqueId(), id -> new AnvilSession());
        ItemStack input = clean(inventory.getItem(ANVIL_SLOT_INPUT_LEFT));
        ItemStack addition = clean(inventory.getItem(ANVIL_SLOT_INPUT_RIGHT));

        AnvilComputation result = computeAnvil(player, input, addition, session.renameText);
        session.cost = result.cost;
        session.repairItemCountCost = result.repairItemCountCost;
        session.onlyRenaming = result.onlyRenaming;

        inventory.setItem(ANVIL_SLOT_RESULT, result.result != null ? result.result : buildResultPlaceholder());
        inventory.setItem(ANVIL_SLOT_INFO, buildAnvilInfoItem(session.cost, result.tooExpensive, result.hasResult));
        inventory.setItem(ANVIL_SLOT_RENAME, buildRenameItem(session.renameText));
    }

    private void refreshSmithing(Inventory inventory) {
        String type = menuType(inventory);
        if (!TYPE_SMITHING.equals(type)) return;
        Player viewer = null;
        if (!inventory.getViewers().isEmpty() && inventory.getViewers().get(0) instanceof Player p) {
            viewer = p;
        }
        ItemStack template = clean(inventory.getItem(SMITH_SLOT_TEMPLATE));
        ItemStack base = clean(inventory.getItem(SMITH_SLOT_BASE));
        ItemStack addition = clean(inventory.getItem(SMITH_SLOT_ADDITION));

        ItemStack result = computeSmithingResult(template, base, addition);
        boolean hasResult = result != null && !result.getType().isAir() && result.getAmount() > 0;
        if (viewer != null) {
            SmithingSession session = smithingSessions.computeIfAbsent(viewer.getUniqueId(), id -> new SmithingSession());
            session.hasResult = hasResult;
        }
        inventory.setItem(SMITH_SLOT_RESULT, hasResult ? result : buildResultPlaceholder());
        inventory.setItem(SMITH_SLOT_INFO, buildSmithingInfoItem(hasResult));
    }

    private void takeAnvilResult(Player player, Inventory inventory) {
        AnvilSession session = anvilSessions.get(player.getUniqueId());
        if (session == null) return;

        ItemStack result = clean(inventory.getItem(ANVIL_SLOT_RESULT));
        if (result == null) return;
        if (session.cost <= 0) return;
        if (player.getGameMode() != GameMode.CREATIVE && player.getLevel() < session.cost) return;

        if (player.getGameMode() != GameMode.CREATIVE) {
            player.setLevel(Math.max(0, player.getLevel() - session.cost));
        }

        ItemStack addition = clean(inventory.getItem(ANVIL_SLOT_INPUT_RIGHT));
        if (session.repairItemCountCost > 0 && addition != null) {
            int left = addition.getAmount() - session.repairItemCountCost;
            if (left > 0) {
                addition.setAmount(left);
                inventory.setItem(ANVIL_SLOT_INPUT_RIGHT, addition);
            } else {
                inventory.setItem(ANVIL_SLOT_INPUT_RIGHT, null);
            }
        } else if (!session.onlyRenaming) {
            inventory.setItem(ANVIL_SLOT_INPUT_RIGHT, null);
        }

        inventory.setItem(ANVIL_SLOT_INPUT_LEFT, null);
        inventory.setItem(ANVIL_SLOT_RESULT, null);
        giveOrDrop(player, result.clone());
        handleVanillaAnvilDamage(player, session.sourceLocation);

        session.cost = 0;
        session.repairItemCountCost = 0;
        session.onlyRenaming = false;
        scheduleRefresh(player);
    }

    private void handleVanillaAnvilDamage(Player player, Location sourceLocation) {
        if (player == null || sourceLocation == null) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;
        Block block = sourceLocation.getBlock();
        Material type = block.getType();
        if (!isAnvil(type)) return;

        if (ThreadLocalRandom.current().nextFloat() < 0.12f) {
            if (type == Material.ANVIL) {
                block.setType(Material.CHIPPED_ANVIL);
                block.getWorld().playSound(sourceLocation, Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                return;
            }
            if (type == Material.CHIPPED_ANVIL) {
                block.setType(Material.DAMAGED_ANVIL);
                block.getWorld().playSound(sourceLocation, Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                return;
            }
            if (type == Material.DAMAGED_ANVIL) {
                block.setType(Material.AIR);
                block.getWorld().playSound(sourceLocation, Sound.BLOCK_ANVIL_DESTROY, 1.0f, 1.0f);
                return;
            }
        }
        block.getWorld().playSound(sourceLocation, Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
    }

    private void takeSmithingResult(Player player, Inventory inventory) {
        SmithingSession session = smithingSessions.get(player.getUniqueId());
        if (session == null || !session.hasResult) return;
        ItemStack result = clean(inventory.getItem(SMITH_SLOT_RESULT));
        if (result == null) return;

        shrinkTopSlot(inventory, SMITH_SLOT_TEMPLATE, 1);
        shrinkTopSlot(inventory, SMITH_SLOT_BASE, 1);
        shrinkTopSlot(inventory, SMITH_SLOT_ADDITION, 1);
        inventory.setItem(SMITH_SLOT_RESULT, null);

        giveOrDrop(player, result.clone());
        scheduleRefresh(player);
    }

    private void returnInputToPlayer(Player player, Inventory inventory, int... slots) {
        for (int slot : slots) {
            ItemStack stack = clean(inventory.getItem(slot));
            if (stack == null) continue;
            inventory.setItem(slot, null);
            giveOrDrop(player, stack);
        }
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) return;
        Map<Integer, ItemStack> remains = player.getInventory().addItem(stack);
        if (remains.isEmpty()) return;
        for (ItemStack remain : remains.values()) {
            if (remain == null || remain.getType().isAir() || remain.getAmount() <= 0) continue;
            player.getWorld().dropItemNaturally(player.getLocation(), remain);
        }
    }

    private void shrinkTopSlot(Inventory inventory, int slot, int count) {
        ItemStack stack = clean(inventory.getItem(slot));
        if (stack == null) return;
        int next = stack.getAmount() - count;
        if (next <= 0) {
            inventory.setItem(slot, null);
            return;
        }
        stack.setAmount(next);
        inventory.setItem(slot, stack);
    }

    private ItemStack buildAnvilInfoItem(int cost, boolean tooExpensive, boolean hasResult) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        ComponentUtils.setDisplayName(meta, "§eコスト");
        if (!hasResult) {
            ComponentUtils.setLore(meta, "§7必要レベル: 0", "§8入力待ち");
        } else if (tooExpensive) {
            ComponentUtils.setLore(meta, "§c高すぎます", "§7必要レベル: " + cost);
        } else {
            ComponentUtils.setLore(meta, "§7必要レベル: " + cost);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildRenameItem(String name) {
        ItemStack item = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = item.getItemMeta();
        ComponentUtils.setDisplayName(meta, "§b名前変更");
        String current = (name == null || name.isBlank()) ? "§7(なし)" : "§f" + name;
        ComponentUtils.setLore(meta, "§7現在: " + current, "§7クリックで入力");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildSmithingInfoItem(boolean hasResult) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        ComponentUtils.setDisplayName(meta, "§e状態");
        if (hasResult) {
            ComponentUtils.setLore(meta, "§a作成可能");
        } else {
            ComponentUtils.setLore(meta, "§7レシピ不一致");
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildResultPlaceholder() {
        ItemStack item = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        ComponentUtils.setDisplayName(meta, "§7結果");
        ComponentUtils.setLore(meta, "§7条件を満たすと結果が表示されます");
        item.setItemMeta(meta);
        return item;
    }

    private void decorateAnvilFrame(ChestUI.Builder builder) {
        for (int slot = 0; slot < 27; slot++) {
            if (slot == ANVIL_SLOT_INPUT_LEFT || slot == ANVIL_SLOT_INPUT_RIGHT || slot == ANVIL_SLOT_RESULT
                    || slot == ANVIL_SLOT_INFO || slot == ANVIL_SLOT_NATIVE || slot == ANVIL_SLOT_RENAME || slot == ANVIL_SLOT_CLOSE) {
                continue;
            }
            builder.addButtonAt(slot, " ", Material.GRAY_STAINED_GLASS_PANE);
        }
        builder.addButtonAt(9, "§7対象", Material.CYAN_STAINED_GLASS_PANE, "§7修理/合成したいアイテム");
        builder.addButtonAt(11, "§8＋", Material.BLACK_STAINED_GLASS_PANE, "§7素材と組み合わせ");
        builder.addButtonAt(13, "§7素材", Material.CYAN_STAINED_GLASS_PANE, "§7修理素材 / エンチャ本");
        builder.addButtonAt(15, "§8→", Material.BLACK_STAINED_GLASS_PANE, "§7結果");
    }

    private void decorateSmithingFrame(ChestUI.Builder builder) {
        for (int slot = 0; slot < 27; slot++) {
            if (slot == SMITH_SLOT_TEMPLATE || slot == SMITH_SLOT_BASE || slot == SMITH_SLOT_ADDITION
                    || slot == SMITH_SLOT_RESULT || slot == SMITH_SLOT_INFO || slot == SMITH_SLOT_NATIVE || slot == SMITH_SLOT_CLOSE) {
                continue;
            }
            builder.addButtonAt(slot, " ", Material.GRAY_STAINED_GLASS_PANE);
        }
        builder.addButtonAt(9, "§7テンプレ", Material.CYAN_STAINED_GLASS_PANE, "§7鍛冶テンプレート");
        builder.addButtonAt(13, "§8→", Material.BLACK_STAINED_GLASS_PANE, "§7完成品");
        builder.addButtonAt(18, "§7ベース", Material.CYAN_STAINED_GLASS_PANE, "§7対象装備");
        builder.addButtonAt(19, "§7追加", Material.CYAN_STAINED_GLASS_PANE, "§7素材");
    }

    private AnvilComputation computeAnvil(Player player, ItemStack input, ItemStack addition, String requestedName) {
        if (input == null) {
            return AnvilComputation.empty();
        }

        ItemStack result = input.clone();
        Map<Enchantment, Integer> merged = new HashMap<>(extractEnchantments(result));
        int price = 0;
        long tax = getRepairCost(input) + (addition == null ? 0 : getRepairCost(addition));
        int repairItemCountCost = 0;
        int namingCost = 0;
        boolean onlyRenaming = false;

        if (addition != null) {
            boolean usingBook = isStoredEnchantmentItem(addition);
            if (isDamageable(result) && input.isRepairableBy(addition)) {
                int repairAmount = Math.min(getDamage(result), maxDamage(result) / 4);
                if (repairAmount <= 0) {
                    return AnvilComputation.empty();
                }
                int count = 0;
                while (repairAmount > 0 && count < addition.getAmount()) {
                    int nextDamage = getDamage(result) - repairAmount;
                    setDamage(result, Math.max(0, nextDamage));
                    price++;
                    count++;
                    repairAmount = Math.min(getDamage(result), maxDamage(result) / 4);
                }
                repairItemCountCost = count;
            } else {
                if (!(usingBook || (isDamageable(result) && result.getType() == addition.getType()))) {
                    return AnvilComputation.empty();
                }

                if (isDamageable(result) && !usingBook) {
                    int remaining1 = maxDamage(input) - getDamage(input);
                    int remaining2 = maxDamage(addition) - getDamage(addition);
                    int additional = remaining2 + maxDamage(result) * 12 / 100;
                    int remaining = remaining1 + additional;
                    int resultDamage = maxDamage(result) - remaining;
                    if (resultDamage < 0) {
                        resultDamage = 0;
                    }
                    if (resultDamage < getDamage(result)) {
                        setDamage(result, resultDamage);
                        price += 2;
                    }
                }

                Map<Enchantment, Integer> addEnchants = extractEnchantments(addition);
                boolean anyCompatible = false;
                boolean anyNotCompatible = false;

                for (Map.Entry<Enchantment, Integer> entry : addEnchants.entrySet()) {
                    Enchantment enchant = entry.getKey();
                    int current = merged.getOrDefault(enchant, 0);
                    int level = current == entry.getValue() ? entry.getValue() + 1 : Math.max(entry.getValue(), current);

                    boolean compatible = canApplyEnchantToItemStrict(input, enchant);
                    if (player.getGameMode() == GameMode.CREATIVE || input.getType() == Material.ENCHANTED_BOOK) {
                        compatible = true;
                    }

                    for (Enchantment other : merged.keySet()) {
                        if (other.equals(enchant) || !enchant.conflictsWith(other)) continue;
                        compatible = false;
                        price++;
                    }

                    if (!compatible) {
                        anyNotCompatible = true;
                        continue;
                    }

                    anyCompatible = true;
                    if (level > enchant.getMaxLevel()) {
                        level = enchant.getMaxLevel();
                    }
                    merged.put(enchant, level);

                    int fee = enchant.getAnvilCost();
                    if (usingBook) {
                        fee = Math.max(1, fee / 2);
                    }
                    price += fee * level;
                    if (input.getAmount() > 1) {
                        price = ANVIL_MAX_COST;
                    }
                }

                if (anyNotCompatible && !anyCompatible) {
                    return AnvilComputation.empty();
                }
            }
        }

        String normalizedName = requestedName == null ? "" : requestedName;
        String inputName = plainDisplayName(input);

        if (normalizedName.isBlank()) {
            if (hasCustomName(input)) {
                namingCost = 1;
                price += namingCost;
                clearCustomName(result);
            }
        } else if (!normalizedName.equals(inputName)) {
            namingCost = 1;
            price += namingCost;
            setCustomName(result, normalizedName);
        }

        int finalCost = price <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, tax + price);
        boolean tooExpensive = finalCost >= ANVIL_MAX_COST && player.getGameMode() != GameMode.CREATIVE;

        if (price <= 0) {
            return AnvilComputation.empty();
        }

        if (namingCost == price && namingCost > 0) {
            if (finalCost >= ANVIL_MAX_COST) {
                finalCost = ANVIL_MAX_COST - 1;
            }
            onlyRenaming = true;
            tooExpensive = false;
        }

        if (tooExpensive) {
            return new AnvilComputation(null, finalCost, repairItemCountCost, false, true, false);
        }

        int baseRepairCost = Math.max(getRepairCost(result), addition == null ? 0 : getRepairCost(addition));
        if (namingCost != price || namingCost == 0) {
            baseRepairCost = calculateIncreasedRepairCost(baseRepairCost);
        }
        setRepairCost(result, baseRepairCost);
        boolean allowAnyEnchant = player.getGameMode() == GameMode.CREATIVE || input.getType() == Material.ENCHANTED_BOOK;
        if (!allowAnyEnchant) {
            for (Enchantment enchantment : merged.keySet()) {
                if (canApplyEnchantToItemStrict(result, enchantment)) continue;
                return AnvilComputation.empty();
            }
        }
        applyEnchantments(result, merged, allowAnyEnchant);
        result.setAmount(1);

        return new AnvilComputation(result, finalCost, repairItemCountCost, onlyRenaming, false, true);
    }

    private ItemStack computeSmithingResult(ItemStack template, ItemStack base, ItemStack addition) {
        if (base == null) return null;

        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (!(recipe instanceof SmithingRecipe smithingRecipe)) continue;
            if (!matchesChoice(template, templateChoiceOf(smithingRecipe))) continue;
            if (!matchesChoice(base, smithingRecipe.getBase())) continue;
            if (!matchesChoice(addition, smithingRecipe.getAddition())) continue;

            if (smithingRecipe instanceof SmithingTrimRecipe trimRecipe) {
                ItemStack trimmed = applyTrim(base, addition, trimRecipe);
                if (trimmed != null) return trimmed;
                continue;
            }

            if (smithingRecipe instanceof SmithingTransformRecipe transformRecipe) {
                ItemStack transformed = applyTransform(base, transformRecipe);
                if (transformed != null) return transformed;
                continue;
            }

            ItemStack fallback = smithingRecipe.getResult().clone();
            if (!fallback.getType().isAir()) {
                fallback.setAmount(1);
                return fallback;
            }
        }
        return null;
    }

    private ItemStack applyTransform(ItemStack base, SmithingTransformRecipe recipe) {
        ItemStack resultTemplate = recipe.getResult().clone();
        if (resultTemplate.getType().isAir()) return null;

        if (!recipe.willCopyDataComponents()) {
            return resultTemplate;
        }

        ItemStack transformed = resultTemplate.clone();
        transformed.copyDataFrom(base, dataType -> true);
        return transformed;
    }

    private ItemStack applyTrim(ItemStack base, ItemStack addition, SmithingTrimRecipe recipe) {
        if (addition == null) return null;
        TrimMaterial material = addition.getData(DataComponentTypes.PROVIDES_TRIM_MATERIAL);
        if (material == null) {
            material = TRIM_MATERIALS.get(addition.getType());
        }
        if (material == null) return null;
        if (!(base.getItemMeta() instanceof ArmorMeta armorMeta)) return null;

        ArmorTrim trim = new ArmorTrim(material, recipe.getTrimPattern());
        if (Objects.equals(armorMeta.getTrim(), trim)) return null;

        ItemStack result = base.clone();
        result.setAmount(1);
        if (!(result.getItemMeta() instanceof ArmorMeta resultArmor)) return null;
        resultArmor.setTrim(trim);
        result.setItemMeta(resultArmor);
        return result;
    }

    private RecipeChoice templateChoiceOf(SmithingRecipe recipe) {
        if (recipe instanceof SmithingTrimRecipe trimRecipe) {
            return trimRecipe.getTemplate();
        }
        if (recipe instanceof SmithingTransformRecipe transformRecipe) {
            return transformRecipe.getTemplate();
        }
        return RecipeChoice.empty();
    }

    private boolean matchesChoice(ItemStack item, RecipeChoice choice) {
        if (isEmptyChoice(choice)) {
            return item == null || item.getType().isAir() || item.getAmount() <= 0;
        }
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            return false;
        }
        return choice.test(item);
    }

    private boolean isEmptyChoice(RecipeChoice choice) {
        return choice != null && choice.getClass().getSimpleName().equals("EmptyRecipeChoice");
    }

    private Map<Enchantment, Integer> extractEnchantments(ItemStack item) {
        Map<Enchantment, Integer> map = new HashMap<>();
        if (item == null || item.getType().isAir()) return map;

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta && item.getType() == Material.ENCHANTED_BOOK) {
            map.putAll(storageMeta.getStoredEnchants());
            return map;
        }

        map.putAll(item.getEnchantments());
        return map;
    }

    private void applyEnchantments(ItemStack item, Map<Enchantment, Integer> enchants, boolean allowAnyEnchant) {
        if (item == null || item.getType().isAir()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storageMeta && item.getType() == Material.ENCHANTED_BOOK) {
            for (Enchantment enchant : storageMeta.getStoredEnchants().keySet()) {
                storageMeta.removeStoredEnchant(enchant);
            }
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                storageMeta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
            }
            item.setItemMeta(storageMeta);
            return;
        }

        for (Enchantment enchant : item.getEnchantments().keySet()) {
            item.removeEnchantment(enchant);
        }
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            Enchantment enchantment = entry.getKey();
            int level = entry.getValue();
            boolean canApply = canApplyEnchantToItemStrict(item, enchantment);
            if (!allowAnyEnchant && !canApply) {
                continue;
            }
            if (canApply) {
                item.addEnchantment(enchantment, level);
            } else {
                item.addUnsafeEnchantment(enchantment, level);
            }
        }
    }

    private boolean isStoredEnchantmentItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta instanceof EnchantmentStorageMeta storageMeta && !storageMeta.getStoredEnchants().isEmpty();
    }

    private boolean canApplyEnchantToItemStrict(ItemStack item, Enchantment enchantment) {
        if (item == null || item.getType().isAir() || enchantment == null) return false;
        if (item.getType() == Material.ENCHANTED_BOOK) return true;

        ItemType itemType = item.getType().asItemType();
        if (itemType == null) return false;
        if (!enchantment.getSupportedItems().resolve(Registry.ITEM).contains(itemType)) return false;

        ItemStack probe = new ItemStack(item.getType(), 1);
        return enchantment.canEnchantItem(probe);
    }

    private boolean hasCustomName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.hasDisplayName();
    }

    private void clearCustomName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.displayName(null);
        item.setItemMeta(meta);
    }

    private void setCustomName(ItemStack item, String name) {
        if (item == null || name == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
    }

    private String plainDisplayName(ItemStack item) {
        if (item == null || item.getType().isAir()) return "";
        Component component = item.displayName();
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private String filterName(String raw) {
        if (raw == null) return "";
        StringBuilder filtered = new StringBuilder();
        raw.codePoints().forEach(cp -> {
            if (cp >= 0x20 && cp != 0x7F) {
                filtered.appendCodePoint(cp);
            }
        });
        return filtered.toString().trim();
    }

    private int calculateIncreasedRepairCost(int baseCost) {
        long next = baseCost * 2L + 1L;
        return next > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) next;
    }

    private int getRepairCost(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;
        Integer repairCost = item.getData(DataComponentTypes.REPAIR_COST);
        if (repairCost == null) return 0;
        return Math.max(0, repairCost);
    }

    private void setRepairCost(ItemStack item, int repairCost) {
        if (item == null || item.getType().isAir()) return;
        item.setData(DataComponentTypes.REPAIR_COST, Math.max(0, repairCost));
    }

    private boolean isDamageable(ItemStack stack) {
        return stack != null && stack.getType().getMaxDurability() > 0;
    }

    private int maxDamage(ItemStack stack) {
        return stack.getType().getMaxDurability();
    }

    private int getDamage(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return 0;
        return Math.max(0, damageable.getDamage());
    }

    private void setDamage(ItemStack stack, int damage) {
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return;
        damageable.setDamage(Math.max(0, damage));
        stack.setItemMeta((ItemMeta) damageable);
    }

    private ItemStack clean(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) return null;
        return item.clone();
    }

    private String menuType(Inventory inventory) {
        if (inventory == null) return null;
        if (!(inventory.getHolder() instanceof ChestUI chestUI)) return null;
        String type = chestUI.getType();
        if (TYPE_ANVIL.equals(type) || TYPE_SMITHING.equals(type)) {
            return type;
        }
        return null;
    }

    private boolean isAnvil(Material type) {
        return type == Material.ANVIL || type == Material.CHIPPED_ANVIL || type == Material.DAMAGED_ANVIL;
    }

    private static final class AnvilSession {
        private String renameText = "";
        private int repairItemCountCost = 0;
        private int cost = 0;
        private boolean onlyRenaming = false;
        private boolean suppressReturnOnClose = false;
        private ItemStack renameRestoreLeft;
        private ItemStack renameRestoreRight;
        private Location sourceLocation;
    }

    private static final class SmithingSession {
        private boolean hasResult = false;
        private Location sourceLocation;
    }

    private record AnvilComputation(
            ItemStack result,
            int cost,
            int repairItemCountCost,
            boolean onlyRenaming,
            boolean tooExpensive,
            boolean hasResult
    ) {
        private static AnvilComputation empty() {
            return new AnvilComputation(null, 0, 0, false, false, false);
        }
    }
}
