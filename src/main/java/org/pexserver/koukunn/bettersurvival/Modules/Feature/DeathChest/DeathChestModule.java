package org.pexserver.koukunn.bettersurvival.Modules.Feature.DeathChest;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DeathChestModule implements Listener {
    private static final String FEATURE_KEY = "deathchest";
    private static final int SEARCH_RADIUS = 4;
    private static final int[] Y_OFFSETS = {0, 1, -1, 2, -2, 3, -3};

    private final ToggleModule toggle;

    public DeathChestModule(ToggleModule toggle) {
        this.toggle = toggle;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!toggle.getGlobal(FEATURE_KEY)) return;
        if (!toggle.isEnabledFor(event.getPlayer().getUniqueId().toString(), FEATURE_KEY)) return;

        Location deathLocation = event.getPlayer().getLocation();
        sendDeathLocation(event, deathLocation);

        if (event.getKeepInventory() || event.getDrops().isEmpty()) return;

        List<ItemStack> drops = copyDrops(event.getDrops());
        if (drops.isEmpty()) return;

        ChestPlacement placement = findPlacement(deathLocation);
        if (placement == null) {
            event.getPlayer().sendMessage("§cDeathChestを設置できませんでした。アイテムは通常通りドロップします。");
            return;
        }

        ChestInventories inventories = createLargeChest(placement);
        if (inventories == null) {
            event.getPlayer().sendMessage("§cDeathChestを作成できませんでした。アイテムは通常通りドロップします。");
            return;
        }

        event.getDrops().clear();
        int overflow = storeDrops(drops, inventories, placement.first.getLocation().add(0.5, 1.0, 0.5));

        Location chestLocation = placement.first.getLocation();
        event.getPlayer().sendMessage("§aDeathChestを設置しました: §f" + formatLocation(chestLocation));
        if (overflow > 0) {
            event.getPlayer().sendMessage("§eチェストに入り切らなかったアイテム " + overflow + " 個はDeathChest付近にドロップしました。");
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

    private int storeDrops(List<ItemStack> drops, ChestInventories inventories, Location overflowLocation) {
        int overflow = 0;
        for (ItemStack item : drops) {
            List<ItemStack> leftovers = addItem(inventories.primary, List.of(item));
            if (inventories.secondary != null) {
                leftovers = addItem(inventories.secondary, leftovers);
            }
            for (ItemStack leftover : leftovers) {
                overflowLocation.getWorld().dropItemNaturally(overflowLocation, leftover);
                overflow += leftover.getAmount();
            }
        }
        return overflow;
    }

    private List<ItemStack> addItem(Inventory inventory, List<ItemStack> items) {
        if (items.isEmpty()) return items;
        List<ItemStack> leftovers = new ArrayList<>();
        for (ItemStack item : items) {
            Map<Integer, ItemStack> result = inventory.addItem(item);
            leftovers.addAll(result.values());
        }
        return leftovers;
    }

    private ChestPlacement findPlacement(Location location) {
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
                        ChestPlacement placement = checkPlacement(world.getBlockAt(x, y, z), Axis.X);
                        if (placement != null) return placement;
                        placement = checkPlacement(world.getBlockAt(x, y, z), Axis.Z);
                        if (placement != null) return placement;
                    }
                }
            }
        }
        return null;
    }

    private ChestPlacement checkPlacement(Block first, Axis axis) {
        Block second = axis == Axis.X ? first.getRelative(BlockFace.EAST) : first.getRelative(BlockFace.SOUTH);
        if (!canPlaceChest(first) || !canPlaceChest(second)) return null;
        if (!canPlaceAbove(first) || !canPlaceAbove(second)) return null;
        return new ChestPlacement(first, second, axis);
    }

    private boolean canPlaceChest(Block block) {
        return block.getType().isAir();
    }

    private boolean canPlaceAbove(Block block) {
        return block.getRelative(BlockFace.UP).getType().isAir();
    }

    private ChestInventories createLargeChest(ChestPlacement placement) {
        placement.first.setType(Material.CHEST, false);
        placement.second.setType(Material.CHEST, false);
        applyChestData(placement.first, placement.axis, org.bukkit.block.data.type.Chest.Type.LEFT);
        applyChestData(placement.second, placement.axis, org.bukkit.block.data.type.Chest.Type.RIGHT);

        Inventory primary = getChestInventory(placement.first);
        if (primary == null) return null;
        Inventory secondary = primary.getSize() >= 54 ? null : getChestInventory(placement.second);
        return new ChestInventories(primary, secondary);
    }

    private void applyChestData(Block block, Axis axis, org.bukkit.block.data.type.Chest.Type type) {
        BlockData data = Material.CHEST.createBlockData();
        org.bukkit.block.data.type.Chest chestData = (org.bukkit.block.data.type.Chest) data;
        chestData.setFacing(axis == Axis.X ? BlockFace.NORTH : BlockFace.EAST);
        chestData.setType(type);
        block.setBlockData(chestData, true);
    }

    private Inventory getChestInventory(Block block) {
        BlockState state = block.getState(false);
        if (!(state instanceof org.bukkit.block.Chest)) return null;
        return ((org.bukkit.block.Chest) state).getInventory();
    }

    private enum Axis {
        X,
        Z
    }

    private static class ChestPlacement {
        private final Block first;
        private final Block second;
        private final Axis axis;

        private ChestPlacement(Block first, Block second, Axis axis) {
            this.first = first;
            this.second = second;
            this.axis = axis;
        }
    }

    private static class ChestInventories {
        private final Inventory primary;
        private final Inventory secondary;

        private ChestInventories(Inventory primary, Inventory secondary) {
            this.primary = primary;
            this.secondary = secondary;
        }
    }
}
