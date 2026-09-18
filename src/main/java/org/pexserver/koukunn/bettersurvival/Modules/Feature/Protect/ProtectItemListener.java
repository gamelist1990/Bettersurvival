package org.pexserver.koukunn.bettersurvival.Modules.Feature.Protect;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
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
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
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
        String automationActor = automationActor(event.getSource());
        ItemStack item = ProtectModule.copyItem(event.getItem());
        String summary = ProtectModule.itemSummary(item);
        String tag = item != null && item.getType() == Material.BUNDLE ? "#bundle " : "";

        // Hopper系は非常に高頻度なので、巨大なItemStack BLOBを毎回保存せず
        // type/amountをdetailへ記録してDBサイズとserialize負荷を抑える。
        if (source != null) {
            module.recordSystem(
                    automationActor,
                    source,
                    ProtectAction.CONTAINER_TRANSFER,
                    null, null, null,
                    null, null,
                    tag + "OUT " + summary + destinationText(destination));
        }
        if (destination != null) {
            module.recordSystem(
                    automationActor,
                    destination,
                    ProtectAction.CONTAINER_TRANSFER,
                    null, null, null,
                    null, null,
                    tag + "IN " + summary + sourceText(source));
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
    public void onHangingPlace(HangingPlaceEvent event) {
        if (!module.isEnabled()) return;

        Hanging hanging = event.getEntity();
        Player player = event.getPlayer();
        if (player != null) {
            module.recordPlayer(
                    player,
                    hanging.getLocation(),
                    ProtectAction.ITEM_INTERACT,
                    null, null, null,
                    ProtectModule.serializeItem(event.getItemStack()),
                    null,
                    "HANGING_PLACE " + hanging.getType().name());
        } else {
            module.recordSystem(
                    "#hanging",
                    hanging.getLocation(),
                    ProtectAction.ITEM_INTERACT,
                    null, null, null,
                    ProtectModule.serializeItem(event.getItemStack()),
                    null,
                    "HANGING_PLACE " + hanging.getType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (!module.isEnabled()) return;

        Entity remover = event.getRemover();
        if (remover instanceof Player player) {
            module.recordPlayer(
                    player,
                    event.getEntity().getLocation(),
                    ProtectAction.ITEM_INTERACT,
                    null, null, null,
                    null, null,
                    "HANGING_BREAK " + event.getEntity().getType().name());
        } else {
            module.recordSystem(
                    remover == null ? "#hanging" : "#" + remover.getType().name().toLowerCase(Locale.ROOT),
                    event.getEntity().getLocation(),
                    ProtectAction.ITEM_INTERACT,
                    null, null, null,
                    null, null,
                    "HANGING_BREAK " + event.getEntity().getType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemFrameInteract(PlayerInteractEntityEvent event) {
        if (!module.isEnabled() || !(event.getRightClicked() instanceof ItemFrame frame)) return;

        module.recordPlayer(
                event.getPlayer(),
                frame.getLocation(),
                ProtectAction.ITEM_INTERACT,
                null, null, null,
                ProtectModule.serializeItem(frame.getItem()),
                ProtectModule.serializeItem(event.getPlayer().getInventory().getItem(event.getHand())),
                "ITEM_FRAME_INTERACT");
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

    private String automationActor(Inventory inventory) {
        if (inventory != null && inventory.getHolder() instanceof BlockInventoryHolder blockHolder) {
            try {
                return "#" + blockHolder.getBlock().getType().name().toLowerCase(Locale.ROOT);
            } catch (IllegalStateException ignored) {
            }
        }
        return "#transfer";
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
