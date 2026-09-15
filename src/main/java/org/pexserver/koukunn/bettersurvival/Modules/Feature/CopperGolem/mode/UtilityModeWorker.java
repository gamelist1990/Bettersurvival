package org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.mode;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Container;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model.ContainerTarget;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model.GolemMode;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.LandProtectionModule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** 登録された保管先への整理・回収と、操作プレイヤーへの追従。 */
public final class UtilityModeWorker implements ModeWorker {
    private final Loader plugin;
    private final NamespacedKey operatorKey;
    private final NamespacedKey cargoKey;

    public UtilityModeWorker(Loader plugin) {
        this.plugin = plugin;
        operatorKey = new NamespacedKey(plugin, "golem_operator");
        cargoKey = new NamespacedKey(plugin, "golem_cargo");
    }

    public void assign(CopperGolem golem, Player player) {
        golem.getPersistentDataContainer().set(operatorKey, PersistentDataType.STRING, player.getUniqueId().toString());
        golem.setTarget(null);
        golem.getPathfinder().stopPathfinding();
    }

    public void releaseCargo(CopperGolem golem) {
        byte[] bytes = golem.getPersistentDataContainer().get(cargoKey, PersistentDataType.BYTE_ARRAY);
        if (bytes != null) {
            golem.getWorld().dropItemNaturally(golem.getLocation(), ItemStack.deserializeBytes(bytes));
            golem.getPersistentDataContainer().remove(cargoKey);
        }
    }

    @Override
    public int execute(ModeExecutionContext context) {
        CopperGolem golem = context.golem();
        String operator = golem.getPersistentDataContainer().get(operatorKey, PersistentDataType.STRING);
        Player player = operator == null ? null : Bukkit.getPlayer(UUID.fromString(operator));
        if (player == null || player.isDead() || golem.isLeashed()) {
            golem.getPathfinder().stopPathfinding();
            return 0;
        }
        if (context.profile().mode() == GolemMode.FOLLOW) {
            if (golem.getWorld().equals(player.getWorld()) && golem.getLocation().distanceSquared(player.getLocation()) > 9) {
                golem.getPathfinder().moveTo(player, 1.3);
            } else {
                golem.getPathfinder().stopPathfinding();
            }
            return 0;
        }
        List<ContainerTarget> targets = context.profile().targets().stream()
                .filter(target -> canUse(player, target, golem)).toList();
        if (targets.isEmpty()) {
            golem.getPathfinder().stopPathfinding();
            return 0;
        }
        if (context.profile().mode() == GolemMode.SORT) {
            int index = (int) ((golem.getWorld().getGameTime() / 100) % targets.size());
            ContainerTarget target = targets.get(index);
            if (approach(golem, target.anchor())) {
                compact(inventory(target));
            }
            return 0;
        }
        byte[] bytes = golem.getPersistentDataContainer().get(cargoKey, PersistentDataType.BYTE_ARRAY);
        if (bytes != null) {
            ItemStack cargo = ItemStack.deserializeBytes(bytes);
            for (ContainerTarget target : targets) {
                Inventory inventory = inventory(target);
                if (!hasSpace(inventory, cargo)) {
                    continue;
                }
                if (approach(golem, target.anchor())) {
                    ItemStack remaining = inventory.addItem(cargo.clone()).values().stream().findFirst().orElse(null);
                    if (remaining == null) {
                        golem.getPersistentDataContainer().remove(cargoKey);
                    } else {
                        golem.getPersistentDataContainer().set(cargoKey, PersistentDataType.BYTE_ARRAY, remaining.serializeAsBytes());
                    }
                }
                return 0;
            }
            golem.getPathfinder().stopPathfinding();
            return 0;
        }
        Location center = targets.getFirst().anchor();
        int radius = Math.min(context.profile().range(), context.maxRange());
        Item nearest = center.getWorld().getNearbyEntities(center, radius, radius, radius).stream()
                .filter(Item.class::isInstance).map(Item.class::cast)
                .filter(item -> item.isValid() && item.getPickupDelay() <= 0 && item.getOwner() == null)
                .filter(item -> allowed(player, item.getLocation()))
                .filter(item -> targets.stream().anyMatch(target -> hasSpace(inventory(target), item.getItemStack())))
                .min(Comparator.comparingDouble(item -> item.getLocation().distanceSquared(golem.getLocation())))
                .orElse(null);
        if (nearest == null) {
            golem.getPathfinder().stopPathfinding();
        } else if (approach(golem, nearest.getLocation())) {
            InventoryPickupItemEvent event = new InventoryPickupItemEvent(inventory(targets.getFirst()), nearest);
            if (event.callEvent() && nearest.isValid()) {
                golem.getPersistentDataContainer().set(cargoKey, PersistentDataType.BYTE_ARRAY, nearest.getItemStack().serializeAsBytes());
                nearest.remove();
            }
        }
        return 0;
    }

