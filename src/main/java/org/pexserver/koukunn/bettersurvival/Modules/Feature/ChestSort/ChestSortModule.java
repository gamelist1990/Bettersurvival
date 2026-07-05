package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestSort;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock.ChestLockModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopModule;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * ChestSort: スニークしながら専用の木の棒でチェスト群を選択し、一括整理します。
 *
 * 左クリック: チェスト選択 / 選択解除
 * 右クリック: 選択済みチェスト群をまとめて整理
 */
@SuppressWarnings("deprecation")
public class ChestSortModule implements Listener {

    private static final String FEATURE_KEY = "chestsort";
    private static final long OUTLINE_PERIOD_TICKS = 20L;

    private final ToggleModule toggle;
    private final ChestLockModule chestLock;
    private final ChestShopModule chestShop;
    private final Map<UUID, LinkedHashMap<String, SelectedContainer>> selections = new LinkedHashMap<>();
    private final BukkitTask outlineTask;

    public ChestSortModule(Loader plugin, ToggleModule toggle, ChestLockModule chestLock, ChestShopModule chestShop) {
        this.toggle = toggle;
        this.chestLock = chestLock;
        this.chestShop = chestShop;
        this.outlineTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSelectionOutlines, OUTLINE_PERIOD_TICKS, OUTLINE_PERIOD_TICKS);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK)
            return;
        if (event.getHand() != EquipmentSlot.HAND)
            return;
        Player player = event.getPlayer();
        if (!player.isSneaking())
            return;
        if (!isSortStick(event.getItem()))
            return;

        Block clicked = event.getClickedBlock();
        if (clicked == null || !isSupportedContainer(clicked))
            return;
        if (!toggle.getGlobal(FEATURE_KEY) || !toggle.isEnabledFor(player.getUniqueId().toString(), FEATURE_KEY))
            return;
        if (!canUseContainer(player, clicked.getLocation()))
            return;

        event.setCancelled(true);
        if (action == Action.LEFT_CLICK_BLOCK) {
            toggleSelection(player, clicked);
            return;
        }

        executeSort(player, clicked);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        selections.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isSupportedContainer(block))
            return;
        clearSelectionsByBrokenBlock(block.getLocation());
    }

    public void shutdown() {
        outlineTask.cancel();
        selections.clear();
    }

    private boolean isSortStick(ItemStack stack) {
        if (stack == null || stack.getType() != Material.STICK || !stack.hasItemMeta())
            return false;
        ItemMeta meta = stack.getItemMeta();
        if (!meta.hasDisplayName())
            return false;
        String displayName = meta.getDisplayName();
        if (displayName == null)
            return false;
        String lower = displayName.toLowerCase(Locale.ROOT);
        return lower.equals("sort") || lower.equals("整理") || lower.equals("整列");
    }

    private boolean isSupportedContainer(Block block) {
        Material type = block.getType();
        if (!isSupportedContainerType(type))
            return false;
        return block.getState() instanceof InventoryHolder;
    }

    private boolean isSupportedContainerType(Material type) {
        if (type == null)
            return false;
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST || type == Material.BARREL)
            return true;
        String name = type.name();
        if (name.endsWith("_SHULKER_BOX"))
            return true;
        return name.contains("COPPER_CHEST");
    }

    private boolean canUseContainer(Player player, Location location) {
        if (location == null)
            return false;
        if (chestShop != null && chestShop.isShopChest(location)) {
            player.sendMessage("§cこのチェストはショップに紐づいているため整理できません");
            return false;
        }
        if (chestLock != null && !chestLock.canAccess(player, location)) {
            player.sendMessage("§cこのチェストは保護されているため整理できません");
            return false;
        }
        return true;
    }

    private void toggleSelection(Player player, Block clicked) {
        SelectedContainer selection = createSelection(clicked);
        LinkedHashMap<String, SelectedContainer> playerSelections = selections.computeIfAbsent(player.getUniqueId(), id -> new LinkedHashMap<>());
        if (playerSelections.remove(selection.key()) != null) {
            if (playerSelections.isEmpty())
                selections.remove(player.getUniqueId());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6F, 0.7F);
            player.sendMessage("§e選択解除: §f" + formatLocation(selection.displayLocation()));
            return;
        }

        playerSelections.put(selection.key(), selection);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7F, 1.2F);
        spawnSelectionPulse(player, selection.blocks());
        player.sendMessage("§a選択: §f" + formatLocation(selection.displayLocation()) + " §7(" + playerSelections.size() + "件)");
    }

    private void executeSort(Player player, Block clicked) {
        LinkedHashMap<String, SelectedContainer> playerSelections = selections.get(player.getUniqueId());
        if (playerSelections == null || playerSelections.isEmpty()) {
            SelectedContainer single = createSelection(clicked);
            playerSelections = new LinkedHashMap<>();
            playerSelections.put(single.key(), single);
        }

        List<ResolvedContainer> containers = resolveContainers(player, playerSelections.values());
        if (containers.isEmpty()) {
            selections.remove(player.getUniqueId());
            player.sendMessage("§c整理できるチェストが見つかりませんでした");
            return;
        }

        sortInventories(containers);
        playSortEffects(player, containers);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7F, 1.2F);
        player.sendMessage("§a" + containers.size() + "個のチェストをまとめて整理しました");
        selections.remove(player.getUniqueId());
        player.sendMessage("§7選択を解除しました");
    }

    private List<ResolvedContainer> resolveContainers(Player player, Collection<SelectedContainer> selectedContainers) {
        List<ResolvedContainer> containers = new ArrayList<>();
        Set<String> resolvedKeys = new LinkedHashSet<>();
        for (SelectedContainer selected : selectedContainers) {
            BlockState state = selected.displayLocation().getBlock().getState();
            if (!(state instanceof InventoryHolder holder))
                continue;
            Inventory inventory = holder.getInventory();
            if (!canUseContainer(player, selected.displayLocation()))
                continue;
            List<Location> locations = resolveContainerLocations(holder, selected.displayLocation());
            String inventoryKey = toSelectionKey(locations);
            if (!resolvedKeys.add(inventoryKey))
                continue;
            containers.add(new ResolvedContainer(inventoryKey, inventory, locations, selected.displayLocation()));
        }
        containers.sort(Comparator.comparing(container -> container.displayLocation().toString()));
        return containers;
    }

    private void sortInventories(List<ResolvedContainer> containers) {
        // 1. スナップショット取得(失敗時ロールバック用)と、同種アイテムの総数集計
        List<ItemStack[]> snapshots = new ArrayList<>();
        Map<ItemStack, Long> totals = new LinkedHashMap<>();
        long totalBefore = 0L;
        for (ResolvedContainer container : containers) {
            ItemStack[] contents = container.inventory().getContents();
            ItemStack[] snapshot = new ItemStack[contents.length];
            for (int slot = 0; slot < contents.length; slot++)
                snapshot[slot] = contents[slot] == null ? null : contents[slot].clone();
            snapshots.add(snapshot);
            for (ItemStack item : contents) {
                if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0)
                    continue;
                totalBefore += item.getAmount();
                ItemStack key = item.clone();
                key.setAmount(1);
                totals.merge(key, (long) item.getAmount(), Long::sum);
            }
        }
        if (totals.isEmpty())
            return;

        // 2. 総数から満杯スタックを優先して再構成(端数は各種類につき最大1つ)
        List<ItemStack> stacks = new ArrayList<>();
        for (Map.Entry<ItemStack, Long> entry : totals.entrySet()) {
            ItemStack key = entry.getKey();
            int maxStackSize = Math.max(1, key.getMaxStackSize());
            long remaining = entry.getValue();
            while (remaining > 0) {
                int amount = (int) Math.min(maxStackSize, remaining);
                ItemStack stack = key.clone();
                stack.setAmount(amount);
                stacks.add(stack);
                remaining -= amount;
            }
        }

        // 3. カテゴリ → 素材 → メタ情報 → 数量(多い順)の全順序でソート
        stacks.sort(ChestSortModule::compareStacks);

        // 4. 配置。書き込み前に総数一致を検証し、不一致なら何もせずロールバック
        int index = 0;
        long totalAfter = 0L;
        List<ItemStack[]> layouts = new ArrayList<>();
        for (ResolvedContainer container : containers) {
            int size = container.inventory().getSize();
            ItemStack[] layout = new ItemStack[size];
            for (int slot = 0; slot < size && index < stacks.size(); slot++) {
                layout[slot] = stacks.get(index++);
                totalAfter += layout[slot].getAmount();
            }
            layouts.add(layout);
        }
        List<ItemStack> overflow = new ArrayList<>();
        for (; index < stacks.size(); index++) {
            overflow.add(stacks.get(index));
            totalAfter += stacks.get(index).getAmount();
        }
        if (totalAfter != totalBefore) {
            for (int i = 0; i < containers.size(); i++)
                containers.get(i).inventory().setContents(snapshots.get(i));
            return;
        }
        for (int i = 0; i < containers.size(); i++)
            containers.get(i).inventory().setContents(layouts.get(i));

        // オーバースタック品の再展開などで枠が足りない場合のみ発生。消滅させずドロップする
        if (!overflow.isEmpty()) {
            Location dropLocation = containers.get(containers.size() - 1).displayLocation().clone().add(0.5D, 1.0D, 0.5D);
            World world = dropLocation.getWorld();
            if (world != null) {
                for (ItemStack leftover : overflow)
                    world.dropItemNaturally(dropLocation, leftover);
            }
        }
    }

    private void tickSelectionOutlines() {
        List<UUID> emptyPlayers = new ArrayList<>();
        for (Map.Entry<UUID, LinkedHashMap<String, SelectedContainer>> entry : selections.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                emptyPlayers.add(entry.getKey());
                continue;
            }
            if (!toggle.getGlobal(FEATURE_KEY) || !toggle.isEnabledFor(player.getUniqueId().toString(), FEATURE_KEY))
                continue;
            for (SelectedContainer selection : entry.getValue().values())
                spawnOutline(player, selection.blocks());
        }
        for (UUID playerId : emptyPlayers)
            selections.remove(playerId);
    }

    private void spawnSelectionPulse(Player player, List<Location> blocks) {
        for (Location blockLocation : blocks) {
            World world = blockLocation.getWorld();
            if (world == null)
                continue;
            player.spawnParticle(Particle.WAX_ON, blockLocation.clone().add(0.5D, 0.65D, 0.5D), 8, 0.3D, 0.2D, 0.3D, 0.01D);
        }
    }

    private void spawnOutline(Player player, List<Location> blocks) {
        for (Location blockLocation : blocks) {
            World world = blockLocation.getWorld();
            if (world == null)
                continue;
            spawnEdgeBox(player, blockLocation, Particle.WAX_OFF);
        }
    }

    private void playSortEffects(Player player, List<ResolvedContainer> containers) {
        for (ResolvedContainer container : containers) {
            for (Location blockLocation : container.blocks()) {
                World world = blockLocation.getWorld();
                if (world == null)
                    continue;
                Location center = blockLocation.clone().add(0.5D, 0.6D, 0.5D);
                world.spawnParticle(Particle.ENCHANT, center, 10, 0.3D, 0.2D, 0.3D, 0.02D);
                world.spawnParticle(Particle.WAX_ON, center, 6, 0.2D, 0.12D, 0.2D, 0.01D);
                world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5F, 1.3F);
            }
        }
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0.0D, 1.0D, 0.0D), 4, 0.2D, 0.2D, 0.2D, 0.02D);
    }

    private void spawnEdgeBox(Player player, Location blockLocation, Particle particle) {
        double minX = blockLocation.getX();
        double minY = blockLocation.getY();
        double minZ = blockLocation.getZ();
        double maxX = minX + 1.0D;
        double maxY = minY + 1.0D;
        double maxZ = minZ + 1.0D;
        double step = 0.5D;
        for (double offset = 0.0D; offset <= 1.0001D; offset += step) {
            player.spawnParticle(particle, minX + offset, minY, minZ, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            player.spawnParticle(particle, minX + offset, minY, maxZ, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            player.spawnParticle(particle, minX + offset, maxY, minZ, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            player.spawnParticle(particle, minX + offset, maxY, maxZ, 1, 0.0D, 0.0D, 0.0D, 0.0D);

            player.spawnParticle(particle, minX, minY + offset, minZ, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            player.spawnParticle(particle, minX, minY + offset, maxZ, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            player.spawnParticle(particle, maxX, minY + offset, minZ, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            player.spawnParticle(particle, maxX, minY + offset, maxZ, 1, 0.0D, 0.0D, 0.0D, 0.0D);

            player.spawnParticle(particle, minX, minY, minZ + offset, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            player.spawnParticle(particle, maxX, minY, minZ + offset, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            player.spawnParticle(particle, minX, maxY, minZ + offset, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            player.spawnParticle(particle, maxX, maxY, minZ + offset, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private SelectedContainer createSelection(Block clicked) {
        List<Location> relatedLocations = resolveContainerLocations(clicked);
        relatedLocations.sort(Comparator.comparing(Location::toString));
        return new SelectedContainer(toSelectionKey(relatedLocations), relatedLocations.get(0), relatedLocations);
    }

    private List<Location> resolveContainerLocations(Block block) {
        BlockState state = block.getState();
        if (state instanceof InventoryHolder holder)
            return resolveContainerLocations(holder, block.getLocation());
        List<Location> locations = new ArrayList<>();
        locations.add(block.getLocation());
        return locations;
    }

    private List<Location> resolveContainerLocations(InventoryHolder holder, Location fallback) {
        List<Location> locations = new ArrayList<>();
        if (holder instanceof Chest chest) {
            Inventory inventory = chest.getInventory();
            if (inventory instanceof DoubleChestInventory doubleChestInventory) {
                addHolderLocation(doubleChestInventory.getLeftSide().getHolder(), locations);
                addHolderLocation(doubleChestInventory.getRightSide().getHolder(), locations);
            }
        }
        if (holder instanceof BlockState state)
            locations.add(state.getLocation());
        if (locations.isEmpty() && fallback != null)
            locations.add(fallback);
        LinkedHashMap<String, Location> unique = new LinkedHashMap<>();
        for (Location location : locations) {
            if (location == null || location.getWorld() == null)
                continue;
            unique.putIfAbsent(formatLocation(location), location);
        }
        if (unique.isEmpty() && fallback != null && fallback.getWorld() != null)
            unique.put(formatLocation(fallback), fallback);
        return new ArrayList<>(unique.values());
    }

    private void addHolderLocation(InventoryHolder holder, List<Location> locations) {
        if (!(holder instanceof BlockState state))
            return;
        Location location = state.getLocation();
        if (location != null && location.getWorld() != null)
            locations.add(location);
    }

    private void clearSelectionsByBrokenBlock(Location brokenLocation) {
        List<UUID> emptyPlayers = new ArrayList<>();
        for (Map.Entry<UUID, LinkedHashMap<String, SelectedContainer>> entry : selections.entrySet()) {
            LinkedHashMap<String, SelectedContainer> playerSelections = entry.getValue();
            int beforeSize = playerSelections.size();
            playerSelections.entrySet().removeIf(selection -> containsLocation(selection.getValue().blocks(), brokenLocation));
            if (playerSelections.isEmpty())
                emptyPlayers.add(entry.getKey());
            if (beforeSize == playerSelections.size())
                continue;
            Player selectedPlayer = Bukkit.getPlayer(entry.getKey());
            if (selectedPlayer == null || !selectedPlayer.isOnline())
                continue;
            selectedPlayer.playSound(selectedPlayer.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6F, 0.7F);
            selectedPlayer.sendMessage("§eチェスト破壊により選択を解除しました");
        }
        for (UUID playerId : emptyPlayers)
            selections.remove(playerId);
    }

    private boolean containsLocation(List<Location> locations, Location target) {
        for (Location location : locations) {
            if (sameBlock(location, target))
                return true;
        }
        return false;
    }

    private boolean sameBlock(Location first, Location second) {
        if (first == null || second == null)
            return false;
        if (!Objects.equals(first.getWorld(), second.getWorld()))
            return false;
        return first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private String toSelectionKey(List<Location> locations) {
        List<String> parts = new ArrayList<>();
        for (Location location : locations)
            parts.add(formatLocation(location));
        parts.sort(String::compareTo);
        return String.join("|", parts);
    }

    private String formatLocation(Location location) {
        return location.getWorld().getName() + ":" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    /**
     * 仕分けカテゴリ。宣言順がそのまま格納順になる。
     */
    private enum SortCategory {
        VALUABLES,
        TOOLS,
        WEAPONS,
        ARMOR,
        FOOD,
        POTIONS,
        REDSTONE,
        STORAGE,
        BLOCKS,
        MATERIALS,
        MISC
    }

    private static int compareStacks(ItemStack a, ItemStack b) {
        int compare = Integer.compare(categoryOf(a.getType()).ordinal(), categoryOf(b.getType()).ordinal());
        if (compare != 0)
            return compare;
        // Material の宣言順(レジストリ順)は同系統のアイテムが隣接するため、アルファベット順より自然に並ぶ
        compare = Integer.compare(a.getType().ordinal(), b.getType().ordinal());
        if (compare != 0)
            return compare;
        compare = Integer.compare(enchantWeight(b), enchantWeight(a));
        if (compare != 0)
            return compare;
        String aName = displayNameOf(a);
        String bName = displayNameOf(b);
        if (aName == null && bName != null)
            return 1;
        if (aName != null && bName == null)
            return -1;
        if (aName != null) {
            compare = aName.compareTo(bName);
            if (compare != 0)
                return compare;
        }
        compare = Integer.compare(damage(a), damage(b));
        if (compare != 0)
            return compare;
        // 残るメタ差(ロア・ポーション効果など)を決定的に順序付けする
        compare = metaKey(a).compareTo(metaKey(b));
        if (compare != 0)
            return compare;
        return Integer.compare(b.getAmount(), a.getAmount());
    }

    private static SortCategory categoryOf(Material material) {
        if (material == null)
            return SortCategory.MISC;
        String name = material.name();
        if (isValuable(material, name))
            return SortCategory.VALUABLES;
        if (isTool(material, name))
            return SortCategory.TOOLS;
        if (isWeapon(material, name))
            return SortCategory.WEAPONS;
        if (isArmor(material, name))
            return SortCategory.ARMOR;
        if (isPotion(material, name))
            return SortCategory.POTIONS;
        if (material.isEdible() || name.endsWith("_SEEDS") || material == Material.WHEAT
                || material == Material.SUGAR_CANE || material == Material.EGG || material == Material.MILK_BUCKET)
            return SortCategory.FOOD;
        if (isRedstone(material, name))
            return SortCategory.REDSTONE;
        if (name.endsWith("SHULKER_BOX") || material == Material.BUNDLE || material == Material.ENDER_CHEST)
            return SortCategory.STORAGE;
        if (material.isBlock())
            return SortCategory.BLOCKS;
        if (material.isItem())
            return SortCategory.MATERIALS;
        return SortCategory.MISC;
    }

    private static boolean isValuable(Material material, String name) {
        switch (material) {
            case DIAMOND:
            case EMERALD:
            case NETHERITE_SCRAP:
            case ANCIENT_DEBRIS:
            case LAPIS_LAZULI:
            case QUARTZ:
            case AMETHYST_SHARD:
                return true;
            default:
                break;
        }
        return name.endsWith("_INGOT") || name.endsWith("_NUGGET") || name.startsWith("RAW_")
                || name.endsWith("_ORE") || name.endsWith("_BLOCK") && isValuableBlock(name);
    }

    private static boolean isValuableBlock(String name) {
        return name.equals("DIAMOND_BLOCK") || name.equals("EMERALD_BLOCK") || name.equals("GOLD_BLOCK")
                || name.equals("IRON_BLOCK") || name.equals("NETHERITE_BLOCK") || name.equals("COPPER_BLOCK")
                || name.equals("LAPIS_BLOCK") || name.equals("REDSTONE_BLOCK") || name.equals("COAL_BLOCK")
                || name.equals("RAW_IRON_BLOCK") || name.equals("RAW_GOLD_BLOCK") || name.equals("RAW_COPPER_BLOCK");
    }

    private static boolean isTool(Material material, String name) {
        switch (material) {
            case FLINT_AND_STEEL:
            case SHEARS:
            case FISHING_ROD:
            case COMPASS:
            case RECOVERY_COMPASS:
            case CLOCK:
            case SPYGLASS:
            case BRUSH:
            case LEAD:
            case NAME_TAG:
                return true;
            default:
                break;
        }
        return name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE");
    }

    private static boolean isWeapon(Material material, String name) {
        switch (material) {
            case BOW:
            case CROSSBOW:
            case TRIDENT:
            case MACE:
            case ARROW:
            case SPECTRAL_ARROW:
                return true;
            default:
                break;
        }
        return name.endsWith("_SWORD");
    }

    private static boolean isArmor(Material material, String name) {
        if (material == Material.SHIELD || material == Material.ELYTRA || material == Material.TOTEM_OF_UNDYING)
            return true;
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS") || name.endsWith("_HORSE_ARMOR"))
            return true;
        EquipmentSlot slot = material.getEquipmentSlot();
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }

    private static boolean isPotion(Material material, String name) {
        return material == Material.POTION || material == Material.SPLASH_POTION || material == Material.LINGERING_POTION
                || material == Material.TIPPED_ARROW || material == Material.EXPERIENCE_BOTTLE
                || material == Material.ENCHANTED_BOOK || name.equals("GLASS_BOTTLE") || name.equals("BREWING_STAND");
    }

    private static boolean isRedstone(Material material, String name) {
        switch (material) {
            case REDSTONE:
            case REPEATER:
            case COMPARATOR:
            case REDSTONE_TORCH:
            case LEVER:
            case HOPPER:
            case DROPPER:
            case DISPENSER:
            case OBSERVER:
            case PISTON:
            case STICKY_PISTON:
            case SLIME_BLOCK:
            case HONEY_BLOCK:
            case TNT:
            case NOTE_BLOCK:
            case DAYLIGHT_DETECTOR:
            case TARGET:
            case TRIPWIRE_HOOK:
            case REDSTONE_LAMP:
                return true;
            default:
                break;
        }
        return name.endsWith("_RAIL") || name.equals("RAIL") || name.endsWith("_BUTTON") || name.endsWith("_PRESSURE_PLATE");
    }

    private static int enchantWeight(ItemStack item) {
        int weight = item.getEnchantments().size();
        if (item.getType() == Material.ENCHANTED_BOOK)
            weight += 100;
        return weight;
    }

    private static String displayNameOf(ItemStack item) {
        if (!item.hasItemMeta())
            return null;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() ? meta.getDisplayName() : null;
    }

    private static String metaKey(ItemStack item) {
        if (!item.hasItemMeta())
            return "";
        ItemMeta meta = item.getItemMeta();
        return meta == null ? "" : String.valueOf(meta);
    }

    private static int damage(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable damageable)
            return damageable.getDamage();
        return 0;
    }

    private record SelectedContainer(String key, Location displayLocation, List<Location> blocks) {
    }

    private record ResolvedContainer(String key, Inventory inventory, List<Location> blocks, Location displayLocation) {
    }
}
