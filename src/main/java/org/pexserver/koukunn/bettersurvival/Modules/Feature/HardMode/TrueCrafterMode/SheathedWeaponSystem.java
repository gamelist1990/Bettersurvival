package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.TrueCrafterMode;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 手持ち武器と鞘に収めた武器を距離に応じて交換する。 */
public final class SheathedWeaponSystem {
    private static final double MELEE_DISTANCE_SQUARED = 16.0D;
    private static final double RANGED_DISTANCE_SQUARED = 49.0D;
    private static final double MAX_SWITCH_DISTANCE_SQUARED = 256.0D;
    private static final int SWITCH_COOLDOWN_TICKS = 15;

    private final NamespacedKey ownerKey;
    private final NamespacedKey stowedWeaponKey;
    private final Map<UUID, Integer> cooldowns = new HashMap<>();

    public SheathedWeaponSystem(Plugin plugin) {
        ownerKey = new NamespacedKey(plugin, "truecrafter_sheath_owner");
        stowedWeaponKey = new NamespacedKey(plugin, "truecrafter_stowed_weapon");
    }

    public void initialize(LivingEntity entity, ItemStack stowedWeapon) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null || stowedWeapon == null || stowedWeapon.getType().isAir()) return;
        store(entity, stowedWeapon);
        equipment.setItemInOffHand(ItemStack.empty());
        updateDisplay(entity, stowedWeapon);
    }

    public void tick(LivingEntity entity, Player target) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null || target == null || entity.getWorld() != target.getWorld()) return;
        ItemStack stowed = loadOrMigrate(entity);
        if (stowed == null || stowed.getType().isAir()) return;
        equipment.setItemInOffHand(ItemStack.empty());
        int cooldown = cooldowns.getOrDefault(entity.getUniqueId(), 0);
        if (cooldown > 0) {
            cooldowns.put(entity.getUniqueId(), cooldown - 1);
            return;
        }
        ItemStack active = equipment.getItemInMainHand();
        if (active.getType().isAir()) return;
        double distanceSquared = entity.getLocation().distanceSquared(target.getLocation());
        boolean drawMelee = active.getType() == Material.BOW && stowed.getType() != Material.BOW
                && distanceSquared <= MELEE_DISTANCE_SQUARED;
        boolean drawBow = active.getType() != Material.BOW && stowed.getType() == Material.BOW
                && distanceSquared >= RANGED_DISTANCE_SQUARED && distanceSquared <= MAX_SWITCH_DISTANCE_SQUARED;
        if (!drawMelee && !drawBow) return;
        equipment.setItemInMainHand(stowed);
        store(entity, active);
        updateDisplay(entity, active);
        cooldowns.put(entity.getUniqueId(), SWITCH_COOLDOWN_TICKS);
        entity.getWorld().playSound(entity.getLocation(), drawMelee
                ? Sound.ITEM_ARMOR_EQUIP_IRON : Sound.ITEM_ARMOR_EQUIP_GENERIC, 1.5F, 1.0F);
    }

    public void refreshDisplay(LivingEntity entity) {
        ItemStack stowed = loadOrMigrate(entity);
        if (stowed != null && !stowed.getType().isAir()) updateDisplay(entity, stowed);
    }

    public void restore(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        ItemStack stowed = loadOrMigrate(entity);
        if (equipment != null && stowed != null && !stowed.getType().isAir()
                && equipment.getItemInOffHand().getType().isAir()) {
            equipment.setItemInOffHand(stowed);
        }
        entity.getPersistentDataContainer().remove(stowedWeaponKey);
        cooldowns.remove(entity.getUniqueId());
    }

    public void remove(UUID entityId) {
        cooldowns.remove(entityId);
    }

    public void clear() {
        cooldowns.clear();
    }

    private ItemStack loadOrMigrate(LivingEntity entity) {
        byte[] serialized = entity.getPersistentDataContainer().get(stowedWeaponKey, PersistentDataType.BYTE_ARRAY);
        if (serialized != null && serialized.length > 0) {
            try {
                return ItemStack.deserializeBytes(serialized);
            } catch (IllegalArgumentException ignored) {
                entity.getPersistentDataContainer().remove(stowedWeaponKey);
            }
        }
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return null;
        ItemStack offHand = equipment.getItemInOffHand();
        if (!offHand.getType().isAir()) {
            ItemStack migrated = offHand.clone();
            store(entity, migrated);
            equipment.setItemInOffHand(ItemStack.empty());
            updateDisplay(entity, migrated);
            return migrated;
        }
        ItemDisplay display = sheath(entity);
        if (display == null || display.getItemStack().getType().isAir()) return null;
        ItemStack migrated = display.getItemStack().clone();
        store(entity, migrated);
        return migrated;
    }

    private void store(LivingEntity entity, ItemStack item) {
        entity.getPersistentDataContainer().set(stowedWeaponKey, PersistentDataType.BYTE_ARRAY,
                item.asOne().serializeAsBytes());
    }

    private void updateDisplay(LivingEntity entity, ItemStack item) {
        ItemDisplay display = sheath(entity);
        if (display != null) display.setItemStack(item.asOne());
    }

    private ItemDisplay sheath(LivingEntity entity) {
        String ownerId = entity.getUniqueId().toString();
        return entity.getPassengers().stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast)
                .filter(display -> ownerId.equals(display.getPersistentDataContainer()
                        .get(ownerKey, PersistentDataType.STRING)))
                .findFirst().orElse(null);
    }
}