    private boolean canUse(Player player, ContainerTarget target, CopperGolem golem) {
        for (Location location : target.footprint()) {
            if (location.getWorld() == null || !location.getWorld().equals(golem.getWorld())
                    || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                    || !allowed(player, location)
                    || (plugin.getChestLockModule() != null && plugin.getChestLockModule().getEffectiveLockLocation(location).isPresent())
                    || (plugin.getSharedStorageModule() != null && plugin.getSharedStorageModule().isSharedStorageContainer(location))
                    || (plugin.getChestShopModule() != null && plugin.getChestShopModule().isShopChest(location))) {
                return false;
            }
            Material type = location.getBlock().getType();
            if (type != Material.CHEST && type != Material.BARREL) {
                return false;
            }
        }
        Inventory inventory = inventory(target);
        if (!inventory.getViewers().isEmpty()) {
            return false;
        }
        if (inventory.getHolder() instanceof org.bukkit.block.DoubleChest chest) {
            for (org.bukkit.inventory.InventoryHolder side : List.of(chest.getLeftSide(), chest.getRightSide())) {
                if (side instanceof Container container && target.footprint().stream().noneMatch(container.getLocation()::equals)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean allowed(Player player, Location location) {
        LandProtectionModule land = plugin.getLandProtectionModule();
        if (land == null || !land.isFeatureEnabled()) {
            return true;
        }
        var claim = land.getActiveClaimAt(location);
        return claim == null || land.canBypass(player, claim);
    }

    private Inventory inventory(ContainerTarget target) {
        return ((Container) target.anchor().getBlock().getState()).getInventory();
    }

    private boolean approach(CopperGolem golem, Location location) {
        Location destination = location.clone().add(0.5, 0, 0.5);
        if (golem.getLocation().distanceSquared(destination) <= 6.25 && golem.hasLineOfSight(destination)) {
            golem.getPathfinder().stopPathfinding();
            return true;
        }
        golem.getPathfinder().moveTo(destination, 1.1);
        return false;
    }

    private boolean hasSpace(Inventory inventory, ItemStack item) {
        for (ItemStack slot : inventory.getStorageContents()) {
            if (slot == null || slot.getType().isAir()
                    || (slot.isSimilar(item) && slot.getAmount() < Math.min(slot.getMaxStackSize(), inventory.getMaxStackSize()))) {
                return true;
            }
        }
        return false;
    }

    private void compact(Inventory inventory) {
        List<ItemStack> packed = new ArrayList<>();
        for (ItemStack original : inventory.getStorageContents()) {
            if (original == null || original.getType().isAir()) {
                continue;
            }
            ItemStack rest = original.clone();
            for (ItemStack existing : packed) {
                if (!existing.isSimilar(rest)) {
                    continue;
                }
                int moved = Math.min(rest.getAmount(), Math.max(0, Math.min(existing.getMaxStackSize(), inventory.getMaxStackSize()) - existing.getAmount()));
                existing.setAmount(existing.getAmount() + moved);
                rest.setAmount(rest.getAmount() - moved);
                if (rest.getAmount() == 0) {
                    break;
                }
            }
            if (rest.getAmount() > 0) {
                packed.add(rest);
            }
        }
        packed.sort(Comparator.comparing(stack -> stack.getType().name()));
        inventory.setStorageContents(packed.toArray(new ItemStack[inventory.getStorageContents().length]));
    }
}
