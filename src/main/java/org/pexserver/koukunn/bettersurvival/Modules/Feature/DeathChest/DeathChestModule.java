package org.pexserver.koukunn.bettersurvival.Modules.Feature.DeathChest;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.LandProtectionModule;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DeathChestModule implements Listener {
    private static final String FEATURE_KEY = "deathchest";
    private static final int SEARCH_RADIUS = 4;
    private static final int[] Y_OFFSETS = {0, 1, -1, 2, -2, 3, -3};
    private static final int GUI_SIZE = 27;
    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final ToggleModule toggle;
    private final LandProtectionModule landProtection;
    private final NamespacedKey deathChestKey;

    public DeathChestModule(Loader plugin, ToggleModule toggle, LandProtectionModule landProtection) {
        this.toggle = toggle;
        this.landProtection = landProtection;
        this.deathChestKey = new NamespacedKey(plugin, "death_chest");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!toggle.getGlobal(FEATURE_KEY)) return;
        if (!toggle.isEnabledFor(event.getPlayer().getUniqueId().toString(), FEATURE_KEY)) return;

        Location deathLocation = event.getPlayer().getLocation();
        if (landProtection != null && landProtection.getActiveClaimAt(deathLocation) != null) return;
        if (event.getEntity().getKiller() != null) return;
        if (event.getKeepInventory() || event.getDrops().isEmpty()) return;

        sendDeathLocation(event, deathLocation);

        List<ItemStack> drops = copyDrops(event.getDrops());
        if (drops.isEmpty()) return;

        Block placement = findPlacement(deathLocation);
        if (placement == null) {
            event.getPlayer().sendMessage("§cDeathChestを設置できませんでした。アイテムは通常通りドロップします。");
            return;
        }

        Inventory chestInventory = createSingleChest(placement);
        if (chestInventory == null) {
            event.getPlayer().sendMessage("§cDeathChestを作成できませんでした。アイテムは通常通りドロップします。");
            return;
        }

        event.getDrops().clear();
        int overflow = storeDrops(drops, chestInventory, placement.getLocation().add(0.5, 1.0, 0.5));

        Location chestLocation = placement.getLocation();
        event.getPlayer().sendMessage("§aDeathChestを設置しました: §f" + formatLocation(chestLocation));
        if (overflow > 0) {
            event.getPlayer().sendMessage("§eチェストに入り切らなかったアイテム " + overflow + " 個はDeathChest付近にドロップしました。");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Material type = event.getBlockPlaced().getType();
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) return;
        for (BlockFace face : HORIZONTAL_FACES) {
            if (isDeathChestBlock(event.getBlockPlaced().getRelative(face))) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cDeathChestの隣にチェストを設置することはできません。");
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isDeathChestBlock(block)) return;
        event.setDropItems(false);
        event.setExpToDrop(0);
        BlockState state = block.getState(false);
        if (state instanceof Chest chest) {
            Inventory inventory = chest.getInventory();
            Location dropLocation = block.getLocation().add(0.5, 0.5, 0.5);
            World world = block.getWorld();
            for (ItemStack item : inventory.getContents()) {
                if (item == null || item.getType().isAir() || item.getAmount() <= 0) continue;
                world.dropItemNaturally(dropLocation, item);
            }
            inventory.clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!isDeathChestBlock(block)) return;
        event.setCancelled(true);
        openDeathChestGui(event.getPlayer(), block);
    }

    private void openDeathChestGui(Player player, Block block) {
        BlockState state = block.getState(false);
        if (!(state instanceof Chest chest)) return;
        Inventory source = chest.getInventory();
        DeathChestHolder holder = new DeathChestHolder(block);
        Inventory gui = Bukkit.createInventory(holder, GUI_SIZE, ComponentUtils.legacy("§8☠ §cDeathChest §8☠"));
        int limit = Math.min(source.getSize(), GUI_SIZE);
        for (int i = 0; i < limit; i++) {
            ItemStack item = source.getItem(i);
            if (item != null && !item.getType().isAir()) {
                gui.setItem(i, item.clone());
            }
        }
        holder.setInventory(gui);
        player.openInventory(gui);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof DeathChestHolder)) return;
        InventoryAction action = event.getAction();
        if (event.getClickedInventory() == top) {
            switch (action) {
                case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR,
                     HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> event.setCancelled(true);
                default -> {}
            }
            return;
        }
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof DeathChestHolder)) return;
        int topSize = top.getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory gui = event.getInventory();
        if (!(gui.getHolder() instanceof DeathChestHolder holder)) return;
        Block block = holder.block();
        if (block.getType() != Material.CHEST) return;
        BlockState state = block.getState(false);
        if (!(state instanceof Chest chest)) return;
        Inventory chestInventory = chest.getInventory();
        chestInventory.clear();
        int limit = Math.min(gui.getSize(), chestInventory.getSize());
        for (int i = 0; i < limit; i++) {
            ItemStack item = gui.getItem(i);
            if (item != null && !item.getType().isAir()) {
                chestInventory.setItem(i, item);
            }
        }
        if (isInventoryEmpty(chestInventory)) {
            block.setType(Material.AIR, false);
        }
    }

    private void sendDeathLocation(PlayerDeathEvent event, Location location) {
        event.getPlayer().sendMessage("§e死亡地点: §f" + formatLocation(location));
    }

    private String formatLocation(Location location) {
        World world = location.getWorld();
        String worldName = world == null ? "unknown" : world.getName();
        return worldName + " X:" + location.getBlockX() + " Y:" + location.getBlockY() + " Z:" + location.getBlockZ();
    }

    private List<ItemStack> copyDrops(List<ItemStack> source) {
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack item : source) {
            if (item == null || item.getType().isAir()) continue;
            drops.add(item.clone());
        }
        return drops;
    }

    private int storeDrops(List<ItemStack> drops, Inventory inventory, Location overflowLocation) {
        int overflow = 0;
        World world = overflowLocation.getWorld();
        for (ItemStack item : drops) {
            Map<Integer, ItemStack> result = inventory.addItem(item);
            for (ItemStack leftover : result.values()) {
                world.dropItemNaturally(overflowLocation, leftover);
                overflow += leftover.getAmount();
            }
        }
        return overflow;
    }

    private Block findPlacement(Location location) {
        World world = location.getWorld();
        if (world == null) return null;

        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();
        for (int yOffset : Y_OFFSETS) {
            int y = baseY + yOffset;
            if (y < world.getMinHeight() || y >= world.getMaxHeight() - 1) continue;
            for (int radius = 0; radius <= SEARCH_RADIUS; radius++) {
                for (int x = baseX - radius; x <= baseX + radius; x++) {
                    for (int z = baseZ - radius; z <= baseZ + radius; z++) {
                        if (Math.max(Math.abs(x - baseX), Math.abs(z - baseZ)) != radius) continue;
                        Block candidate = world.getBlockAt(x, y, z);
                        if (!canPlaceChest(candidate)) continue;
                        if (!canPlaceAbove(candidate)) continue;
                        if (hasAdjacentChest(candidate)) continue;
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean canPlaceChest(Block block) {
        return block.getType().isAir();
    }

    private boolean canPlaceAbove(Block block) {
        return block.getRelative(BlockFace.UP).getType().isAir();
    }

    private boolean hasAdjacentChest(Block block) {
        for (BlockFace face : HORIZONTAL_FACES) {
            Material type = block.getRelative(face).getType();
            if (type == Material.CHEST || type == Material.TRAPPED_CHEST) {
                return true;
            }
        }
        return false;
    }

    private Inventory createSingleChest(Block block) {
        block.setType(Material.CHEST, false);
        BlockData data = block.getBlockData();
        if (data instanceof org.bukkit.block.data.type.Chest chestData) {
            chestData.setType(org.bukkit.block.data.type.Chest.Type.SINGLE);
            block.setBlockData(chestData, true);
        }
        BlockState state = block.getState(false);
        if (!(state instanceof Chest chest)) {
            block.setType(Material.AIR, false);
            return null;
        }
        PersistentDataContainer container = chest.getPersistentDataContainer();
        container.set(deathChestKey, PersistentDataType.BYTE, (byte) 1);
        chest.update(true);
        return chest.getInventory();
    }

    private boolean isInventoryEmpty(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) continue;
            return false;
        }
        return true;
    }

    private boolean isDeathChestBlock(Block block) {
        if (block.getType() != Material.CHEST) return false;
        BlockState state = block.getState(false);
        if (!(state instanceof Chest chest)) return false;
        return chest.getPersistentDataContainer().has(deathChestKey, PersistentDataType.BYTE);
    }

    private static final class DeathChestHolder implements InventoryHolder {
        private final Block block;
        private Inventory inventory;

        private DeathChestHolder(Block block) {
            this.block = block;
        }

        private Block block() {
            return block;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
