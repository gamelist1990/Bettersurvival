package org.pexserver.koukunn.bettersurvival.Modules.Feature.Protect;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import java.util.Locale;

/**
 * Protect のアイテム・インベントリ監査。
 */
public final class ProtectItemListener implements Listener {
    private final ProtectModule module;

    public ProtectItemListener(ProtectModule module) {
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMove(InventoryMoveItemEvent event) {
        if (!module.isEnabled()) return;

        Location source = resolveLocation(event.getSource());
        Location destination = resolveLocation(event.getDestination());
        ItemStack item = ProtectModule.copyItem(event.getItem());
        byte[] bytes = ProtectModule.serializeItem(item);
        String summary = ProtectModule.itemSummary(bytes);

        if (source != null) {
            module.recordSystem(
                    "#hopper",
                    source,
                    ProtectAction.CONTAINER_TRANSFER,
                    null, null, null,
                    bytes, null,
                    "OUT " + summary + destinationText(destination));
        }
        if (destination != null) {
            module.recordSystem(
                    "#hopper",
                    destination,
                    ProtectAction.CONTAINER_TRANSFER,
                    null, null, null,
                    null, bytes,
                    "IN " + summary + sourceText(source));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!module.isEnabled()) return;

        Item itemEntity = event.getItemDrop();
        ItemStack item = itemEntity.getItemStack();
        module.recordPlayer(
                event.getPlayer(),
                itemEntity.getLocation(),
                ProtectAction.ITEM_DROP,
                null, null, null,
                ProtectModule.serializeItem(item), null,
                ProtectModule.itemSummary(ProtectModule.serializeItem(item)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!module.isEnabled() || !(event.getEntity() instanceof Player player)) return;

        Item itemEntity = event.getItem();
        ItemStack item = itemEntity.getItemStack();
        module.recordPlayer(
                player,
                itemEntity.getLocation(),
                ProtectAction.ITEM_PICKUP,
                null, null, null,
                null, ProtectModule.serializeItem(item),
                ProtectModule.itemSummary(ProtectModule.serializeItem(item)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemBreak(PlayerItemBreakEvent event) {
        if (!module.isEnabled()) return;

        ItemStack broken = event.getBrokenItem();
        module.recordPlayer(
                event.getPlayer(),
                event.getPlayer().getLocation(),
                ProtectAction.ITEM_BREAK,
                null, null, null,
                ProtectModule.serializeItem(broken), null,
                ProtectModule.itemSummary(ProtectModule.serializeItem(broken)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!module.isEnabled() || !(event.getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getRecipe().getResult();
        module.recordPlayer(
                player,
                player.getLocation(),
                ProtectAction.ITEM_CRAFT,
                null, null, null,
                null, ProtectModule.serializeItem(result),
                "recipe=" + result.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!module.isEnabled()) return;

        Projectile projectile = event.getEntity();
        ProjectileSource shooter = projectile.getShooter();
        if (!(shooter instanceof Player player)) return;

        module.recordPlayer(
                player,
                projectile.getLocation(),
                ProtectAction.ITEM_SHOOT,
                null, null, null,
                null, null,
                projectile.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTradeResult(InventoryClickEvent event) {
        if (!module.isEnabled() || !(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getType() != InventoryType.MERCHANT) return;
        if (event.getRawSlot() != 2) return;

        ItemStack result = ProtectModule.copyItem(event.getCurrentItem());
        if (result == null) return;

        module.recordPlayer(
                player,
                player.getLocation(),
                ProtectAction.ITEM_TRADE,
                null, null, null,
                null, ProtectModule.serializeItem(result),
                ProtectModule.itemSummary(ProtectModule.serializeItem(result)));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpecialBlockItemInteraction(PlayerInteractEvent event) {
        if (!module.isEnabled()) return;
        if (event.getClickedBlock() == null) return;

        Material type = event.getClickedBlock().getType();
        if (!isTrackedItemBlock(type)) return;

        ItemStack hand = ProtectModule.copyItem(event.getItem());
        module.recordPlayer(
                event.getPlayer(),
                event.getClickedBlock().getLocation(),
                ProtectAction.ITEM_INTERACT,
                event.getClickedBlock().getBlockData().getAsString(),
                event.getClickedBlock().getBlockData().getAsString(),
                null,
                ProtectModule.serializeItem(hand),
                null,
                type.name() + " / " + event.getAction().name());
    }

    private boolean isTrackedItemBlock(Material type) {
        String name = type.name();
        return type == Material.JUKEBOX
                || type == Material.LECTERN
                || type == Material.CAMPFIRE
                || type == Material.SOUL_CAMPFIRE
                || type == Material.CRAFTER
                || type == Material.DECORATED_POT
                || name.contains("CHISELED_BOOKSHELF")
                || name.contains("SHELF");
    }

    private Location resolveLocation(Inventory inventory) {
        if (inventory == null) return null;
        InventoryHolder holder = inventory.getHolder();
        if (holder instanceof DoubleChest doubleChest) {
            return doubleChest.getLocation();
        }
        if (holder instanceof BlockInventoryHolder blockHolder) {
            try {
                return blockHolder.getBlock().getLocation();
            } catch (IllegalStateException ignored) {
                return null;
            }
        }
        return inventory.getLocation();
    }

    private String destinationText(Location location) {
        return location == null ? "" : " -> " + compact(location);
    }

    private String sourceText(Location location) {
        return location == null ? "" : " <- " + compact(location);
    }

    private String compact(Location location) {
        return location.getWorld().getName() + ":"
                + location.getBlockX() + ","
                + location.getBlockY() + ","
                + location.getBlockZ();
    }
}
